// src/main/java/com/peb/model/product/ProductBOPRevision.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "product_bop_revision")
@SequenceGenerator(
    name           = "mfg_rev_seq_gen",
    sequenceName   = "PRODUCT_BOP_REV_SEQ",
    allocationSize = 1
)
@Getter
@Setter
@ToString(exclude = "productBOP")
public class ProductBOPRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bop_id", nullable = false)
    private ProductBOP productBOP;
}