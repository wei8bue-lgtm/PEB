package com.peb.model.ebom;

import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Part eBOM object
 * ID format: PT00000001, PT00000002, ...
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "parts")
public class Part extends EBOMObject {

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartRevision> revisions = new ArrayList<>();

    @Override
    public ObjectType getObjectType() {
        return ObjectType.PART;
    }

    @Override
    public List<PartRevision> getRevisions() {
        return revisions;
    }
}