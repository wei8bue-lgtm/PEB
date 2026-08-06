package com.peb.dto;

import com.peb.model.material.MaterialRevision;
import com.peb.util.ReleaseStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialRevisionDTO {

    private Long id;
    private String materialId;
    private String revision;
    private ReleaseStatus releaseStatus;
    private String changeDescription;
    private String createdBy;
    private LocalDateTime createdDate;
    private String releasedBy;
    private LocalDateTime releasedDate;

    public static MaterialRevisionDTO from(MaterialRevision r) {
        MaterialRevisionDTO dto = new MaterialRevisionDTO();
        dto.setId(r.getId());
        dto.setMaterialId(r.getMaterial() != null ? r.getMaterial().getId() : null);
        dto.setRevision(r.getRevision());
        dto.setReleaseStatus(r.getReleaseStatus());
        dto.setChangeDescription(r.getChangeDescription());
        dto.setCreatedBy(r.getCreatedBy());
        dto.setCreatedDate(r.getCreatedDate());
        dto.setReleasedBy(r.getReleasedBy());
        dto.setReleasedDate(r.getReleasedDate());
        return dto;
    }
}
