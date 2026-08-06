package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.base.Dataset;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.ebom.DatasetCategory;
import com.peb.repository.DesignRepository;
import com.peb.repository.DesignRevisionRepository;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

/**
 * Service for managing revisions
 * Handles revise operation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevisionService {

    private final DesignRepository designRepository;
    private final DesignRevisionRepository designRevisionRepository;
    private final EmailService emailService;
    private final IDGeneratorService idGeneratorService;
    private final DatasetService datasetService;

    // CAD-Kategorien, deren neuestes Dataset beim Revisionieren
    // automatisch in die neue Revision übernommen wird
    private static final List<DatasetCategory> CAD_DATASET_CATEGORIES = List.of(
            DatasetCategory.DESIGN_NX,
            DatasetCategory.DESIGN_INVENTOR,
            DatasetCategory.DESIGN_SOLIDEDGE
    );

    @Transactional
    public DesignRevision reviseDesign(String designId, String username) {
        Design design = designRepository.findById(designId)
            .orElseThrow(() -> new ObjectNotFoundException("Design not found: " + designId));

        DesignRevision latestRevision = design.getLatestRevision();
        if (latestRevision == null || latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }

        try {
            // Create new revision
            DesignRevision newRevision = new DesignRevision();
            newRevision.setDesign(design);
            newRevision.setName(latestRevision.getName());
            newRevision.setDepartment(latestRevision.getDepartment());
            newRevision.setMass(latestRevision.getMass());
            newRevision.setMaterial(latestRevision.getMaterial());
            
            // Increment revision number
            String nextRevision = incrementRevision(latestRevision.getRevision());
            newRevision.setRevision(nextRevision);
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);

            DesignRevision savedRevision = designRevisionRepository.save(newRevision);

            // ── NEU: neueste CAD-Datasets (NX/Inventor/SolidEdge) in die
            //         neue Revision übernehmen, mit fester Namenskonvention ──
            copyLatestCadDatasets(designId, latestRevision.getRevisionId(),
                                  savedRevision.getRevisionId(), nextRevision);

            emailService.sendRevisionSuccessEmail(username, designId, nextRevision);

            return savedRevision;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, designId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    /**
     * Übernimmt für jede CAD-Kategorie (DESIGN_NX, DESIGN_INVENTOR,
     * DESIGN_SOLIDEDGE) das jeweils neueste Dataset der alten Revision
     * als Kopie in die neue Revision – sofern in der Kategorie überhaupt
     * ein Dataset vorhanden ist.
     *
     * Der neue Dateiname wird dabei immer fest nach dem Schema
     * "%DesignId%_%Revision%.%Dateiendung%" vergeben (z. B. DS00000027_02.prt),
     * unabhängig davon, wie das Quell-Dataset ursprünglich benannt war.
     *
     * Ein Fehler beim Kopieren einer einzelnen Kategorie (z. B. fehlende
     * physische Datei) bricht das Revisionieren selbst NICHT ab – er wird
     * nur geloggt, damit ein Datenproblem bei einem CAD-Dataset nicht das
     * gesamte Revise verhindert.
     */
    private void copyLatestCadDatasets(String designId,
                                       Long   oldRevisionId,
                                       Long   newRevisionId,
                                       String newRevisionNumber) {

        for (DatasetCategory category : CAD_DATASET_CATEGORIES) {
            Dataset latest = datasetService.getLatestDatasetByCategory(
                    oldRevisionId, "DESIGN", category);

            if (latest == null) {
                continue; // keine Datei in dieser Kategorie vorhanden
            }

            String newFileName = designId + "_" + newRevisionNumber
                                  + extractExtension(latest.getFileName());

            try {
                datasetService.copyDatasetToRevision(latest, newRevisionId, newFileName);
                log.info("CAD-Dataset für Revise übernommen: Design[{}] Kategorie={} → '{}'",
                          designId, category, newFileName);
            } catch (IOException e) {
                log.error("CAD-Dataset konnte für Design[{}] (Kategorie={}) nicht kopiert "
                          + "werden: {}", designId, category, e.getMessage(), e);
            }
        }
    }

    /**
     * Liefert die Dateiendung inkl. Punkt (z. B. ".prt"), oder einen leeren
     * String, falls der Dateiname keine Endung enthält.
     */
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    private String incrementRevision(String currentRevision) {
        try {
            int revNum = Integer.parseInt(currentRevision);
            return String.format("%02d", revNum + 1);
        } catch (NumberFormatException e) {
            throw new WorkflowException("Invalid revision format: " + currentRevision);
        }
    }

    @Transactional
    public Design saveAs(String sourceDesignId, String newName, String username) {
        Design sourceDesign = designRepository.findById(sourceDesignId)
            .orElseThrow(() -> new ObjectNotFoundException("Source design not found: " + sourceDesignId));

        DesignRevision releasedRevision = sourceDesign.getReleasedRevision();
        if (releasedRevision == null) {
            throw new WorkflowException("No released revision found for save as operation");
        }

        try {
            // Create new design with new ID
            Design newDesign = new Design();
            newDesign.setId(idGeneratorService.generateId(ObjectType.DESIGN));
            newDesign.setName(newName);
            newDesign.setDepartment(sourceDesign.getDepartment());
            newDesign.setMass(sourceDesign.getMass());
            newDesign.setMaterial(sourceDesign.getMaterial());

            newDesign = designRepository.save(newDesign);

            // Create revision based on released revision
            DesignRevision newRevision = new DesignRevision();
            newRevision.setDesign(newDesign);
            newRevision.setName(newName);
            newRevision.setDepartment(releasedRevision.getDepartment());
            newRevision.setMass(releasedRevision.getMass());
            newRevision.setMaterial(releasedRevision.getMaterial());
            newRevision.setRevision("01");
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);

            designRevisionRepository.save(newRevision);

            emailService.sendSaveAsSuccessEmail(username, sourceDesignId, newDesign.getId());

            return newDesign;
        } catch (Exception e) {
            emailService.sendSaveAsFailureEmail(username, sourceDesignId, e.getMessage());
            throw new WorkflowException("Failed to save as: " + e.getMessage(), e);
        }
    }
}