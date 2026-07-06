// src/main/java/com/peb/model/product/PlantBOPRevision.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "plant_bop_revision")
@SequenceGenerator(
    name           = "mfg_rev_seq_gen",
    sequenceName   = "PLANT_BOP_REV_SEQ",
    allocationSize = 1
)
@Getter
@Setter
@ToString(exclude = "plantBOP")
public class PlantBOPRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_bop_id", nullable = false)
    private PlantBOP plantBOP;
}