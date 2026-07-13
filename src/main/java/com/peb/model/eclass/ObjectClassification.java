package com.peb.model.eclass;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Verknüpfung zwischen einem PEB-Objekt (Design, Part, Module, …)
 * und einer ECLASS-Klasse.
 *
 * Ein PEB-Objekt wird durch objectId + objectType eindeutig identifiziert.
 * Pro Objekt ist genau eine ECLASS-Klassifizierung erlaubt (UniqueConstraint).
 */
@Data
@Entity
@Table(
    name = "object_classifications",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_obj_classification",
        columnNames = {"object_id", "object_type"}
    ),
    indexes = {
        @Index(name = "idx_obj_class_object",   columnList = "object_id, object_type"),
        @Index(name = "idx_obj_class_class_id", columnList = "class_id")
    }
)
public class ObjectClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "obj_class_seq")
    @SequenceGenerator(name = "obj_class_seq", sequenceName = "OBJ_CLASS_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    /**
     * ID des klassifizierten PEB-Objekts, z.B. "DS00000001" oder "PT00000042"
     */
    @Column(name = "object_id", length = 12, nullable = false)
    private String objectId;

    /**
     * Typ des klassifizierten Objekts (DESIGN, PART, MODULE, …)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", length = 30, nullable = false)
    private ObjectType objectType;

    /**
     * Zugeordnete ECLASS-Klasse
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private EclassClass eclassClass;

    /**
     * Merkmalswerte des Objekts für diese Klasse
     */
    @OneToMany(mappedBy = "classification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ObjectPropertyValue> propertyValues = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;
}
