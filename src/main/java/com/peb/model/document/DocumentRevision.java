package com.peb.model.document;

import com.peb.model.base.BaseRevision;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Document Revision
 * Enthält die vom Nutzer hochgeladene finale .doc-Datei sowie die Attributwerte.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "document_revisions")
public class DocumentRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Wert für Attribute 1 */
    @Column(name = "attribute1", length = 500)
    @Size(max = 500)
    private String attribute1;

    /** Wert für Attribute 2 */
    @Column(name = "attribute2", length = 500)
    @Size(max = 500)
    private String attribute2;

    /** Pfad zur hochgeladenen finalen .doc-Datei (nach lokalem Bearbeiten) */
    @Column(name = "doc_file_path", length = 1000)
    private String docFilePath;

    /** Originaler Dateiname der finalen .doc-Datei */
    @Column(name = "doc_file_name", length = 500)
    private String docFileName;

    /** Referenz auf die verwendete Template-Revision */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_revision_id")
    private DocumentTemplateRevision templateRevision;

    @Override
    public String getObjectId() {
        return document != null ? document.getId() : null;
    }

    public void syncFromParent() {
        if (document != null) {
            this.setName(document.getName());
            this.setDepartment(document.getDepartment());
            this.attribute1 = document.getAttribute1();
            this.attribute2 = document.getAttribute2();
        }
    }
}
