package com.peb.search.mapper;

import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class DesignSearchIndexMapper implements SearchIndexMapper<Design> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.DESIGN;
    }

    @Override
    public SearchDocument toSearchDocument(Design design) {
        DesignRevision revision = design.getLatestRevision();
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(design.getName()),
                nullToEmpty(design.getId()),
                nullToEmpty(design.getMaterial()),
                nullToEmpty(design.getDepartment())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + design.getId())
                .objectId(design.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(design.getName())
                .nameSort(design.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null)
                .department(design.getDepartment())
                .material(design.getMaterial())
                .searchText(searchText)
                .createdBy(design.getCreatedBy())
                .createdDate(design.getCreatedDate())
                .lastModifiedDate(design.getLastModifiedDate())
                .detailUrl("/designs/" + design.getId())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
