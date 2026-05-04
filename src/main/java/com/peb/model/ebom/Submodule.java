package com.peb.model.ebom;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Submodule eBOM object
 * ID format: MU00000001, MU00000002, ...
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "submodules")
public class Submodule extends EBOMObject {

    @OneToMany(mappedBy = "submodule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmoduleRevision> revisions = new ArrayList<>();

    @Override
    public ObjectType getObjectType() {
        return ObjectType.SUBMODULE;
    }

    @Override
    public List<SubmoduleRevision> getRevisions() {
        return revisions;
    }
}