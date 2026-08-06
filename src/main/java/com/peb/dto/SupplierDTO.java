package com.peb.dto;

import com.peb.model.supplier.Supplier;
import com.peb.model.supplier.SupplierStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierDTO {

    private String id;
    private String name;
    private String street;
    private String zipCode;
    private String city;
    private String country;
    private String contactPerson;
    private String contactEmail;
    private String contactPhone;
    private String website;
    private String notes;
    private SupplierStatus status;
    private String createdBy;
    private LocalDateTime createdDate;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
    private String releasedRevision;

    public static SupplierDTO from(Supplier s) {
        SupplierDTO dto = new SupplierDTO();
        dto.setId(s.getId());
        dto.setName(s.getName());
        dto.setStreet(s.getStreet());
        dto.setZipCode(s.getZipCode());
        dto.setCity(s.getCity());
        dto.setCountry(s.getCountry());
        dto.setContactPerson(s.getContactPerson());
        dto.setContactEmail(s.getContactEmail());
        dto.setContactPhone(s.getContactPhone());
        dto.setWebsite(s.getWebsite());
        dto.setNotes(s.getNotes());
        dto.setStatus(s.getStatus());
        dto.setCreatedBy(s.getCreatedBy());
        dto.setCreatedDate(s.getCreatedDate());
        dto.setLastModifiedBy(s.getLastModifiedBy());
        dto.setLastModifiedDate(s.getLastModifiedDate());
        dto.setReleasedRevision(s.getReleasedRevision());
        return dto;
    }
}
