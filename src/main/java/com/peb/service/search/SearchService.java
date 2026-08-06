package com.peb.service.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query-Schicht für die zentrale Suche.
 *
 * Zwei Modi, je nachdem ob der Suchbegriff Wildcards (* oder ?) enthält:
 *
 * 1. NORMALFALL (kein * oder ?): Criteria-API mit "contains" - das
 *    matched implizit bereits als *Begriff* (Infix-Suche), ist aber ein
 *    analysiertes Text-Matching (findet einzelne Wörter/Teilwörter).
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
            org.springframework.data.elasticsearch.core.query.Query esQuery =
                    buildQuery(query.trim(), null, null,
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
            log.error("Suche (Autocomplete) fehlgeschlagen - Elasticsearch nicht erreichbar? Fehler: {}",
                    e.getMessage());
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
            org.springframework.data.elasticsearch.core.query.Query esQuery =
                    buildQuery(query.trim(), objectType, releaseStatus, PageRequest.of(Math.max(page, 0), DEFAULT_PAGE_SIZE));

            SearchHits<SearchDocument> hits = elasticsearchOperations.search(esQuery, SearchDocument.class);

            List<SearchDocument> results = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            long totalHits = hits.getTotalHits();
            int totalPages = (int) Math.ceil((double) totalHits / DEFAULT_PAGE_SIZE);

            return new SearchResultPageDTO(query, results, totalHits, page, DEFAULT_PAGE_SIZE, totalPages, false);
        } catch (Exception e) {
            log.error("Suche fehlgeschlagen - Elasticsearch nicht erreichbar? Fehler: {}", e.getMessage());
            return SearchResultPageDTO.unavailable(query, page, DEFAULT_PAGE_SIZE);
        }
    }

    private org.springframework.data.elasticsearch.core.query.Query buildQuery(
            String query, ObjectType objectType, String releaseStatus, Pageable pageable) {

        boolean isWildcardQuery = query.contains("*") || query.contains("?");

        if (isWildcardQuery) {
            return buildWildcardQuery(query, objectType, releaseStatus, pageable);
        }
        return buildDefaultQuery(query, objectType, releaseStatus, pageable);
    }

    /**
     * Normalfall: Criteria-API, "contains" auf dem analysierten
     * searchText-Feld + Präfix-Match auf der objectId.
     */
    private CriteriaQuery buildDefaultQuery(String query, ObjectType objectType, String releaseStatus, Pageable pageable) {
        Criteria criteria = new Criteria("searchText").contains(query)
                .or(new Criteria("objectId").startsWith(query.toUpperCase()));

        if (objectType != null) {
            criteria = criteria.and(new Criteria("objectType").is(objectType.name()));
        }
        if (releaseStatus != null && !releaseStatus.isBlank()) {
            criteria = criteria.and(new Criteria("releaseStatus").is(releaseStatus));
        }

        CriteriaQuery criteriaQuery = new CriteriaQuery(criteria);
        criteriaQuery.setPageable(pageable);
        return criteriaQuery;
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
            b.should(s -> s.wildcard(w -> w.field("nameSort").value(query).caseInsensitive(true)));
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
}
