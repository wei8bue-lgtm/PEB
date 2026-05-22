// DATEI: src/main/java/com/peb/service/product/ProductBOMService.java
package com.peb.service.product;

import com.peb.model.product.ManufacturingBOM;
import com.peb.model.product.ProductBOM;
import com.peb.model.product.ProductData;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ProductBOMRepository;
import com.peb.repository.product.ProductDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBOMService {

    private final ProductBOMRepository       bomRepo;
    private final ProductDataRepository      pdRepo;
    private final ManufacturingBOMRepository mfgRepo;

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
        log.info("Erstelle ProductBOM: {}", bom.getName());
        return bomRepo.save(bom);
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
}