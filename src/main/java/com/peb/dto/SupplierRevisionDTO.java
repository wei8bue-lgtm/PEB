package com.peb.dto;

import com.peb.model.supplier.SupplierRevision;
import com.peb.util.ReleaseStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierRevisionDTO {

    private Long id;
    private String supplierId;
    private String revision;
    private ReleaseStatus releaseStatus;
    private String changeDescription;
    private String createdBy;
    private LocalDateTime createdDate;
    private String releasedBy;
    private LocalDateTime releasedDate;

    public static SupplierRevisionDTO from(SupplierRevision r) {
        SupplierRevisionDTO dto = new SupplierRevisionDTO();
        dto.setId(r.getId());
        dto.setSupplierId(r.getSupplier() != null ? r.getSupplier().getId() : null);
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
