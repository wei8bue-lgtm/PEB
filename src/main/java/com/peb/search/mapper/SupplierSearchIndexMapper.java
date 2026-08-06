package com.peb.search.mapper;

import com.peb.model.supplier.Supplier;
import com.peb.model.supplier.SupplierRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

/**
 * Supplier hat (anders als die meisten Objekttypen) kein department-Feld
 * und keine getLatestRevision()-Methode auf der Entity selbst (nur
 * getReleasedRevision(), das einen String statt das Revisionsobjekt
 * liefert) - daher wird die aktuellste Revision hier lokal ermittelt.
 */
@Component
public class SupplierSearchIndexMapper implements SearchIndexMapper<Supplier> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.SUPPLIER;
    }

    @Override
    public SearchDocument toSearchDocument(Supplier supplier) {
        SupplierRevision revision = latestRevision(supplier);
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(supplier.getName()),
                nullToEmpty(supplier.getId()),
                nullToEmpty(supplier.getCity()),
                nullToEmpty(supplier.getCountry()),
                nullToEmpty(supplier.getContactPerson())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + supplier.getId())
                .objectId(supplier.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(supplier.getName())
                .nameSort(supplier.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null)
                // Supplier hat kein department-Feld
                .department(null)
                .searchText(searchText)
                .createdBy(supplier.getCreatedBy())
                .createdDate(supplier.getCreatedDate())
                .lastModifiedDate(supplier.getLastModifiedDate())
                // Route bestätigt: SupplierController @RequestMapping("/suppliers")
                .detailUrl("/suppliers/" + supplier.getId())
                .build();
    }

    private SupplierRevision latestRevision(Supplier supplier) {
        if (supplier.getRevisions() == null || supplier.getRevisions().isEmpty()) {
            return null;
        }
        return supplier.getRevisions().stream()
                .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
                .orElse(null);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
