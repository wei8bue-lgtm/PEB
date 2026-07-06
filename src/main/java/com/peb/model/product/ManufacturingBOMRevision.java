// src/main/java/com/peb/model/product/ManufacturingBOMRevision.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "manufacturing_bom_revision")
@SequenceGenerator(
    name           = "mfg_rev_seq_gen",
    sequenceName   = "MFG_BOM_REV_SEQ",
    allocationSize = 1
)
@Getter
@Setter
@ToString(exclude = "manufacturingBOM")
public class ManufacturingBOMRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturing_bom_id", nullable = false)
    private ManufacturingBOM manufacturingBOM;
}