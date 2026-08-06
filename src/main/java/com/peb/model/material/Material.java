package com.peb.model.material;

import com.peb.model.base.BaseObject;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Material-Stammdatenobjekt.
 * ID-Format: MT00000001, MT00000002, ...
 *
 * Analog zu {@code Design}: kein Strukturknoten der eBOM-Hierarchie
 * (daher Erbe von {@link BaseObject}, nicht {@code EBOMObject}),
 * sondern ein referenzierbares Stammdatenobjekt mit eigener Revisionierung.
 *
 * Klassifizierung erfolgt wie bei allen anderen PEB-Objekten optional
 * über {@code ObjectClassification} (objectId + ObjectType.MATERIAL).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "materials")
public class Material extends BaseObject {

    /** Werkstoffgruppe, z.B. "Stahl", "Aluminium", "Kunststoff PA6" */
    @Column(name = "material_group", length = 256)
    @Size(max = 256)
    private String materialGroup;

    /** Norm/Spezifikation, z.B. "EN 10025-2 S355" */
    @Column(name = "specification", length = 256)
    @Size(max = 256)
    private String specification;

    /** Mengeneinheit: kg, m, m2, m3, Stk */
    @Column(name = "unit_of_measure", length = 20)
    private String unitOfMeasure;

    // ── Physikalische Kennwerte (typisiert statt generischer Merkmale) ──

    @Column(name = "density_kg_m3")
    private Double densityKgM3;

    @Column(name = "tensile_strength_mpa")
    private Double tensileStrengthMpa;

    @Column(name = "elastic_modulus_gpa")
    private Double elasticModulusGpa;

    @Column(name = "melting_point_c")
    private Double meltingPointC;

    @Column(name = "thermal_conductivity_w_mk")
    private Double thermalConductivityWMk;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private MaterialStatus status = MaterialStatus.ACTIVE;

    @Lob
    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "material", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdDate ASC")
    private List<MaterialRevision> revisions = new ArrayList<>();

    @OneToOne(mappedBy = "material", cascade = CascadeType.ALL, orphanRemoval = true)
    private MaterialCompliance compliance;

    @OneToMany(mappedBy = "material", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaterialSupplier> suppliers = new ArrayList<>();

    public ObjectType getObjectType() {
        return ObjectType.MATERIAL;
    }

    public MaterialRevision getLatestRevision() {
        return revisions.stream()
                .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
                .orElse(null);
    }

    public MaterialRevision getReleasedRevision() {
        return revisions.stream()
                .filter(r -> r.getReleaseStatus() == ReleaseStatus.RELEASED)
                .findFirst()
                .orElse(null);
    }
}
