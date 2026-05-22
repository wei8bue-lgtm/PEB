// src/main/java/com/peb/model/product/PlantBOPNode.java
package com.peb.model.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Knoten in der PlantBOP-Baumstruktur.
 *
 * WICHTIG: Alle Assoziationen sind LAZY.
 * EAGER auf OneToMany + LAZY auf ManyToOne in derselben Entity
 * führt zu Hibernate-Fehlern bei JPQL JOIN FETCH Queries.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "plant_bop_node")
@ToString(exclude = {"parent", "children", "plantBOP",
                     "productBOP", "manufacturingBOM"})
@EqualsAndHashCode(exclude = {"parent", "children", "plantBOP",
                               "productBOP", "manufacturingBOM"})
public class PlantBOPNode {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "plant_bop_node_seq")
    @SequenceGenerator(name           = "plant_bop_node_seq",
                       sequenceName   = "plant_bop_node_seq",
                       allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", length = 50, nullable = false)
    private NodeType nodeType;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    // ── Selbstreferenz ────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PlantBOPNode parent;

    /**
     * LAZY – nicht EAGER!
     * Kinder werden explizit im Service/Repository geladen.
     * EAGER auf selbstreferenzielle OneToMany führt zu:
     *   - Hibernate Fetch-Konflikten
     *   - Endlosrekursion bei JSON-Serialisierung
     *   - N+1 Queries
     */
    @OneToMany(mappedBy     = "parent",
               cascade      = CascadeType.ALL,
               orphanRemoval = true,
               fetch        = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<PlantBOPNode> children = new ArrayList<>();

    // ── Zugehörigkeit ─────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_bop_id", nullable = false)
    private PlantBOP plantBOP;

    // ── Optionale Referenzen ──────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bop_id")
    private ProductBOP productBOP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mfg_bom_id")
    private ManufacturingBOM manufacturingBOM;

    @Column(name = "boe_id")
    private Long boeId;

    @Size(max = 255)
    @Column(name = "boe_name", length = 255)
    private String boeName;

    // ── Hilfsmethoden ─────────────────────────────────────────────
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public int getDepth() {
        int depth = 0;
        PlantBOPNode cur = this.parent;
        while (cur != null) {
            depth++;
            cur = cur.parent;
        }
        return depth;
    }

    // ── Enum ──────────────────────────────────────────────────────
    public enum NodeType {
        FOLDER(           "Folder",    "📁"),
        PRODUCT_BOP(      "Product BOP","📋"),
        MANUFACTURING_BOM("Mfg BOM",   "🔧"),
        BOE(              "BOE",        "📦");

        private final String displayName;
        private final String icon;

        NodeType(String displayName, String icon) {
            this.displayName = displayName;
            this.icon        = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getIcon()        { return icon;        }
        public String getLabel()       {
            return icon + " " + displayName;
        }
    }
}