package com.peb.search.mapper;

import com.peb.model.ebom.EBOMObject;
import com.peb.model.ebom.EBOMRevision;
import com.peb.search.SearchDocument;
import com.peb.util.ObjectType;

/**
 * Gemeinsame Mapping-Logik für Machine, System, Subsystem, EBOMModule,
 * Submodule und Auxiliary - strukturell identisch zu Part (alle erben
 * von EBOMObject/EBOMRevision). Statt 6x denselben Code zu kopieren,
 * bündeln die dünnen Mapper-Klassen (z.B. MachineSearchIndexMapper)
 * hier ihre Logik.
 */
final class EBOMSearchMapping {

    private EBOMSearchMapping() {
    }

    static SearchDocument map(EBOMObject entity, ObjectType objectType, String detailUrlPrefix) {
        EBOMRevision revision = entity.getLatestRevision();
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(entity.getName()),
                nullToEmpty(entity.getId()),
                nullToEmpty(entity.getMaterial()),
                nullToEmpty(entity.getSupplier()),
                nullToEmpty(entity.getDepartment())
        );

        return SearchDocument.builder()
                .id(objectType.name() + "_" + entity.getId())
                .objectId(entity.getId())
                .objectType(objectType)
                .objectTypeDisplayName(objectType.getDisplayName())
                .name(entity.getName())
                .nameSort(entity.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null)
                .department(entity.getDepartment())
                .material(entity.getMaterial())
                .supplier(entity.getSupplier())
                .searchText(searchText)
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .lastModifiedDate(entity.getLastModifiedDate())
                .detailUrl(detailUrlPrefix + entity.getId())
                .build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
