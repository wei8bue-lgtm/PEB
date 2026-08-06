// src/main/java/com/peb/service/product/ProductDataService.java
package com.peb.service.product;

import com.peb.model.product.ManufacturingStatus;
import com.peb.model.product.ProductData;
import com.peb.model.product.ProductDataRevision;
import com.peb.repository.product.ProductDataRepository;
import com.peb.repository.product.ProductDataRevisionRepository;
import com.peb.service.IDGeneratorService;
import com.peb.service.search.SearchIndexService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductDataService {

    private final ProductDataRepository         repository;
    private final ProductDataRevisionRepository revisionRepository;
    private final IDGeneratorService            idGeneratorService;
    private final SearchIndexService            searchIndexService; // NEU: zentrale Suche

    @Transactional
    public ProductData create(ProductData pd) {
        // ── 1. ID generieren & ProductData speichern ──────────
        String newId = idGeneratorService.generateId(
                ObjectType.PRODUCT_DATA);
        pd.setId(newId);
        ProductData saved = repository.save(pd);

        // ── 2. Initiale Revision 01 automatisch erstellen ─────
        ProductDataRevision initRev = new ProductDataRevision();
        initRev.setProductData(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        revisionRepository.save(initRev);
        saved.getRevisions().add(initRev); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht

        log.info("Erstelle ProductData: {} – '{}' "
                + "mit initialer Revision 01 (WORKING)",
                newId, pd.getName());

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.PRODUCT_DATA, saved);

        return saved;
    }

    @Transactional
    public ProductData update(String id, ProductData form) {
        ProductData existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductData nicht gefunden: " + id));

        // Nur Stammdaten – Status läuft über LifecycleService!
        existing.setName(form.getName());
        existing.setDepartment(form.getDepartment());
        existing.setProductType(form.getProductType());
        existing.setDescription(form.getDescription());

        log.info("Aktualisiere ProductData: {}", id);
        ProductData saved = repository.save(existing);

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.PRODUCT_DATA, saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ProductData> findById(String id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ProductData> findAll() {
        return repository.findAll(
                Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public List<ProductData> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        String q = query.replace("*", "").trim();
        return repository.searchByQuery(q);
    }

    @Transactional
    public void delete(String id) {
        log.info("Lösche ProductData: {}", id);
        repository.deleteById(id);

        // NEU: aus zentraler Suche entfernen
        searchIndexService.remove(ObjectType.PRODUCT_DATA, id);
    }

    // ── Hilfsmethode ─────────────────────────────────────────
    private String currentUser() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}