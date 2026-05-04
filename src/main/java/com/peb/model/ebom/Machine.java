package com.peb.model.ebom;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Machine eBOM object
 * ID format: MA00000001, MA00000002, ...
 * Can contain: System, Part, Auxiliary
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "machines")
public class Machine extends EBOMObject {

    @OneToMany(mappedBy = "machine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MachineRevision> revisions = new ArrayList<>();

    @Override
    public ObjectType getObjectType() {
        return ObjectType.MACHINE;
    }

    @Override
    public List<MachineRevision> getRevisions() {
        return revisions;
    }
}