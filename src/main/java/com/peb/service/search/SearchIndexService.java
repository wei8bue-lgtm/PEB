package com.peb.service.search;

import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Zentrale Stelle, um den Elasticsearch-Suchindex synchron mit der
 * Oracle-DB zu halten. Wird von den bestehenden Fach-Services
 * (PartService, DesignService, MaterialService, ...) direkt nach jeder
 * schreibenden Operation aufgerufen - siehe docs/SEARCH_INTEGRATION.md
 * für die genauen Call-Sites.
 *
 * Bewusst KEIN automatisches Hooking über JPA EntityListener/@PostPersist:
 * Das würde für jede der 15 Entity-Klassen einen Listener erfordern und
 * macht den Datenfluss schwerer nachvollziehbar. Ein expliziter Aufruf
 * "index(part)" nach dem Speichern ist einfacher zu lesen, zu debuggen
 * und zu testen.
 *
 * WICHTIG 1 (Historie, für's Verständnis): Ursprünglich lief das über ein
 * Spring Data Repository (ElasticsearchRepository<SearchDocument, String>).
 * Das musste wieder entfernt werden: Spring Data Repository-Beans werden
 * vom Framework BEWUSST eager validiert (Fail-Fast bei Query-Ableitungs-
 * fehlern), und zwar SELBST wenn spring.main.lazy-initialization=true
 * gesetzt ist. Ergebnis: Der App-Start hing weiterhin an der
 * ES-Erreichbarkeit, obwohl die globale Lazy-Property gesetzt war.
 * Lösung: direkte Nutzung von ElasticsearchOperations statt eines
 * Repository-Interfaces - das ist ein ganz normales @Bean und respektiert
 * Lazy-Init zuverlässig, ohne Spring Datas Repository-Sonderbehandlung.
 *
 * WICHTIG 2: elasticsearchOperations ist zusätzlich @Lazy injiziert
 * (Proxy statt echter Bean-Referenz an dieser Stelle) - in Kombination
 * mit spring.main.lazy-initialization=true doppelt abgesichert.
 *
 * WICHTIG 3: index()/remove() werfen bewusst KEINE Exception weiter, wenn
 * Elasticsearch gerade nicht erreichbar ist (Details siehe dort).
 */
@Slf4j
@Service
public class SearchIndexService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final List<SearchIndexMapper<?>> mappers;
    private Map<ObjectType, SearchIndexMapper<?>> mappersByType;
    private final AtomicBoolean indexEnsured = new AtomicBoolean(false);

    public SearchIndexService(@Lazy ElasticsearchOperations elasticsearchOperations,
                               List<SearchIndexMapper<?>> mappers) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.mappers = mappers;
    }

    private Map<ObjectType, SearchIndexMapper<?>> mappersByType() {
        if (mappersByType == null) {
            mappersByType = mappers.stream()
                    .collect(Collectors.toMap(SearchIndexMapper::getObjectType, m -> m));
        }
        return mappersByType;
    }

    /**
     * Legt den Index (inkl. der über @Field-Annotationen definierten
     * Mapping-Typen) an, falls er noch nicht existiert. Wird bewusst NICHT
     * beim App-Start aufgerufen (siehe createIndex=false an @Document),
     * sondern lazy beim ersten tatsächlichen Schreibzugriff bzw. explizit
     * über den Admin-Reindex-Endpunkt - so bleibt der App-Start unabhängig
     * von der Erreichbarkeit von Elasticsearch.
     */
    private void ensureIndexExists() {
        if (indexEnsured.get()) {
            return;
        }
        IndexOperations indexOps = elasticsearchOperations.indexOps(SearchDocument.class);
        if (!indexOps.exists()) {
            indexOps.create();
            indexOps.putMapping();
            log.info("Elasticsearch-Index 'peb-search-index' angelegt (inkl. Mapping).");
        }
        indexEnsured.set(true);
    }

    /**
     * Indexiert (oder aktualisiert) ein einzelnes Objekt. Aufrufen nach
     * create/update, z.B. am Ende von PartService.createPart(...) bzw.
     * PartService.updatePart(...).
     *
     * Beispiel:
     *   Part saved = partRepository.save(part);
     *   searchIndexService.index(ObjectType.PART, saved);
     *   return saved;
     */
    @SuppressWarnings("unchecked")
    public <T> void index(ObjectType objectType, T entity) {
        try {
            SearchIndexMapper<T> mapper = (SearchIndexMapper<T>) mappersByType().get(objectType);
            if (mapper == null) {
                log.warn("Kein SearchIndexMapper für ObjectType {} registriert - " +
                        "Objekt wird NICHT in der Suche auffindbar sein.", objectType);
                return;
            }
            SearchDocument document = mapper.toSearchDocument(entity);
            if (document == null) {
                log.debug("Mapper für {} hat kein SearchDocument erzeugt (z.B. keine Revision) - übersprungen.",
                        objectType);
                return;
            }
            ensureIndexExists();
            elasticsearchOperations.save(document);
        } catch (Exception e) {
            // Bewusst geschluckt statt weitergeworfen, siehe Klassenkommentar:
            // ein ES-Ausfall darf niemals eine fachliche Transaktion scheitern lassen.
            log.error("Konnte Objekt (type={}) nicht in der Suche indexieren - " +
                    "Elasticsearch nicht erreichbar? Fachliche Operation ist davon NICHT betroffen. Fehler: {}",
                    objectType, e.getMessage());
        }
    }

    /**
     * Entfernt ein Objekt aus dem Index. Aufrufen nach dem Löschen des
     * Fachobjekts, z.B. am Ende von PartService.deletePart(id).
     */
    public void remove(ObjectType objectType, String objectId) {
        try {
            ensureIndexExists();
            elasticsearchOperations.delete(objectType.name() + "_" + objectId, SearchDocument.class);
        } catch (Exception e) {
            log.error("Konnte Objekt (type={}, id={}) nicht aus der Suche entfernen - " +
                    "Elasticsearch nicht erreichbar? Fachliche Operation ist davon NICHT betroffen. Fehler: {}",
                    objectType, objectId, e.getMessage());
        }
    }

    /**
     * Voller Reindex eines gesamten Objekttyps. Genutzt vom
     * Admin-Endpunkt (siehe SearchAdminController) und initial beim
     * Rollout, um den Index einmalig aus dem Bestand der DB zu befüllen.
     * Hier DARF/SOLL ein ES-Fehler sichtbar werden (kein @Transactional-
     * Kontext einer Fachoperation), daher kein Schlucken der Exception -
     * der Admin-Endpunkt soll erkennbar fehlschlagen, wenn ES down ist.
     */
    public <T> void reindexAll(ObjectType objectType, List<T> allEntities) {
        ensureIndexExists();
        int count = 0;
        for (T entity : allEntities) {
            index(objectType, entity);
            count++;
        }
        log.info("Reindex abgeschlossen für {}: {} Objekte verarbeitet.", objectType, count);
    }
}
