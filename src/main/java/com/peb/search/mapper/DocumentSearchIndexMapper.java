package com.peb.search.mapper;

import com.peb.model.document.Document;
import com.peb.model.document.DocumentRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class DocumentSearchIndexMapper implements SearchIndexMapper<Document> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.DOCUMENT;
    }

    @Override
    public SearchDocument toSearchDocument(Document document) {
        DocumentRevision revision = document.getLatestRevision();
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(document.getName()),
                nullToEmpty(document.getId()),
                nullToEmpty(document.getDescription()),
                nullToEmpty(document.getAttribute1()),
                nullToEmpty(document.getAttribute2()),
                nullToEmpty(document.getDepartment())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + document.getId())
                .objectId(document.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(document.getName())
                .nameSort(document.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null)
                .department(document.getDepartment())
                .searchText(searchText)
                .createdBy(document.getCreatedBy())
                .createdDate(document.getCreatedDate())
                .lastModifiedDate(document.getLastModifiedDate())
                // Route bestätigt: DocumentController @RequestMapping("/documents")
                .detailUrl("/documents/" + document.getId())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
