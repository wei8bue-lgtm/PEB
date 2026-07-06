// src/main/java/com/peb/service/product/ProductBOMService.java
package com.peb.service.product;

import com.peb.model.product.*;
import com.peb.repository.product.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBOMService {

    private final ProductBOMRepository         bomRepo;
    private final ProductBOMRevisionRepository bomRevRepo;
    private final ProductDataRepository        pdRepo;
    private final ManufacturingBOMRepository   mfgRepo;

    @Transactional
    public ProductBOM create(ProductBOM bom, String productDataId) {

        if (productDataId != null && !productDataId.isBlank()) {
            ProductData pd = pdRepo.findById(productDataId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ProductData nicht gefunden: "
                            + productDataId));
            if (pd.getProductBOM() != null) {
                throw new IllegalStateException(
                        "ProductData " + productDataId
                        + " hat bereits eine ProductBOM ("
                        + pd.getProductBOM().getId()
                        + "). Kardinalität 1:1 verletzt.");
            }
            bom.setProductData(pd);
        }

        ProductBOM saved = bomRepo.save(bom);

        // ── Initiale Revision 01 automatisch erstellen ────────
        ProductBOMRevision initRev = new ProductBOMRevision();
        initRev.setProductBOM(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        bomRevRepo.save(initRev);

        log.info("Erstelle ProductBOM: '{}' (ID: {}) "
                + "mit initialer Revision 01 (WORKING)",
                saved.getName(), saved.getId());
        return saved;
    }

    @Transactional
    public ProductBOM assignProductData(Long bomId,
                                        String productDataId) {
        ProductBOM bom = bomRepo.findById(bomId).orElseThrow();

        if (bom.getProductData() != null) {
            log.info("Löse PD-Verknüpfung: {}",
                    bom.getProductData().getId());
            bom.setProductData(null);
            bomRepo.saveAndFlush(bom);
        }

        if (productDataId != null && !productDataId.isBlank()) {
            ProductData pd = pdRepo.findById(productDataId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ProductData nicht gefunden: "
                            + productDataId));
            if (pd.getProductBOM() != null
                    && !pd.getProductBOM().getId().equals(bomId)) {
                throw new IllegalStateException(
                        "ProductData " + productDataId
                        + " ist bereits einer anderen ProductBOM ("
                        + pd.getProductBOM().getId()
                        + ") zugewiesen.");
            }
            bom.setProductData(pd);
        }
        return bomRepo.save(bom);
    }

    @Transactional(readOnly = true)
    public Optional<ProductBOM> findById(Long id) {
        return bomRepo.findByIdWithProductData(id);
    }

    @Transactional(readOnly = true)
    public List<ProductBOM> findAll() {
        return bomRepo.findAll();
    }

    @Transactional
    public void addToManufacturingBOM(Long bomId, Long mfgBomId) {
        ManufacturingBOM mfg = mfgRepo.findById(mfgBomId)
                .orElseThrow();
        ProductBOM pb = bomRepo.findById(bomId).orElseThrow();
        if (!mfg.getProductBOMs().contains(pb)) {
            mfg.getProductBOMs().add(pb);
            mfgRepo.save(mfg);
        }
    }

    @Transactional
    public void removeFromManufacturingBOM(Long bomId,
                                           Long mfgBomId) {
        ManufacturingBOM mfg = mfgRepo.findById(mfgBomId)
                .orElseThrow();
        mfg.getProductBOMs().removeIf(
                pb -> pb.getId().equals(bomId));
        mfgRepo.save(mfg);
    }

    // ── Hilfsmethode ─────────────────────────────────────────────
    private String currentUser() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}