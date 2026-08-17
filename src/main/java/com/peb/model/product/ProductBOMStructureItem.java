package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_bom_structure_item")
@Getter
@Setter
public class ProductBOMStructureItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_bom_struct_item_seq")
    @SequenceGenerator(name = "product_bom_struct_item_seq", sequenceName = "PRODUCT_BOM_STRUCT_ITEM_SEQ", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bom_id", nullable = false)
    private ProductBOM productBOM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_item_id")
    private ProductBOMStructureItem parentItem;

    @OneToMany(mappedBy = "parentItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductBOMStructureItem> children = new ArrayList<>();

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "object_id", nullable = false, length = 12)
    private String objectId;

    @Column(name = "object_type", length = 100)
    private String objectType;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "revision", length = 20)
    private String revision;

    @Column(name = "release_status", length = 40)
    private String releaseStatus;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "position_number", length = 20)
    private String positionNumber;

    @Column(name = "reference_designator", length = 50)
    private String referenceDesignator;

    @Column(name = "bmk", length = 100)
    private String bmk;

    @Column(name = "relation_type", length = 20)
    private String relationType;

    @Column(name = "config_rule", length = 500)
    private String configRule;

    @Column(name = "alternative_group", length = 50)
    private String alternativeGroup;
}
