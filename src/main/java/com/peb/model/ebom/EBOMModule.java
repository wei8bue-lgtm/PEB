package com.peb.model.ebom;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * EBOMModule object (renamed to avoid conflict with java.lang.Module)
 * ID format: MO00000001, MO00000002, ...
 * Can contain: Submodule, Part, Auxiliary
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "modules")
public class EBOMModule extends EBOMObject {

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EBOMModuleRevision> revisions = new ArrayList<>();

    @Override
    public ObjectType getObjectType() {
        return ObjectType.MODULE;
    }

    @Override
    public List<EBOMModuleRevision> getRevisions() {
        return revisions;
    }
}