package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.repository.DesignRepository;
import com.peb.repository.DesignRevisionRepository;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing revisions
 * Handles revise operation
 */
@Service
@RequiredArgsConstructor
public class RevisionService {

    private final DesignRepository designRepository;
    private final DesignRevisionRepository designRevisionRepository;
    private final EmailService emailService;
    private final IDGeneratorService idGeneratorService;

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

            emailService.sendRevisionSuccessEmail(username, designId, nextRevision);

            return savedRevision;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, designId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
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