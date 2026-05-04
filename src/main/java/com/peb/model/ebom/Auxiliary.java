package com.peb.model.ebom;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Auxiliary eBOM object
 * ID format: AX00000001, AX00000002, ...
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "auxiliaries")
public class Auxiliary extends EBOMObject {

    @OneToMany(mappedBy = "auxiliary", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuxiliaryRevision> revisions = new ArrayList<>();

    @Override
    public ObjectType getObjectType() {
        return ObjectType.AUXILIARY;
    }

    @Override
    public List<AuxiliaryRevision> getRevisions() {
        return revisions;
    }
}