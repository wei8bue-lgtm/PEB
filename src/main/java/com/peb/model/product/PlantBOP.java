// src/main/java/com/peb/model/product/PlantBOP.java
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
@Table(name = "plant_bop")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString(exclude = {"nodes", "revisions"})
public class PlantBOP {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "plant_bop_seq")
    @SequenceGenerator(name           = "plant_bop_seq",
                       sequenceName   = "PLANT_BOP_SEQ",
                       allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "plant_code", length = 50)
    private String plantCode;

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

    @OneToMany(
        mappedBy      = "plantBOP",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("sortOrder ASC")
    private List<PlantBOPNode> nodes = new ArrayList<>();

    @OneToMany(
        mappedBy      = "plantBOP",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("revision ASC")
    private List<PlantBOPRevision> revisions =
            new ArrayList<>();

    // ── Convenience ───────────────────────────────────────────────

	public PlantBOPRevision getLatestRevision() {
		if (revisions == null || revisions.isEmpty()) return null;
		return revisions.get(revisions.size() - 1);
	}

	public PlantBOPRevision getReleasedRevision() {
		return revisions.stream()
				.filter(r -> r.isReleased())           // ← Lambda
				.max(Comparator.comparing(
						r -> r.getRevision()))          // ← Lambda
				.orElse(null);
	}

	public String getLatestStatusName() {
		PlantBOPRevision latest = getLatestRevision();
		if (latest == null) return "KEINE_REVISION";
		if (latest.getStatus() == null) return "KEINE_REVISION";
		return latest.getStatus().name();
	}
}