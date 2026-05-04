package com.peb.model.ebom;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Subsystem eBOM object
 * ID format: SU00000001, SU00000002, ...
 * Can contain: Module, Part, Auxiliary
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "subsystems")
public class Subsystem extends EBOMObject {

    @OneToMany(mappedBy = "subsystem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubsystemRevision> revisions = new ArrayList<>();

    @Override
    public ObjectType getObjectType() {
        return ObjectType.SUBSYSTEM;
    }

    @Override
    public List<SubsystemRevision> getRevisions() {
        return revisions;
    }
}