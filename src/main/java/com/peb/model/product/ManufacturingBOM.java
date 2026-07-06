// src/main/java/com/peb/model/product/ManufacturingBOM.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "manufacturing_bom")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString(exclude = {"productBOMs", "operations", "revisions"})
public class ManufacturingBOM {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "mfg_bom_seq")
    @SequenceGenerator(name           = "mfg_bom_seq",
                       sequenceName   = "MFG_BOM_SEQ",
                       allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "bom_version", length = 10)
    private String bomVersion = "01";

    @Column(columnDefinition = "CLOB")
    private String description;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by",
            updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;

    // ── Beziehungen ───────────────────────────────────────────────

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name               = "mfg_bom_product_bom",
        joinColumns        = @JoinColumn(name = "mfg_bom_id"),
        inverseJoinColumns = @JoinColumn(name = "product_bom_id")
    )
    private List<ProductBOM> productBOMs = new ArrayList<>();

	@OneToMany(
		mappedBy      = "manufacturingBOM",   // Feld in ManufacturingOperation
		cascade       = CascadeType.ALL,
		orphanRemoval = true,
		fetch         = FetchType.LAZY
	)
	@OrderBy("sequenceNumber ASC")
	private List<ManufacturingOperation> operations = new ArrayList<>();

    @OneToMany(
        mappedBy      = "manufacturingBOM",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("revision ASC")
    private List<ManufacturingBOMRevision> revisions =
            new ArrayList<>();

    // ── Convenience ───────────────────────────────────────────────

	public ManufacturingBOMRevision getLatestRevision() {
		if (revisions == null || revisions.isEmpty()) return null;
		return revisions.get(revisions.size() - 1);
	}

	public ManufacturingBOMRevision getReleasedRevision() {
		return revisions.stream()
				.filter(r -> r.isReleased())           // ← Lambda
				.max(Comparator.comparing(
						r -> r.getRevision()))          // ← Lambda
				.orElse(null);
	}

	public String getLatestStatusName() {
		ManufacturingBOMRevision latest = getLatestRevision();
		if (latest == null) return "KEINE_REVISION";
		if (latest.getStatus() == null) return "KEINE_REVISION";
		return latest.getStatus().name();
	}
}