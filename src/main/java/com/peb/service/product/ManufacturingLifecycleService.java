// src/main/java/com/peb/service/product/ManufacturingLifecycleService.java
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

/**
 * Lifecycle analog zu DrawingRevisionService:
 *
 *   RELEASE: Aktuelle WORKING-Revision → RELEASED.
 *            Alle anderen Revisionen → OBSOLETE.
 *
 *   REVISE:  Neue WORKING-Revision erstellen.
 *            Vorherige Revision bleibt RELEASED.
 *
 *   SAVE AS: Neues Master-Objekt + initiale Revision 01.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManufacturingLifecycleService {

    // ── Repositories ─────────────────────────────────────────────
    private final ProductDataRepository              pdRepo;
    private final ProductDataRevisionRepository      pdRevRepo;
    private final ProductBOMRepository               pbomRepo;
    private final ProductBOMRevisionRepository       pbomRevRepo;
    private final ManufacturingBOMRepository         mfgRepo;
    private final ManufacturingBOMRevisionRepository mfgRevRepo;
    private final ProductBOPRepository               pbopRepo;
    private final ProductBOPRevisionRepository       pbopRevRepo;
    private final PlantBOPRepository                 plantRepo;
    private final PlantBOPRevisionRepository         plantRevRepo;

    // ══════════════════════════════════════════════════════════════
    //  PRODUCT DATA
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public ProductDataRevision releaseProductData(
            String productDataId) {

        ProductData pd = findProductData(productDataId);
        ProductDataRevision latest = pd.getLatestRevision();

        validateNotNull(latest, "ProductData", productDataId);
        validateWorking(latest.isWorking(),
                "ProductData", productDataId);

        // Alle anderen Revisionen → OBSOLETE
        pd.getRevisions().stream()
                .filter(r -> !r.getRevisionId()
                              .equals(latest.getRevisionId()))
                .filter(r -> !r.isObsolete())
                .forEach(r -> {
                    r.setStatus(ManufacturingStatus.OBSOLETE);
                    pdRevRepo.save(r);
                });

        latest.setStatus(ManufacturingStatus.RELEASED);
        log.info("ProductData {} Rev {} RELEASED von {}",
                productDataId, latest.getRevision(),
                currentUser());
        return pdRevRepo.save(latest);
    }

    @Transactional
    public ProductDataRevision reviseProductData(
            String productDataId) {

        ProductData pd = findProductData(productDataId);
        ProductDataRevision latest = pd.getLatestRevision();

        validateNotNull(latest, "ProductData", productDataId);
        validateReleased(latest.isReleased(),
                "ProductData", productDataId);

        ProductDataRevision newRev = new ProductDataRevision();
        newRev.setProductData(pd);
        newRev.setRevision(nextRevision(latest.getRevision()));
        newRev.setStatus(ManufacturingStatus.WORKING);
        newRev.setCreatedBy(currentUser());

        // Vorherige Revision bleibt RELEASED!

        log.info("ProductData {} → neue Revision {} von {}. "
                + "Rev {} bleibt RELEASED.",
                productDataId, newRev.getRevision(),
                currentUser(), latest.getRevision());
        return pdRevRepo.save(newRev);
    }

    @Transactional
    public ProductData saveAsProductData(String sourceId,
                                          String newName) {
        ProductData source = findProductData(sourceId);

        // Neues Master-Objekt
        ProductData copy = new ProductData();
        copy.setId(generateProductDataId());
        copy.setName(newName);
        copy.setProductType(source.getProductType());
        copy.setDepartment(source.getDepartment());
        copy.setDescription(source.getDescription());
        ProductData saved = pdRepo.save(copy);

        // Initiale Revision
        ProductDataRevision initRev = new ProductDataRevision();
        initRev.setProductData(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        pdRevRepo.save(initRev);

        log.info("ProductData {} → SaveAs '{}' ({}) von {}",
                sourceId, newName, saved.getId(), currentUser());
        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    //  PRODUCT BOM
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public ProductBOMRevision releaseProductBOM(Long id) {

        ProductBOM bom = findProductBOM(id);
        ProductBOMRevision latest = bom.getLatestRevision();

        validateNotNull(latest, "ProductBOM", id);
        validateWorking(latest.isWorking(), "ProductBOM", id);

        bom.getRevisions().stream()
                .filter(r -> !r.getRevisionId()
                              .equals(latest.getRevisionId()))
                .filter(r -> !r.isObsolete())
                .forEach(r -> {
                    r.setStatus(ManufacturingStatus.OBSOLETE);
                    pbomRevRepo.save(r);
                });

        latest.setStatus(ManufacturingStatus.RELEASED);
        log.info("ProductBOM {} Rev {} RELEASED von {}",
                id, latest.getRevision(), currentUser());
        return pbomRevRepo.save(latest);
    }

    @Transactional
    public ProductBOMRevision reviseProductBOM(Long id) {

        ProductBOM bom = findProductBOM(id);
        ProductBOMRevision latest = bom.getLatestRevision();

        validateNotNull(latest, "ProductBOM", id);
        validateReleased(latest.isReleased(), "ProductBOM", id);

        ProductBOMRevision newRev = new ProductBOMRevision();
        newRev.setProductBOM(bom);
        newRev.setRevision(nextRevision(latest.getRevision()));
        newRev.setStatus(ManufacturingStatus.WORKING);
        newRev.setCreatedBy(currentUser());

        log.info("ProductBOM {} → neue Revision {} von {}.",
                id, newRev.getRevision(), currentUser());
        return pbomRevRepo.save(newRev);
    }

    @Transactional
    public ProductBOM saveAsProductBOM(Long sourceId,
                                        String newName) {
        ProductBOM source = findProductBOM(sourceId);

        ProductBOM copy = new ProductBOM();
        copy.setName(newName);
        copy.setBomVersion(source.getBomVersion());
        copy.setDescription(source.getDescription());
        ProductBOM saved = pbomRepo.save(copy);

        ProductBOMRevision initRev = new ProductBOMRevision();
        initRev.setProductBOM(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        pbomRevRepo.save(initRev);

        log.info("ProductBOM {} → SaveAs '{}' ({}) von {}",
                sourceId, newName, saved.getId(), currentUser());
        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    //  MANUFACTURING BOM
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public ManufacturingBOMRevision releaseManufacturingBOM(
            Long id) {

        ManufacturingBOM bom = findMfgBOM(id);
        ManufacturingBOMRevision latest = bom.getLatestRevision();

        validateNotNull(latest, "ManufacturingBOM", id);
        validateWorking(latest.isWorking(),
                "ManufacturingBOM", id);

        bom.getRevisions().stream()
                .filter(r -> !r.getRevisionId()
                              .equals(latest.getRevisionId()))
                .filter(r -> !r.isObsolete())
                .forEach(r -> {
                    r.setStatus(ManufacturingStatus.OBSOLETE);
                    mfgRevRepo.save(r);
                });

        latest.setStatus(ManufacturingStatus.RELEASED);
        log.info("ManufacturingBOM {} Rev {} RELEASED von {}",
                id, latest.getRevision(), currentUser());
        return mfgRevRepo.save(latest);
    }

    @Transactional
    public ManufacturingBOMRevision reviseManufacturingBOM(
            Long id) {

        ManufacturingBOM bom = findMfgBOM(id);
        ManufacturingBOMRevision latest = bom.getLatestRevision();

        validateNotNull(latest, "ManufacturingBOM", id);
        validateReleased(latest.isReleased(),
                "ManufacturingBOM", id);

        ManufacturingBOMRevision newRev =
                new ManufacturingBOMRevision();
        newRev.setManufacturingBOM(bom);
        newRev.setRevision(nextRevision(latest.getRevision()));
        newRev.setStatus(ManufacturingStatus.WORKING);
        newRev.setCreatedBy(currentUser());

        log.info("ManufacturingBOM {} → neue Revision {} "
                + "von {}.", id, newRev.getRevision(),
                currentUser());
        return mfgRevRepo.save(newRev);
    }

    @Transactional
    public ManufacturingBOM saveAsManufacturingBOM(Long sourceId,
                                                    String newName) {
        ManufacturingBOM source = findMfgBOM(sourceId);

        ManufacturingBOM copy = new ManufacturingBOM();
        copy.setName(newName);
        copy.setBomVersion(source.getBomVersion());
        copy.setDescription(source.getDescription());
        ManufacturingBOM saved = mfgRepo.save(copy);

        ManufacturingBOMRevision initRev =
                new ManufacturingBOMRevision();
        initRev.setManufacturingBOM(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        mfgRevRepo.save(initRev);

        log.info("ManufacturingBOM {} → SaveAs '{}' ({}) von {}",
                sourceId, newName, saved.getId(), currentUser());
        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    //  PRODUCT BOP
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public ProductBOPRevision releaseProductBOP(Long id) {

        ProductBOP bop = findProductBOP(id);
        ProductBOPRevision latest = bop.getLatestRevision();

        validateNotNull(latest, "ProductBOP", id);
        validateWorking(latest.isWorking(), "ProductBOP", id);

        bop.getRevisions().stream()
                .filter(r -> !r.getRevisionId()
                              .equals(latest.getRevisionId()))
                .filter(r -> !r.isObsolete())
                .forEach(r -> {
                    r.setStatus(ManufacturingStatus.OBSOLETE);
                    pbopRevRepo.save(r);
                });

        latest.setStatus(ManufacturingStatus.RELEASED);
        log.info("ProductBOP {} Rev {} RELEASED von {}",
                id, latest.getRevision(), currentUser());
        return pbopRevRepo.save(latest);
    }

    @Transactional
    public ProductBOPRevision reviseProductBOP(Long id) {

        ProductBOP bop = findProductBOP(id);
        ProductBOPRevision latest = bop.getLatestRevision();

        validateNotNull(latest, "ProductBOP", id);
        validateReleased(latest.isReleased(), "ProductBOP", id);

        ProductBOPRevision newRev = new ProductBOPRevision();
        newRev.setProductBOP(bop);
        newRev.setRevision(nextRevision(latest.getRevision()));
        newRev.setStatus(ManufacturingStatus.WORKING);
        newRev.setCreatedBy(currentUser());

        log.info("ProductBOP {} → neue Revision {} von {}.",
                id, newRev.getRevision(), currentUser());
        return pbopRevRepo.save(newRev);
    }

    @Transactional
    public ProductBOP saveAsProductBOP(Long sourceId,
                                        String newName) {
        ProductBOP source = findProductBOP(sourceId);

        ProductBOP copy = new ProductBOP();
        copy.setName(newName);
        copy.setBopVersion(source.getBopVersion());
        copy.setDescription(source.getDescription());
        ProductBOP saved = pbopRepo.save(copy);

        ProductBOPRevision initRev = new ProductBOPRevision();
        initRev.setProductBOP(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        pbopRevRepo.save(initRev);

        log.info("ProductBOP {} → SaveAs '{}' ({}) von {}",
                sourceId, newName, saved.getId(), currentUser());
        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    //  PLANT BOP
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public PlantBOPRevision releasePlantBOP(Long id) {

        PlantBOP bop = findPlantBOP(id);
        PlantBOPRevision latest = bop.getLatestRevision();

        validateNotNull(latest, "PlantBOP", id);
        validateWorking(latest.isWorking(), "PlantBOP", id);

        bop.getRevisions().stream()
                .filter(r -> !r.getRevisionId()
                              .equals(latest.getRevisionId()))
                .filter(r -> !r.isObsolete())
                .forEach(r -> {
                    r.setStatus(ManufacturingStatus.OBSOLETE);
                    plantRevRepo.save(r);
                });

        latest.setStatus(ManufacturingStatus.RELEASED);
        log.info("PlantBOP {} Rev {} RELEASED von {}",
                id, latest.getRevision(), currentUser());
        return plantRevRepo.save(latest);
    }

    @Transactional
    public PlantBOPRevision revisePlantBOP(Long id) {

        PlantBOP bop = findPlantBOP(id);
        PlantBOPRevision latest = bop.getLatestRevision();

        validateNotNull(latest, "PlantBOP", id);
        validateReleased(latest.isReleased(), "PlantBOP", id);

        PlantBOPRevision newRev = new PlantBOPRevision();
        newRev.setPlantBOP(bop);
        newRev.setRevision(nextRevision(latest.getRevision()));
        newRev.setStatus(ManufacturingStatus.WORKING);
        newRev.setCreatedBy(currentUser());

        log.info("PlantBOP {} → neue Revision {} von {}.",
                id, newRev.getRevision(), currentUser());
        return plantRevRepo.save(newRev);
    }

    @Transactional
    public PlantBOP saveAsPlantBOP(Long sourceId, String newName) {
        PlantBOP source = findPlantBOP(sourceId);

        PlantBOP copy = new PlantBOP();
        copy.setName(newName);
        copy.setPlantCode(source.getPlantCode());
        copy.setBopVersion(source.getBopVersion());
        copy.setDescription(source.getDescription());
        PlantBOP saved = plantRepo.save(copy);

        PlantBOPRevision initRev = new PlantBOPRevision();
        initRev.setPlantBOP(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        plantRevRepo.save(initRev);

        log.info("PlantBOP {} → SaveAs '{}' ({}) von {}",
                sourceId, newName, saved.getId(), currentUser());
        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    //  Private Hilfsmethoden
    // ══════════════════════════════════════════════════════════════

	private ProductData findProductData(String id) {
		// ── ID bereinigen (Whitespace, Encoding) ──────────────────
		String cleanId = id != null ? id.trim() : "";
		
		return pdRepo.findById(cleanId)
				.orElseThrow(() -> new IllegalArgumentException(
						"ProductData nicht gefunden: " + cleanId));
	}

    private ProductBOM findProductBOM(Long id) {
        return pbomRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOM nicht gefunden: " + id));
    }

    private ManufacturingBOM findMfgBOM(Long id) {
        return mfgRepo.findByIdOnly(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ManufacturingBOM nicht gefunden: " + id));
    }

    private ProductBOP findProductBOP(Long id) {
        return pbopRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOP nicht gefunden: " + id));
    }

    private PlantBOP findPlantBOP(Long id) {
        return plantRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PlantBOP nicht gefunden: " + id));
    }

    private String generateProductDataId() {
        // Höchste vorhandene numerische ID + 1
        // Native Query aus Repository
        Long max = pdRepo.findMaxSequence();
        long next = (max != null ? max : 0L) + 1L;
        return String.format("PD%08d", next);
    }

    private String nextRevision(String current) {
        if (current == null || current.isBlank()) return "02";
        try {
            int num      = Integer.parseInt(current.trim());
            int padWidth = Math.max(current.trim().length(), 2);
            return String.format("%0" + padWidth + "d", num + 1);
        } catch (NumberFormatException e) {
            return current + "_1";
        }
    }

    private void validateNotNull(Object revision,
                                  String type, Object id) {
        if (revision == null) {
            throw new IllegalStateException(
                    type + " " + id
                    + " hat keine Revisionen.");
        }
    }

    private void validateWorking(boolean isWorking,
                                  String type, Object id) {
        if (!isWorking) {
            throw new IllegalStateException(
                    type + " " + id
                    + ": Freigabe nur bei Status WORKING.");
        }
    }

    private void validateReleased(boolean isReleased,
                                   String type, Object id) {
        if (!isReleased) {
            throw new IllegalStateException(
                    type + " " + id
                    + ": Revise nur bei Status RELEASED.");
        }
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}