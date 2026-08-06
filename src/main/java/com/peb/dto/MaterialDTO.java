package com.peb.dto;

import com.peb.model.material.Material;
import com.peb.model.material.MaterialStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class MaterialDTO {

    private String id;
    private String name;
    private String department;
    private String materialGroup;
    private String specification;
    private String unitOfMeasure;

    private Double densityKgM3;
    private Double tensileStrengthMpa;
    private Double elasticModulusGpa;
    private Double meltingPointC;
    private Double thermalConductivityWMk;

    private MaterialStatus status;
    private String description;

    private String createdBy;
    private LocalDateTime createdDate;

    private String latestRevision;
    private String releasedRevision;

    private List<MaterialSupplierDTO> suppliers;
    private MaterialComplianceDTO compliance;

    public static MaterialDTO from(Material m) {
        MaterialDTO dto = new MaterialDTO();
        dto.setId(m.getId());
        dto.setName(m.getName());
        dto.setDepartment(m.getDepartment());
        dto.setMaterialGroup(m.getMaterialGroup());
        dto.setSpecification(m.getSpecification());
        dto.setUnitOfMeasure(m.getUnitOfMeasure());
        dto.setDensityKgM3(m.getDensityKgM3());
        dto.setTensileStrengthMpa(m.getTensileStrengthMpa());
        dto.setElasticModulusGpa(m.getElasticModulusGpa());
        dto.setMeltingPointC(m.getMeltingPointC());
        dto.setThermalConductivityWMk(m.getThermalConductivityWMk());
        dto.setStatus(m.getStatus());
        dto.setDescription(m.getDescription());
        dto.setCreatedBy(m.getCreatedBy());
        dto.setCreatedDate(m.getCreatedDate());

        if (m.getLatestRevision() != null) {
            dto.setLatestRevision(m.getLatestRevision().getRevision());
        }
        if (m.getReleasedRevision() != null) {
            dto.setReleasedRevision(m.getReleasedRevision().getRevision());
        }
        if (m.getSuppliers() != null) {
            dto.setSuppliers(m.getSuppliers().stream()
                    .map(MaterialSupplierDTO::from).collect(Collectors.toList()));
        }
        if (m.getCompliance() != null) {
            dto.setCompliance(MaterialComplianceDTO.from(m.getCompliance()));
        }
        return dto;
    }
}
