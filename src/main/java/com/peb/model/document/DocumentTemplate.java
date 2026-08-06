package com.peb.model.document;

import com.peb.model.base.BaseObject;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Document Template (DT-Objekt)
 * ID-Format: DT00000001, DT00000002, ...
 * Enthält freigegebene .doc-Templates mit konfigurierbaren Attributen.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "document_templates")
public class DocumentTemplate extends BaseObject {

    @Column(name = "description", length = 1000)
    @Size(max = 1000)
    private String description;

    @Column(name = "attribute1", length = 500)
    @Size(max = 500)
    private String attribute1;

    @Column(name = "attribute2", length = 500)
    @Size(max = 500)
    private String attribute2;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentTemplateRevision> revisions = new ArrayList<>();

    public DocumentTemplateRevision getLatestRevision() {
        return revisions.stream()
                .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
                .orElse(null);
    }

    public DocumentTemplateRevision getReleasedRevision() {
        return revisions.stream()
                .filter(r -> r.getReleaseStatus()
                        == com.peb.util.ReleaseStatus.RELEASED)
                .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
                .orElse(null);
    }
}
