package com.peb.model.document;

import com.peb.model.base.BaseRevision;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Document Template Revision
 * Enthält die hochgeladene .doc-Datei mit Platzhaltern {{Attribute1}} und {{Attribute2}}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "document_template_revisions")
public class DocumentTemplateRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private DocumentTemplate template;

    @Column(name = "attribute1", length = 500)
    @Size(max = 500)
    private String attribute1;

    @Column(name = "attribute2", length = 500)
    @Size(max = 500)
    private String attribute2;

    /** Pfad zur hochgeladenen .doc-Template-Datei */
    @Column(name = "doc_file_path", length = 1000)
    private String docFilePath;

    /** Originaler Dateiname der hochgeladenen Template-Datei */
    @Column(name = "doc_file_name", length = 500)
    private String docFileName;

    @Override
    public String getObjectId() {
        return template != null ? template.getId() : null;
    }

    public void syncFromParent() {
        if (template != null) {
            this.setName(template.getName());
            this.setDepartment(template.getDepartment());
            this.attribute1 = template.getAttribute1();
            this.attribute2 = template.getAttribute2();
        }
    }
}
