package com.peb.model.ebom;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * System eBOM object
 * ID format: SY00000001, SY00000002, ...
 * Can contain: Subsystem, Part, Auxiliary
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "systems")
public class System extends EBOMObject {

    @OneToMany(mappedBy = "system", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SystemRevision> revisions = new ArrayList<>();

    @Override
    public ObjectType getObjectType() {
        return ObjectType.SYSTEM;
    }

    @Override
    public List<SystemRevision> getRevisions() {
        return revisions;
    }
}