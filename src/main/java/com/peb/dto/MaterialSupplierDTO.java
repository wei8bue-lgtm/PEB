package com.peb.dto;

import com.peb.model.material.MaterialSupplier;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialSupplierDTO {

    private Long id;
    private String supplierName;
    private String supplierPartNumber;
    private BigDecimal price;
    private String currency;
    private Integer leadTimeDays;
    private Integer minOrderQuantity;
    private Boolean approved;
    private Boolean preferred;
    private String notes;

    public static MaterialSupplierDTO from(MaterialSupplier s) {
        MaterialSupplierDTO dto = new MaterialSupplierDTO();
        dto.setId(s.getId());
        dto.setSupplierName(s.getSupplierName());
        dto.setSupplierPartNumber(s.getSupplierPartNumber());
        dto.setPrice(s.getPrice());
        dto.setCurrency(s.getCurrency());
        dto.setLeadTimeDays(s.getLeadTimeDays());
        dto.setMinOrderQuantity(s.getMinOrderQuantity());
        dto.setApproved(s.getApproved());
        dto.setPreferred(s.getPreferred());
        dto.setNotes(s.getNotes());
        return dto;
    }
}
