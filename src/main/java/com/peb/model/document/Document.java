package com.peb.model.document;

import com.peb.model.base.BaseObject;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Document (DC-Objekt)
 * ID-Format: DC00000001, DC00000002, ...
 * Wird aus einem freigegebenen DocumentTemplate erstellt.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "documents")
public class Document extends BaseObject {

    @Column(name = "description", length = 1000)
    @Size(max = 1000)
    private String description;

    /** Wert für Attribute 1 (ersetzt {{Attribute1}} im Template) */
    @Column(name = "attribute1", length = 500)
    @Size(max = 500)
    private String attribute1;

    /** Wert für Attribute 2 (ersetzt {{Attribute2}} im Template) */
    @Column(name = "attribute2", length = 500)
    @Size(max = 500)
    private String attribute2;

    /** Das Template, aus dem dieses Dokument erstellt wurde */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private DocumentTemplate template;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentRevision> revisions = new ArrayList<>();

    public DocumentRevision getLatestRevision() {
        return revisions.stream()
                .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
                .orElse(null);
    }

    public DocumentRevision getReleasedRevision() {
        return revisions.stream()
                .filter(r -> r.getReleaseStatus()
                        == com.peb.util.ReleaseStatus.RELEASED)
                .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
                .orElse(null);
    }
}
