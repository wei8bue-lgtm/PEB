package com.peb.search.mapper;

import com.peb.model.material.Material;
import com.peb.model.material.MaterialRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

/**
 * Material weicht vom Standard-Muster ab: MaterialRevision erbt NICHT von
 * BaseRevision (eigenständige Klasse), daher hier kein Zugriff auf
 * revision.getObjectId()/syncFromParent() wie bei den anderen Mappern -
 * die benötigten Felder werden direkt gelesen.
 */
@Component
public class MaterialSearchIndexMapper implements SearchIndexMapper<Material> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.MATERIAL;
    }

    @Override
    public SearchDocument toSearchDocument(Material material) {
        MaterialRevision revision = material.getLatestRevision();
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(material.getName()),
                nullToEmpty(material.getId()),
                nullToEmpty(material.getMaterialGroup()),
                nullToEmpty(material.getSpecification()),
                nullToEmpty(material.getDepartment())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + material.getId())
                .objectId(material.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(material.getName())
                .nameSort(material.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null)
                .department(material.getDepartment())
                .material(material.getMaterialGroup())
                .searchText(searchText)
                .createdBy(material.getCreatedBy())
                .createdDate(material.getCreatedDate())
                .lastModifiedDate(material.getLastModifiedDate())
                // Route bestätigt: MaterialController @RequestMapping("/materials")
                .detailUrl("/materials/" + material.getId())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
