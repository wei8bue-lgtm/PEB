package com.peb.search.mapper;

import com.peb.model.document.DocumentTemplate;
import com.peb.model.document.DocumentTemplateRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class DocumentTemplateSearchIndexMapper implements SearchIndexMapper<DocumentTemplate> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.DOCUMENT_TEMPLATE;
    }

    @Override
    public SearchDocument toSearchDocument(DocumentTemplate template) {
        DocumentTemplateRevision revision = template.getLatestRevision();
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(template.getName()),
                nullToEmpty(template.getId()),
                nullToEmpty(template.getDescription()),
                nullToEmpty(template.getDepartment())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + template.getId())
                .objectId(template.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(template.getName())
                .nameSort(template.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null)
                .department(template.getDepartment())
                .searchText(searchText)
                .createdBy(template.getCreatedBy())
                .createdDate(template.getCreatedDate())
                .lastModifiedDate(template.getLastModifiedDate())
                // Route bestätigt: DocumentTemplateController @RequestMapping("/document-templates")
                .detailUrl("/document-templates/" + template.getId())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
