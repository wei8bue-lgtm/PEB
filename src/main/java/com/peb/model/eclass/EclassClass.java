package com.peb.model.eclass;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ECLASS class / commodity master data.
 * Hierarchie: Segment (1) → Main Group (2) → Group (3) → Commodity (4)
 * Beispiel:   "27"        → "27-37"         → "27-37-04" → "27-37-04-01"
 */
@Data
@Entity
@Table(
    name = "eclass_classes",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_eclass_code_version",
        columnNames = {"code", "version"}
    ),
    indexes = {
        @Index(name = "idx_eclass_code",    columnList = "code"),
        @Index(name = "idx_eclass_segment", columnList = "segment"),
        @Index(name = "idx_eclass_level",   columnList = "class_level"),
        @Index(name = "idx_eclass_name",    columnList = "name")
    }
)
public class EclassClass {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eclass_class_seq")
    @SequenceGenerator(name = "eclass_class_seq", sequenceName = "ECLASS_CLASS_SEQ", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    /**
     * ECLASS coded name, z.B. "27-37-04-01"
     */
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    /**
     * ECLASS Release-Version, z.B. "12.0"
     */
    @Column(name = "version", length = 10)
    private String version;

    /**
     * IRDI (International Registration Data Identifier)
     * z.B. "0173-1#01-BAC001#001"
     */
    @Column(name = "irdi", length = 60)
    private String irdi;

    /**
     * Bevorzugter Name (deutsch oder englisch)
     */
    @Column(name = "name", length = 500)
    private String name;

    /**
     * Englischer Name (optional)
     */
    @Column(name = "name_en", length = 500)
    private String nameEn;

    /**
     * Definition / Beschreibung
     */
    @Column(name = "definition", length = 2000)
    private String definition;

    /**
     * Komma-getrennte Schlüsselwörter für die Suche
     */
    @Column(name = "keywords", length = 2000)
    private String keywords;

    /**
     * Hierarchie-Ebene: 1=Segment, 2=Hauptgruppe, 3=Gruppe, 4=Commodity
     */
    @Column(name = "class_level")
    private Integer classLevel;

    /**
     * Segment-Code (2 Zeichen), z.B. "27"
     */
    @Column(name = "segment", length = 2)
    private String segment;

    /**
     * Hauptgruppen-Code (5 Zeichen), z.B. "27-37"
     */
    @Column(name = "main_group", length = 5)
    private String mainGroup;

    /**
     * Gruppen-Code (8 Zeichen), z.B. "27-37-04"
     */
    @Column(name = "group_code", length = 8)
    private String groupCode;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @OneToMany(mappedBy = "eclassClass", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<EclassProperty> properties = new ArrayList<>();

    /**
     * Leitet Segment, Main Group und Group Code aus dem ECLASS-Code ab.
     * Wird beim Import automatisch aufgerufen.
     */
    public void deriveHierarchyFromCode() {
        if (code == null) return;
        String[] parts = code.split("-");
        if (parts.length >= 1) this.segment   = parts[0];
        if (parts.length >= 2) this.mainGroup  = parts[0] + "-" + parts[1];
        if (parts.length >= 3) this.groupCode  = parts[0] + "-" + parts[1] + "-" + parts[2];
        // Ebene aus trailing-zero-Paaren ableiten (ECLASS: AA-BB-CC-DD)
        if (parts.length == 4) {
            if ("00".equals(parts[1]) && "00".equals(parts[2]) && "00".equals(parts[3]))
                this.classLevel = 1;
            else if ("00".equals(parts[2]) && "00".equals(parts[3]))
                this.classLevel = 2;
            else if ("00".equals(parts[3]))
                this.classLevel = 3;
            else
                this.classLevel = 4;
        } else {
            this.classLevel = parts.length;
        }
    }
}
