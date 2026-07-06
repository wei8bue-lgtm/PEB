// src/main/java/com/peb/service/product/RevisionChainService.java
package com.peb.service.product;

import com.peb.model.product.*;
import com.peb.repository.product.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RevisionChainService {

    private final ProductDataRevisionRepository      pdRevRepo;
    private final ProductBOMRevisionRepository       pbomRevRepo;
    private final ManufacturingBOMRevisionRepository mfgRevRepo;
    private final ProductBOPRevisionRepository       pbopRevRepo;
    private final PlantBOPRevisionRepository         plantRevRepo;

    @Transactional(readOnly = true)
    public List<ProductDataRevision> getRevisionsForProductData(
            String productDataId) {
        List<ProductDataRevision> revs =
                pdRevRepo.findByProductDataIdOrderByRevisionAsc(
                        productDataId);
        log.debug("ProductData {}: {} Revisionen",
                productDataId, revs.size());
        return revs.isEmpty()
                ? Collections.emptyList()
                : revs;
    }

    @Transactional(readOnly = true)
    public List<ProductBOMRevision> getRevisionsForProductBOM(
            Long productBOMId) {
        return pbomRevRepo
                .findByProductBOMIdOrderByRevisionAsc(
                        productBOMId);
    }

    @Transactional(readOnly = true)
    public List<ManufacturingBOMRevision>
            getRevisionsForManufacturingBOM(Long mfgBOMId) {
        return mfgRevRepo
                .findByManufacturingBOMIdOrderByRevisionAsc(
                        mfgBOMId);
    }

    @Transactional(readOnly = true)
    public List<ProductBOPRevision> getRevisionsForProductBOP(
            Long productBOPId) {
        return pbopRevRepo
                .findByProductBOPIdOrderByRevisionAsc(
                        productBOPId);
    }

    @Transactional(readOnly = true)
    public List<PlantBOPRevision> getRevisionsForPlantBOP(
            Long plantBOPId) {
        return plantRevRepo
                .findByPlantBOPIdOrderByRevisionAsc(
                        plantBOPId);
    }
}