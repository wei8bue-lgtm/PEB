package com.peb.dto;

import com.peb.util.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO für ein ähnliches / wiederverwendbares Objekt in der ECLASS-Suche.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EclassSimilarResultDTO {

    private String objectId;
    private ObjectType objectType;
    private String objectName;
    private String objectDepartment;
    private String eclassCode;
    private String eclassName;

    /** Link zur Detailseite des Objekts */
    public String getDetailUrl() {
        if (objectType == null) return "#";
        return switch (objectType) {
            case DESIGN   -> "/designs/" + objectId;
            case DRAWING  -> "/drawings/" + objectId;
            case MACHINE  -> "/ebom/machine/" + objectId;
            case SYSTEM   -> "/ebom/system/" + objectId;
            case SUBSYSTEM -> "/ebom/subsystem/" + objectId;
            case MODULE   -> "/ebom/module/" + objectId;
            case SUBMODULE -> "/ebom/submodule/" + objectId;
            case PART     -> "/ebom/part/" + objectId;
            case AUXILIARY -> "/ebom/auxiliary/" + objectId;
            default       -> "#";
        };
    }

    /** Icon / Badge-Farbe je Objekttyp */
    public String getTypeBadgeClass() {
        if (objectType == null) return "badge-secondary";
        return switch (objectType) {
            case DESIGN, DRAWING -> "badge-info";
            case MACHINE         -> "badge-danger";
            case SYSTEM          -> "badge-warning";
            case SUBSYSTEM       -> "badge-primary";
            case MODULE          -> "badge-success";
            case SUBMODULE       -> "badge-secondary";
            case PART            -> "badge-dark";
            case AUXILIARY       -> "badge-light";
            default              -> "badge-secondary";
        };
    }
}
