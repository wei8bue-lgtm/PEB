package com.peb.dto;

import com.peb.model.material.MaterialCompliance;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MaterialComplianceDTO {

    private Long id;
    private Boolean reachSvhc;
    private String reachSvhcSubstances;
    private LocalDate reachCheckedDate;
    private Boolean rohsCompliant;
    private LocalDate rohsCheckedDate;
    private MaterialCompliance.ConflictMineralsStatus conflictMineralsStatus;
    private String complianceDocumentPath;
    private LocalDate nextReviewDate;
    private String notes;

    public static MaterialComplianceDTO from(MaterialCompliance c) {
        MaterialComplianceDTO dto = new MaterialComplianceDTO();
        dto.setId(c.getId());
        dto.setReachSvhc(c.getReachSvhc());
        dto.setReachSvhcSubstances(c.getReachSvhcSubstances());
        dto.setReachCheckedDate(c.getReachCheckedDate());
        dto.setRohsCompliant(c.getRohsCompliant());
        dto.setRohsCheckedDate(c.getRohsCheckedDate());
        dto.setConflictMineralsStatus(c.getConflictMineralsStatus());
        dto.setComplianceDocumentPath(c.getComplianceDocumentPath());
        dto.setNextReviewDate(c.getNextReviewDate());
        dto.setNotes(c.getNotes());
        return dto;
    }
}
