// src/main/java/com/peb/model/product/ProductBOMRevision.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "product_bom_revision")
@SequenceGenerator(
    name           = "mfg_rev_seq_gen",
    sequenceName   = "PRODUCT_BOM_REV_SEQ",
    allocationSize = 1
)
@Getter
@Setter
@ToString(exclude = "productBOM")
public class ProductBOMRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bom_id", nullable = false)
    private ProductBOM productBOM;
}