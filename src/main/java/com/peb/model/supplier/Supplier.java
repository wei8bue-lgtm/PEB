package com.peb.model.supplier;

import com.peb.util.ReleaseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lieferanten-Stammdaten (Basisdaten). Analog zu com.peb.model.material.Material
 * aufgebaut: eigene ID (Präfix "MF"), Status und eine Historie von Revisionen
 * mit Freigabe-Workflow (siehe SupplierRevision).
 *
 * Bewusst unabhängig von com.peb.model.material.MaterialSupplier (AVL-Verknüpfung
 * Material↔Lieferant mit Preis/Lieferzeit) gehalten – dort bleibt der Lieferant
 * vorerst als Freitext-Name gepflegt.
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier {

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false)
    private String name;

    private String street;
    private String zipCode;
    private String city;
    private String country;

    private String contactPerson;
    private String contactEmail;
    private String contactPhone;
    private String website;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    private String createdBy;

    @Column(updatable = false)
    private LocalDateTime createdDate;

    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;

    @ToString.Exclude
    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupplierRevision> revisions;

    @PrePersist
    protected void onCreate() {
        this.createdDate = LocalDateTime.now();
        this.lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedDate = LocalDateTime.now();
    }

    /**
     * Bezeichnung der aktuell freigegebenen Revision (Status RELEASED),
     * analog zu Material#getReleasedRevision(). Liefert null, wenn (noch)
     * keine Revision freigegeben ist.
     */
    public String getReleasedRevision() {
        if (revisions == null) return null;
        return revisions.stream()
                .filter(r -> r.getReleaseStatus() == ReleaseStatus.RELEASED)
                .map(SupplierRevision::getRevision)
                .findFirst()
                .orElse(null);
    }
}
