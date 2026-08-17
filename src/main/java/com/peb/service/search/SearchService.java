package com.peb.service.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.peb.model.material.Material;
import com.peb.model.material.MaterialRevision;
import com.peb.repository.MaterialRepository;
import com.peb.dto.search.SearchResultPageDTO;
import com.peb.dto.search.SearchSuggestionDTO;
import com.peb.search.SearchDocument;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Query-Schicht für die zentrale Suche.
 *
 * Zwei Modi, je nachdem ob der Suchbegriff Wildcards (* oder ?) enthält:
 *
 * 1. NORMALFALL (kein * oder ?): Native-Query mit match auf searchText
 *    (Operator AND) plus optionaler Prefix-Match auf objectId.
 *
 * 2. WILDCARD-MODUS (Nutzer tippt selbst * oder ?): echte Elasticsearch
 *    Wildcard-Query auf den unanalysierten Keyword-Feldern (nameSort,
 *    objectId). * = beliebig viele Zeichen, ? = genau ein Zeichen -
 *    Standard-Wildcard-Semantik, wie man sie aus Datei-Suchen kennt.
 *    Beispiele: "Mot*", "PT0000*", "Sch?ube".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private static final int SUGGESTION_LIMIT = 8;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ElasticsearchOperations elasticsearchOperations;
    private final MaterialRepository materialRepository;

    /**
     * Für die Navbar-Autocomplete. Bewusst knapp gehalten (max. 8
     * Treffer), sortiert nach Name (nur im Normalfall - bei
     * Wildcard-Queries sortiert Elasticsearch nach Relevanz/_score).
     */
    public List<SearchSuggestionDTO> suggest(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            String normalizedQuery = normalizeQueryInput(query);
            org.springframework.data.elasticsearch.core.query.Query esQuery =
                    buildQuery(normalizedQuery, null, null,
                            PageRequest.of(0, SUGGESTION_LIMIT, Sort.by("nameSort").ascending()));

            SearchHits<SearchDocument> hits = elasticsearchOperations.search(esQuery, SearchDocument.class);

            return hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(this::toSuggestionDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // Autocomplete soll bei ES-Ausfall einfach leer bleiben statt die
            // Seite mit einem 500er zu stören - der Nutzer merkt es höchstens
            // daran, dass keine Vorschläge erscheinen.
            log.error("Suche (Autocomplete) fehlgeschlagen | rawQuery='{}' normalizedQuery='{}' mode={} | cause={} | message={}",
                    toLogValue(query),
                    toLogValue(normalizeQueryInput(query)),
                    isWildcardQuery(query) ? "WILDCARD" : "DEFAULT",
                    e.getClass().getSimpleName(),
                    toLogValue(e.getMessage()));
            return List.of();
        }
    }

    /**
     * Volle Ergebnisseite mit Paginierung und optionalen Filtern.
     *
     * @param query        Suchbegriff (Pflicht), unterstützt * und ? als Wildcards
     * @param objectType   optionaler Filter, null = alle Typen
     * @param releaseStatus optionaler Filter (z.B. "RELEASED"), null = alle
     * @param page         0-basierte Seitenzahl
     */
    public SearchResultPageDTO search(String query, ObjectType objectType, String releaseStatus, int page) {
        try {
            String normalizedQuery = normalizeQueryInput(query);

            if (isReleasedMaterialSearch(objectType, releaseStatus)) {
                return searchReleasedMaterialsFromDatabase(normalizedQuery, Math.max(page, 0));
            }

            org.springframework.data.elasticsearch.core.query.Query esQuery =
                buildQuery(normalizedQuery, objectType, releaseStatus, PageRequest.of(Math.max(page, 0), DEFAULT_PAGE_SIZE));

            SearchHits<SearchDocument> hits = elasticsearchOperations.search(esQuery, SearchDocument.class);

            List<SearchDocument> results = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            long totalHits = hits.getTotalHits();
            int totalPages = (int) Math.ceil((double) totalHits / DEFAULT_PAGE_SIZE);

            return new SearchResultPageDTO(normalizedQuery, results, totalHits, page, DEFAULT_PAGE_SIZE, totalPages, false);
        } catch (Exception e) {
            String normalizedQuery = normalizeQueryInput(query);
            log.error("Suche fehlgeschlagen | rawQuery='{}' normalizedQuery='{}' mode={} objectType={} releaseStatus='{}' page={} pageSize={} | cause={} | message={}",
                    toLogValue(query),
                    toLogValue(normalizedQuery),
                    isWildcardQuery(normalizedQuery) ? "WILDCARD" : "DEFAULT",
                    objectType != null ? objectType.name() : "ALL",
                    toLogValue(releaseStatus),
                    Math.max(page, 0),
                    DEFAULT_PAGE_SIZE,
                    e.getClass().getSimpleName(),
                    toLogValue(e.getMessage()));
            return SearchResultPageDTO.unavailable(query, page, DEFAULT_PAGE_SIZE);
        }
    }

            private boolean isReleasedMaterialSearch(ObjectType objectType, String releaseStatus) {
            return objectType == ObjectType.MATERIAL
                && releaseStatus != null
                && "RELEASED".equalsIgnoreCase(releaseStatus.trim());
            }

            private SearchResultPageDTO searchReleasedMaterialsFromDatabase(String normalizedQuery, int page) {
            List<SearchDocument> allReleased = materialRepository.findAll().stream()
                .map(this::toReleasedMaterialSearchDocument)
                .filter(doc -> doc != null)
                .filter(doc -> matchesReleasedMaterialQuery(doc, normalizedQuery))
                .sorted(Comparator.comparing(doc -> safeLower(doc.getName())))
                .collect(Collectors.toList());

            long totalHits = allReleased.size();
            int from = page * DEFAULT_PAGE_SIZE;
            int to = Math.min(from + DEFAULT_PAGE_SIZE, allReleased.size());

            List<SearchDocument> pageResults = from >= allReleased.size()
                ? List.of()
                : allReleased.subList(from, to);

            int totalPages = (int) Math.ceil((double) totalHits / DEFAULT_PAGE_SIZE);
            return new SearchResultPageDTO(normalizedQuery, pageResults, totalHits, page, DEFAULT_PAGE_SIZE, totalPages, false);
            }

            private SearchDocument toReleasedMaterialSearchDocument(Material material) {
            MaterialRevision releasedRevision = material.getRevisions().stream()
                .filter(r -> r.getReleaseStatus() != null && "RELEASED".equals(r.getReleaseStatus().name()))
                .max(Comparator.comparing(MaterialRevision::getCreatedDate,
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

            if (releasedRevision == null) {
                return null;
            }

            String searchText = String.join(" ",
                nullToEmpty(material.getName()),
                nullToEmpty(material.getId()),
                nullToEmpty(material.getMaterialGroup()),
                nullToEmpty(material.getSpecification()),
                nullToEmpty(material.getDepartment()));

            return SearchDocument.builder()
                .id(ObjectType.MATERIAL.name() + "_" + material.getId() + "_RELEASED")
                .objectId(material.getId())
                .objectType(ObjectType.MATERIAL)
                .objectTypeDisplayName(ObjectType.MATERIAL.getDisplayName())
                .name(material.getName())
                .nameSort(material.getName())
                .revision(releasedRevision.getRevision())
                .releaseStatus("RELEASED")
                .department(material.getDepartment())
                .material(material.getMaterialGroup())
                .searchText(searchText)
                .createdBy(material.getCreatedBy())
                .createdDate(material.getCreatedDate())
                .lastModifiedDate(material.getLastModifiedDate())
                .detailUrl("/materials/" + material.getId())
                .build();
            }

            private boolean matchesReleasedMaterialQuery(SearchDocument doc, String normalizedQuery) {
            if (normalizedQuery == null || normalizedQuery.isBlank()) {
                return true;
            }

            String q = normalizedQuery.toLowerCase();

            if (normalizedQuery.contains("*") || normalizedQuery.contains("?")) {
                String regex = normalizedQuery
                    .replace("?", ".")
                    .replace("*", ".*");
                return safeLower(doc.getObjectId()).matches(regex.toLowerCase())
                    || safeLower(doc.getName()).matches(regex.toLowerCase())
                    || safeLower(doc.getMaterial()).matches(regex.toLowerCase())
                    || safeLower(doc.getSearchText()).matches(".*" + regex.toLowerCase() + ".*");
            }

            return safeLower(doc.getObjectId()).contains(q)
                || safeLower(doc.getName()).contains(q)
                || safeLower(doc.getMaterial()).contains(q)
                || safeLower(doc.getSearchText()).contains(q);
            }

    private org.springframework.data.elasticsearch.core.query.Query buildQuery(
            String query, ObjectType objectType, String releaseStatus, Pageable pageable) {

        if (query == null || query.isBlank()) {
            return buildFilterOnlyQuery(objectType, releaseStatus, pageable);
        }

        boolean isWildcardQuery = query.contains("*") || query.contains("?");

        if (isWildcardQuery) {
            return buildWildcardQuery(query, objectType, releaseStatus, pageable);
        }
        return buildDefaultQuery(query, objectType, releaseStatus, pageable);
    }

    private NativeQuery buildFilterOnlyQuery(ObjectType objectType, String releaseStatus, Pageable pageable) {
        Query boolQuery = Query.of(q -> q.bool(b -> {
            b.must(m -> m.matchAll(ma -> ma));

            if (objectType != null) {
                b.filter(f -> f.term(t -> t.field("objectType").value(objectType.name())));
            }
            if (releaseStatus != null && !releaseStatus.isBlank()) {
                b.filter(f -> f.term(t -> t.field("releaseStatus").value(releaseStatus)));
            }
            return b;
        }));

        return NativeQuery.builder()
                .withQuery(boolQuery)
                .withPageable(pageable)
                .build();
    }

    /**
     * Normalfall: Criteria-API, "contains" auf dem analysierten
     * searchText-Feld + Präfix-Match auf der objectId.
     */
    private NativeQuery buildDefaultQuery(String query, ObjectType objectType, String releaseStatus, Pageable pageable) {
        String objectIdPrefix = query.toUpperCase();

        Query boolQuery = Query.of(q -> q.bool(b -> {
            b.should(s -> s.match(m -> m
                    .field("searchText")
                    .query(query)
                    .operator(Operator.And)));

            b.should(s -> s.matchPhrasePrefix(mpp -> mpp
                    .field("name")
                    .query(query)));

            b.should(s -> s.prefix(p -> p
                    .field("objectId")
                    .value(objectIdPrefix)));

            b.minimumShouldMatch("1");

            if (objectType != null) {
                b.filter(f -> f.term(t -> t.field("objectType").value(objectType.name())));
            }
            if (releaseStatus != null && !releaseStatus.isBlank()) {
                b.filter(f -> f.term(t -> t.field("releaseStatus").value(releaseStatus)));
            }
            return b;
        }));

        return NativeQuery.builder()
                .withQuery(boolQuery)
                .withPageable(pageable)
                .build();
    }

    /**
     * Wildcard-Modus: echte Elasticsearch wildcard-Query auf den
     * Keyword-Feldern (unanalysiert, also 1:1 wie eingegeben - deshalb
     * caseInsensitive(true), damit Groß-/Kleinschreibung keine Rolle
     * spielt). objectId zusätzlich in Großbuchstaben, da IDs immer
     * großgeschrieben gespeichert sind (Wildcard-Zeichen selbst bleiben
     * beim Hochcasen unangetastet).
     *
     * Hinweis: ein führendes "*" (z.B. "*schraube") ist erlaubt, aber
     * langsamer als ein Suffix-Wildcard, da Elasticsearch dafür nicht
     * den Präfix-Index nutzen kann.
     */
    private NativeQuery buildWildcardQuery(String query, ObjectType objectType, String releaseStatus, Pageable pageable) {
        String objectIdPattern = query.toUpperCase();

        Query boolQuery = Query.of(q -> q.bool(b -> {
            b.should(s -> s.wildcard(w -> w.field("name_sort").value(query).caseInsensitive(true)));
            b.should(s -> s.wildcard(w -> w.field("objectId").value(objectIdPattern).caseInsensitive(true)));
            b.minimumShouldMatch("1");

            if (objectType != null) {
                b.filter(f -> f.term(t -> t.field("objectType").value(objectType.name())));
            }
            if (releaseStatus != null && !releaseStatus.isBlank()) {
                b.filter(f -> f.term(t -> t.field("releaseStatus").value(releaseStatus)));
            }
            return b;
        }));

        return NativeQuery.builder()
                .withQuery(boolQuery)
                .withPageable(pageable)
                .build();
    }

    private SearchSuggestionDTO toSuggestionDTO(SearchDocument doc) {
        return new SearchSuggestionDTO(
                doc.getObjectId(),
                doc.getObjectType(),
                doc.getObjectTypeDisplayName(),
                doc.getName(),
                doc.getRevision(),
                doc.getReleaseStatus(),
                doc.getDetailUrl()
        );
    }

    private String normalizeQueryInput(String query) {
        if (query == null) {
            return "";
        }

        String normalized = query
                .replace('"', ' ')
                .replace('\'', ' ')
                .replace('`', ' ')
                .replaceAll("[^\\p{L}\\p{N}\\s*?_-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.contains(" ") && (normalized.contains("*") || normalized.contains("?"))) {
            normalized = normalized.replace("*", "").replace("?", "").trim();
        }

        return normalized;
    }

    private boolean isWildcardQuery(String query) {
        return query != null && (query.contains("*") || query.contains("?"));
    }

    private String toLogValue(String value) {
        if (value == null) {
            return "<null>";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 160) {
            return compact;
        }
        return compact.substring(0, 160) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
