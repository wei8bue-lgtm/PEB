package com.peb.search.mapper;

import com.peb.model.cim.CimObject;
import com.peb.model.cim.CimRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class CimSearchIndexMapper implements SearchIndexMapper<CimObject> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.CIM;
    }

    @Override
    public SearchDocument toSearchDocument(CimObject cimObject) {
        CimRevision latest = cimObject.getLatestRevision();
        if (latest == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(cimObject.getName()),
                nullToEmpty(cimObject.getId()),
                nullToEmpty(cimObject.getSourceMaeObjectId()),
                nullToEmpty(cimObject.getDepartment())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + cimObject.getId())
                .objectId(cimObject.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(cimObject.getName())
                .nameSort(cimObject.getName())
                .revision(latest.getRevision())
                .releaseStatus(latest.getReleaseStatus() != null ? latest.getReleaseStatus().name() : null)
                .department(cimObject.getDepartment())
                .searchText(searchText)
                .createdBy(cimObject.getCreatedBy())
                .createdDate(cimObject.getCreatedDate())
                .lastModifiedDate(cimObject.getLastModifiedDate())
                .detailUrl("/api/cim/" + cimObject.getId())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
