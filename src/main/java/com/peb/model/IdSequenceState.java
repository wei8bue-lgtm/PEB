package com.peb.model;

import com.peb.util.ObjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistenter ID-Zaehler je Objekttyp.
 * Speichert die zuletzt vergebene Sequenznummer (ever-used).
 */
@Entity
@Table(name = "id_sequence_state")
@Getter
@Setter
@NoArgsConstructor
public class IdSequenceState {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 64)
    private ObjectType objectType;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence;

    public IdSequenceState(ObjectType objectType, Long lastSequence) {
        this.objectType = objectType;
        this.lastSequence = lastSequence;
    }
}
