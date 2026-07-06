// src/main/java/com/peb/model/product/ProductDataRevision.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "product_data_revision")
@SequenceGenerator(
    name           = "mfg_rev_seq_gen",
    sequenceName   = "PRODUCT_DATA_REV_SEQ",
    allocationSize = 1
)
@Getter
@Setter
@ToString(exclude = "productData")
public class ProductDataRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_data_id", nullable = false)
    private ProductData productData;
}