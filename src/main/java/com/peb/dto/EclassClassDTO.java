package com.peb.dto;

import com.peb.model.eclass.EclassClass;
import com.peb.model.eclass.EclassProperty;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO für ECLASS-Klassen-Übersicht und Detailansicht
 */
@Data
public class EclassClassDTO {

    private Long id;
    private String code;
    private String version;
    private String irdi;
    private String name;
    private String nameEn;
    private String definition;
    private String keywords;
    private Integer classLevel;
    private String segment;
    private String mainGroup;
    private String groupCode;
    private List<PropertyDTO> properties;

    @Data
    public static class PropertyDTO {
        private Long id;
        private String irdi;
        private String name;
        private String nameEn;
        private String unit;
        private String dataType;
        private Integer sortOrder;

        public static PropertyDTO from(EclassProperty p) {
            PropertyDTO dto = new PropertyDTO();
            dto.setId(p.getId());
            dto.setIrdi(p.getIrdi());
            dto.setName(p.getName());
            dto.setNameEn(p.getNameEn());
            dto.setUnit(p.getUnit());
            dto.setDataType(p.getDataType());
            dto.setSortOrder(p.getSortOrder());
            return dto;
        }
    }

    public static EclassClassDTO from(EclassClass c) {
        EclassClassDTO dto = new EclassClassDTO();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setVersion(c.getVersion());
        dto.setIrdi(c.getIrdi());
        dto.setName(c.getName());
        dto.setNameEn(c.getNameEn());
        dto.setDefinition(c.getDefinition());
        dto.setKeywords(c.getKeywords());
        dto.setClassLevel(c.getClassLevel());
        dto.setSegment(c.getSegment());
        dto.setMainGroup(c.getMainGroup());
        dto.setGroupCode(c.getGroupCode());
        if (c.getProperties() != null) {
            dto.setProperties(c.getProperties().stream()
                    .map(PropertyDTO::from)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    /** Anzeige-Label für die UI */
    public String getDisplayLabel() {
        return code + " – " + (name != null ? name : nameEn != null ? nameEn : "");
    }
}
