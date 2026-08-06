package com.peb.search.mapper;

import com.peb.model.product.ProductData;
import com.peb.model.product.ProductDataRevision;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

/**
 * ProductData nutzt com.peb.model.product.ManufacturingStatus statt
 * com.peb.util.ReleaseStatus (gleiche Werte WORKING/RELEASED/OBSOLETE,
 * andere Klasse) - dank String-Feld in SearchDocument (statt typisiertem
 * Enum) ist das hier trotzdem unkompliziert.
 */
@Component
public class ProductDataSearchIndexMapper implements SearchIndexMapper<ProductData> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.PRODUCT_DATA;
    }

    @Override
    public SearchDocument toSearchDocument(ProductData productData) {
        ProductDataRevision revision = productData.getLatestRevision();
        if (revision == null) {
            return null;
        }

        String searchText = String.join(" ",
                nullToEmpty(productData.getName()),
                nullToEmpty(productData.getId()),
                nullToEmpty(productData.getProductType()),
                nullToEmpty(productData.getDepartment())
        );

        return SearchDocument.builder()
                .id(getObjectType().name() + "_" + productData.getId())
                .objectId(productData.getId())
                .objectType(getObjectType())
                .objectTypeDisplayName(getObjectType().getDisplayName())
                .name(productData.getName())
                .nameSort(productData.getName())
                .revision(revision.getRevision())
                .releaseStatus(revision.getStatus() != null ? revision.getStatus().name() : null)
                .department(productData.getDepartment())
                .searchText(searchText)
                .createdBy(productData.getCreatedBy())
                .createdDate(productData.getCreatedDate())
                .lastModifiedDate(productData.getLastModifiedDate())
                // Route bestätigt: ProductDataController @RequestMapping("/product-data")
                .detailUrl("/product-data/" + productData.getId())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
