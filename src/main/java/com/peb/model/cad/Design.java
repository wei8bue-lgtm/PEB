package com.peb.model.cad;

import com.peb.model.base.BaseObject;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Design object (CAD Design)
 * ID format: DS00000001, DS00000002, ...
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "designs")
public class Design extends BaseObject {

    @Column(name = "mass", length = 8)
    @Size(max = 8)
    private String mass;

    @Column(name = "material", length = 256)
    @Size(max = 256)
    private String material;
	
    @Column(name = "linked_material_id", length = 10)
	private String linkedMaterialId;

    /**
     * Child_View – Freitext für Stücklisten-/Strukturbeschreibung.
     * Oracle: VARCHAR2(32672 BYTE) – maximale VARCHAR2-Länge in Oracle.
     * ddl-auto=update → Hibernate legt Spalte automatisch an.
     */
	@Lob
	@Column(name = "child_view")
	private String childView;

    @OneToMany(mappedBy = "design", cascade = CascadeType.ALL,
               orphanRemoval = true)
    private List<DesignRevision> revisions = new ArrayList<>();

    public DesignRevision getLatestRevision() {
        return revisions.stream()
                .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
                .orElse(null);
    }

    public DesignRevision getReleasedRevision() {
        return revisions.stream()
                .filter(r -> r.getReleaseStatus()
                        == com.peb.util.ReleaseStatus.RELEASED)
                .findFirst()
                .orElse(null);
    }
}