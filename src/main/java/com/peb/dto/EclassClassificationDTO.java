package com.peb.dto;

import com.peb.model.eclass.ObjectClassification;
import com.peb.model.eclass.ObjectPropertyValue;
import com.peb.util.ObjectType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DTO für die Klassifizierung eines PEB-Objekts.
 * Enthält die ECLASS-Klasse und alle Merkmalswerte.
 */
@Data
public class EclassClassificationDTO {

    private Long classificationId;
    private String objectId;
    private ObjectType objectType;
    private String eclassCode;
    private String eclassName;
    private String eclassVersion;
    private String eclassIrdi;
    private Long eclassClassId;
    private String createdBy;
    private LocalDateTime createdDate;

    /**
     * Map: propertyId → Wert (für das Formular)
     */
    private Map<Long, String> propertyValues = new LinkedHashMap<>();

    /**
     * Map: propertyId → property name (für die Anzeige)
     */
    private Map<Long, String> propertyNames = new LinkedHashMap<>();

    /**
     * Map: propertyId → unit (für die Anzeige)
     */
    private Map<Long, String> propertyUnits = new LinkedHashMap<>();

    public static EclassClassificationDTO from(ObjectClassification oc) {
        EclassClassificationDTO dto = new EclassClassificationDTO();
        dto.setClassificationId(oc.getId());
        dto.setObjectId(oc.getObjectId());
        dto.setObjectType(oc.getObjectType());
        dto.setCreatedBy(oc.getCreatedBy());
        dto.setCreatedDate(oc.getCreatedDate());

        if (oc.getEclassClass() != null) {
            dto.setEclassCode(oc.getEclassClass().getCode());
            dto.setEclassName(oc.getEclassClass().getName() != null
                    ? oc.getEclassClass().getName()
                    : oc.getEclassClass().getNameEn());
            dto.setEclassVersion(oc.getEclassClass().getVersion());
            dto.setEclassIrdi(oc.getEclassClass().getIrdi());
            dto.setEclassClassId(oc.getEclassClass().getId());
        }

        if (oc.getPropertyValues() != null) {
            for (ObjectPropertyValue pv : oc.getPropertyValues()) {
                Long propId = pv.getProperty().getId();
                dto.getPropertyValues().put(propId, pv.getValue());
                dto.getPropertyNames().put(propId,
                        pv.getProperty().getName() != null
                                ? pv.getProperty().getName()
                                : pv.getProperty().getNameEn());
                dto.getPropertyUnits().put(propId, pv.getProperty().getUnit());
            }
        }
        return dto;
    }
}
