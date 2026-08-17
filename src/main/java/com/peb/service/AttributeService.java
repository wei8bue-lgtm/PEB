package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.PEBException;
import com.peb.model.cad.Design;
import com.peb.model.cad.Drawing;
import com.peb.model.ebom.EBOMObject;
import com.peb.repository.DesignRepository;
import com.peb.repository.DrawingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service für Attribut-Verwaltung
 * Unterstützt Lesen und Schreiben von Attributen
 * für Design, Drawing und alle eBOM-Objekte
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttributeService {

    private final DesignService  designService;
    private final DrawingService drawingService;
    private final EBOMService    ebomService;
    private final DesignRepository  designRepository;
    private final DrawingRepository drawingRepository;

    // ══════════════════════════════════════════════════════
    // ALLE ATTRIBUTE LESEN
    // ══════════════════════════════════════════════════════

    /**
     * Alle Attribute eines Objekts als Map lesen
     * Key = Attributname, Value = Attributwert
     */
    @Transactional(readOnly = true)
    public Map<String, String> getAttributes(String objectId) {
        String prefix = objectId.substring(0, 2);

        return switch (prefix) {
            case "DS" -> getDesignAttributes(objectId);
            case "DR" -> getDrawingAttributes(objectId);
            default   -> getEBOMAttributes(objectId);
        };
    }

    /**
     * Einzelnes Attribut lesen
     */
    @Transactional(readOnly = true)
    public String getAttribute(String objectId, String attributeName) {
        Map<String, String> attributes = getAttributes(objectId);

        if (!attributes.containsKey(attributeName.toLowerCase())) {
            throw new PEBException("Attribut nicht gefunden: " + attributeName +
                                   " für Objekt: " + objectId);
        }
        return attributes.get(attributeName.toLowerCase());
    }

    // ══════════════════════════════════════════════════════
    // EINZELNES ATTRIBUT SCHREIBEN
    // ══════════════════════════════════════════════════════

    /**
     * Einzelnes Attribut setzen
     */
    @Transactional
    public Map<String, String> setAttribute(String objectId,
                                            String attributeName,
                                            String attributeValue) {
        String prefix = objectId.substring(0, 2);

        switch (prefix) {
            case "DS" -> setDesignAttribute(objectId, attributeName, attributeValue);
            case "DR" -> setDrawingAttribute(objectId, attributeName, attributeValue);
            default   -> setEBOMAttribute(objectId, attributeName, attributeValue);
        }

        log.info("Attribut gesetzt: {}:{} = {}", objectId, attributeName, attributeValue);
        return getAttributes(objectId);
    }

    // ══════════════════════════════════════════════════════
    // MEHRERE ATTRIBUTE GLEICHZEITIG SCHREIBEN (Bulk)
    // ══════════════════════════════════════════════════════

    /**
     * Mehrere Attribute auf einmal setzen
     */
    @Transactional
    public Map<String, String> setAttributes(String objectId,
                                             Map<String, String> attributes) {
        attributes.forEach((key, value) ->
            setAttribute(objectId, key, value)
        );

        log.info("Bulk-Update: {} Attribute für {}", attributes.size(), objectId);
        return getAttributes(objectId);
    }

    // ══════════════════════════════════════════════════════
    // DESIGN ATTRIBUTE
    // ══════════════════════════════════════════════════════

    private Map<String, String> getDesignAttributes(String designId) {
        Design design = designService.getDesign(designId);

        Map<String, String> attrs = new LinkedHashMap<>();

        // Read-Only Attribute
        attrs.put("id",             nullSafe(design.getId()));
        attrs.put("revision",       design.getLatestRevision() != null
                                    ? nullSafe(design.getLatestRevision().getRevision())
                                    : "");
        attrs.put("releasestatus",  design.getLatestRevision() != null
                                    ? design.getLatestRevision().getReleaseStatus().name()
                                    : "");
        // Editierbare Attribute
        attrs.put("name",           nullSafe(design.getName()));
        attrs.put("department",     nullSafe(design.getDepartment()));
        attrs.put("mass",           nullSafe(design.getMass()));
        attrs.put("material",       nullSafe(design.getMaterial()));

        // Audit-Attribute (read-only)
        attrs.put("createdby",      nullSafe(design.getCreatedBy()));
        attrs.put("createddate",    design.getCreatedDate() != null
                                    ? design.getCreatedDate().toString() : "");
        attrs.put("lastmodifiedby", nullSafe(design.getLastModifiedBy()));
        attrs.put("lastmodifieddate", design.getLastModifiedDate() != null
                                    ? design.getLastModifiedDate().toString() : "");
        return attrs;
    }

    private void setDesignAttribute(String designId,
                                    String attributeName,
                                    String attributeValue) {
        Design design = designService.getDesign(designId);

        switch (attributeName.toLowerCase()) {
            case "name"       -> design.setName(attributeValue);
            case "department" -> design.setDepartment(attributeValue);
            case "mass"       -> {
                validateMass(attributeValue);
                design.setMass(attributeValue);
            }
            case "material"   -> design.setMaterial(attributeValue);

            // Read-Only Attribute abweisen
            case "id", "revision", "releasestatus",
                 "createdby", "createddate",
                 "lastmodifiedby", "lastmodifieddate" ->
                throw new PEBException("Attribut ist schreibgeschützt: " + attributeName);

            default -> throw new PEBException(
                    "Unbekanntes Attribut für Design: " + attributeName);
        }

        designRepository.save(design);
    }

    // ══════════════════════════════════════════════════════
    // DRAWING ATTRIBUTE
    // ══════════════════════════════════════════════════════

    private Map<String, String> getDrawingAttributes(String drawingId) {
        Drawing drawing = drawingService.getDrawing(drawingId);

        Map<String, String> attrs = new LinkedHashMap<>();

        attrs.put("id",             nullSafe(drawing.getId()));
        attrs.put("revision",       drawing.getLatestRevision() != null
                                    ? nullSafe(drawing.getLatestRevision().getRevision())
                                    : "");
        attrs.put("releasestatus",  drawing.getLatestRevision() != null
                                    ? drawing.getLatestRevision().getReleaseStatus().name()
                                    : "");
        attrs.put("name",           nullSafe(drawing.getName()));
        attrs.put("department",     nullSafe(drawing.getDepartment()));
        attrs.put("createdby",      nullSafe(drawing.getCreatedBy()));
        attrs.put("createddate",    drawing.getCreatedDate() != null
                                    ? drawing.getCreatedDate().toString() : "");
        attrs.put("lastmodifiedby", nullSafe(drawing.getLastModifiedBy()));
        attrs.put("lastmodifieddate", drawing.getLastModifiedDate() != null
                                    ? drawing.getLastModifiedDate().toString() : "");
        return attrs;
    }

    private void setDrawingAttribute(String drawingId,
                                     String attributeName,
                                     String attributeValue) {
        Drawing drawing = drawingService.getDrawing(drawingId);

        switch (attributeName.toLowerCase()) {
            case "name"       -> drawing.setName(attributeValue);
            case "department" -> drawing.setDepartment(attributeValue);

            case "id", "revision", "releasestatus",
                 "createdby", "createddate",
                 "lastmodifiedby", "lastmodifieddate" ->
                throw new PEBException("Attribut ist schreibgeschützt: " + attributeName);

            default -> throw new PEBException(
                    "Unbekanntes Attribut für Drawing: " + attributeName);
        }

        drawingRepository.save(drawing);
    }

    // ══════════════════════════════════════════════════════
    // EBOM ATTRIBUTE
    // ══════════════════════════════════════════════════════

    private Map<String, String> getEBOMAttributes(String objectId) {
        EBOMObject obj = ebomService.getEBOMObject(objectId);

        Map<String, String> attrs = new LinkedHashMap<>();

        attrs.put("id",              nullSafe(obj.getId()));
        attrs.put("objecttype",      obj.getObjectType().getDisplayName());
        attrs.put("revision",        obj.getLatestRevision() != null
                                     ? nullSafe(obj.getLatestRevision().getRevision())
                                     : "");
        attrs.put("releasestatus",   obj.getLatestRevision() != null
                                     ? obj.getLatestRevision().getReleaseStatus().name()
                                     : "");
        attrs.put("name",            nullSafe(obj.getName()));
        attrs.put("department",      nullSafe(obj.getDepartment()));
        attrs.put("mass",            nullSafe(obj.getMass()));
        attrs.put("material",        nullSafe(obj.getMaterial()));
        attrs.put("supplier",        nullSafe(obj.getSupplier()));
        attrs.put("linkeddesignid",  nullSafe(obj.getLinkedDesignId()));
        attrs.put("createdby",       nullSafe(obj.getCreatedBy()));
        attrs.put("createddate",     obj.getCreatedDate() != null
                                     ? obj.getCreatedDate().toString() : "");
        attrs.put("lastmodifiedby",  nullSafe(obj.getLastModifiedBy()));
        attrs.put("lastmodifieddate", obj.getLastModifiedDate() != null
                                     ? obj.getLastModifiedDate().toString() : "");
        return attrs;
    }

    private void setEBOMAttribute(String objectId,
                                  String attributeName,
                                  String attributeValue) {
        EBOMObject obj = ebomService.getEBOMObject(objectId);

        switch (attributeName.toLowerCase()) {
            case "name"           -> obj.setName(attributeValue);
            case "department"     -> obj.setDepartment(attributeValue);
            case "mass"           -> {
                validateMass(attributeValue);
                obj.setMass(attributeValue);
            }
            case "material"       -> obj.setMaterial(attributeValue);
            case "supplier"       -> obj.setSupplier(attributeValue);
            case "linkeddesignid" -> obj.setLinkedDesignId(attributeValue);

            case "id", "objecttype", "revision", "releasestatus",
                 "createdby", "createddate",
                 "lastmodifiedby", "lastmodifieddate" ->
                throw new PEBException("Attribut ist schreibgeschützt: " + attributeName);

            default -> throw new PEBException(
                    "Unbekanntes Attribut für eBOM-Objekt: " + attributeName);
        }

        // Über EBOMService speichern (inkl. Synchronisation)
        ebomService.updateEBOMObject(
            objectId,
            obj.getName(),
            obj.getDepartment(),
            obj.getMass(),
            obj.getMaterial(),
            obj.getSupplier(),
            obj.getLinkedDesignId(),
            null  // bomType bleibt unverändert
        );
    }

    // ══════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ══════════════════════════════════════════════════════

    /**
     * Liefert alle editierbaren Attribute eines Objekttyps
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAttributeSchema(String objectId) {
        String prefix = objectId.substring(0, 2);

        Map<String, Object> schema = new LinkedHashMap<>();

        switch (prefix) {
            case "DS" -> {
                schema.put("objectType", "Design");
                schema.put("readOnly",   new String[]{"id", "revision",
                           "releasestatus", "createdby", "createddate",
                           "lastmodifiedby", "lastmodifieddate"});
                schema.put("editable",   new String[]{"name", "department",
                           "mass", "material"});
            }
            case "DR" -> {
                schema.put("objectType", "Drawing");
                schema.put("readOnly",   new String[]{"id", "revision",
                           "releasestatus", "createdby", "createddate",
                           "lastmodifiedby", "lastmodifieddate"});
                schema.put("editable",   new String[]{"name", "department"});
            }
            default -> {
                EBOMObject obj = ebomService.getEBOMObject(objectId);
                schema.put("objectType", obj.getObjectType().getDisplayName());
                schema.put("readOnly",   new String[]{"id", "objecttype",
                           "revision", "releasestatus", "createdby",
                           "createddate", "lastmodifiedby", "lastmodifieddate"});
                schema.put("editable",   new String[]{"name", "department",
                           "mass", "material", "supplier", "linkeddesignid"});
            }
        }

        return schema;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private void validateMass(String mass) {
        if (mass != null && !mass.isEmpty()) {
            if (mass.length() > 8) {
                throw new PEBException("Mass darf max. 8 Zeichen haben");
            }
            if (!mass.matches("\\d+(\\.\\d+)?")) {
                throw new PEBException("Mass muss eine Zahl sein (z.B. 100 oder 10.5)");
            }
        }
    }
}