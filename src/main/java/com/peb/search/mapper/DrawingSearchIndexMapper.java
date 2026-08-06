package com.peb.search.mapper;

import com.peb.model.cad.Drawing;
import com.peb.model.cad.DrawingRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class DrawingSearchIndexMapper implements SearchIndexMapper<Drawing> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.DRAWING;
    }

    @Override
    public SearchDocument toSearchDocument(Drawing drawing) {
        DrawingRevision revision = drawing.getLatestRevision();
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(drawing.getName()),
                nullToEmpty(drawing.getId()),
                nullToEmpty(drawing.getDepartment())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + drawing.getId())
                .objectId(drawing.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(drawing.getName())
                .nameSort(drawing.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null)
                .department(drawing.getDepartment())
                .searchText(searchText)
                .createdBy(drawing.getCreatedBy())
                .createdDate(drawing.getCreatedDate())
                .lastModifiedDate(drawing.getLastModifiedDate())
                // Route bestätigt: DrawingController @RequestMapping("/drawings")
                .detailUrl("/drawings/" + drawing.getId())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
