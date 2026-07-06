// src/main/java/com/peb/model/product/ProductBOP.java
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
@Table(name = "product_bop")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString(exclude = {"manufacturingBOMs", "revisions"})
public class ProductBOP {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "product_bop_seq")
    @SequenceGenerator(name           = "product_bop_seq",
                       sequenceName   = "PRODUCT_BOP_SEQ",
                       allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "bop_version", length = 10)
    private String bopVersion = "01";

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
        name               = "product_bop_mfg_bom",
        joinColumns        = @JoinColumn(name = "product_bop_id"),
        inverseJoinColumns = @JoinColumn(name = "mfg_bom_id")
    )
    private List<ManufacturingBOM> manufacturingBOMs =
            new ArrayList<>();

    @OneToMany(
        mappedBy      = "productBOP",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("revision ASC")
    private List<ProductBOPRevision> revisions =
            new ArrayList<>();

    // ── Convenience ───────────────────────────────────────────────

/* 	public ProductBOPRevision getLatestRevision() {
		if (revisions == null || revisions.isEmpty()) return null;
		return revisions.get(revisions.size() - 1);
	} */

	public ProductBOPRevision getReleasedRevision() {
		return revisions.stream()
				.filter(r -> r.isReleased())           // ← Lambda
				.max(Comparator.comparing(
						r -> r.getRevision()))          // ← Lambda
				.orElse(null);
	}

	// ── Convenience-Methoden ──────────────────────────────────────

	public ProductBOPRevision getLatestRevision() {
		if (revisions == null || revisions.isEmpty()) return null;
		return revisions.get(revisions.size() - 1);
	}

	public ManufacturingStatus getLatestStatus() {
		ProductBOPRevision latest = getLatestRevision();
		return latest != null ? latest.getStatus() : null;
	}

	/**
	 * Null-sicherer Status-Name für Thymeleaf – niemals null.
	 */
	public String getLatestStatusName() {
		ManufacturingStatus status = getLatestStatus();
		return status != null ? status.name() : "KEINE_REVISION";
	}
}