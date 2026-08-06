package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.document.Document;
import com.peb.model.document.DocumentRevision;
import com.peb.model.document.DocumentTemplate;
import com.peb.model.document.DocumentTemplateRevision;
import com.peb.repository.StructureRelationRepository;
import com.peb.repository.DocumentRepository;
import com.peb.repository.DocumentRevisionRepository;
import com.peb.service.search.SearchIndexService;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Service für Document Verwaltung.
 * Unterstützt: Erstellen aus Template, Attribut-Substitution in .doc,
 * Datei-Upload, Lifecycle-Aktionen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository         documentRepository;
    private final DocumentRevisionRepository revisionRepository;
    private final DocumentTemplateService    templateService;
    private final IDGeneratorService         idGeneratorService;
	private final StructureRelationRepository structureRelationRepository;
    private final SearchIndexService searchIndexService; // NEU: zentrale Suche

    @Value("${peb.document.storage-dir:data/documents/}")
    private String storageDir;

    // ─────────────────────────────────────────────
    // ERSTELLEN
    // ─────────────────────────────────────────────

    /**
     * Erstellt ein neues Dokument aus einem freigegebenen Template.
     * Die Attributwerte werden in der Revision gespeichert.
     * Das vorausgefüllte .doc wird separat per Download bereitgestellt.
     */
    @Transactional
    public Document createFromTemplate(String name, String department,
                                       String description,
                                       String attribute1, String attribute2,
                                       String templateId) {
        DocumentTemplate template = templateService.getTemplate(templateId);
        DocumentTemplateRevision releasedRevision = template.getReleasedRevision();
        if (releasedRevision == null) {
            throw new WorkflowException(
                    "Template '" + templateId + "' hat keine freigegebene Revision. "
                    + "Nur RELEASED Templates können für Dokumente verwendet werden.");
        }

        Document document = new Document();
        document.setId(idGeneratorService.generateId(ObjectType.DOCUMENT));
        document.setName(name);
        document.setDepartment(department);
        document.setDescription(description);
        document.setAttribute1(attribute1);
        document.setAttribute2(attribute2);
        document.setTemplate(template);
        document = documentRepository.save(document);

        DocumentRevision revision = new DocumentRevision();
        revision.setDocument(document);
        revision.setRevision("01");
        revision.setReleaseStatus(ReleaseStatus.WORKING);
        revision.setTemplateRevision(releasedRevision);
        revision.syncFromParent();
        revisionRepository.save(revision);
        document.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort greift

        log.info("Document erstellt: id={}, aus Template={}", document.getId(), templateId);

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.DOCUMENT, document);

        return document;
    }

    // ─────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────

	@Transactional(readOnly = true, noRollbackFor = com.peb.exception.ObjectNotFoundException.class)
	public Document getDocument(String id) {
		return documentRepository.findById(id)
				.orElseThrow(() -> new ObjectNotFoundException("Document nicht gefunden: " + id));
	}

    @Transactional(readOnly = true)
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<DocumentRevision> getRevisions(String documentId) {
        return revisionRepository.findByDocumentId(documentId);
    }

    @Transactional(readOnly = true)
    public DocumentRevision getRevision(Long revisionId) {
        return revisionRepository.findById(revisionId)
                .orElseThrow(() -> new ObjectNotFoundException("Document-Revision nicht gefunden: " + revisionId));
    }

    // ─────────────────────────────────────────────
    // UPDATE / DELETE
    // ─────────────────────────────────────────────

	@Transactional
	public Document updateDocument(String id, String name, String department,
								   String description,
								   String attribute1, String attribute2) {
		Document document = getDocument(id);
		DocumentRevision latest = document.getLatestRevision();
		boolean attributesLocked = latest != null && latest.getDocFilePath() != null;

		document.setName(name);
		document.setDepartment(department);
		document.setDescription(description);

		if (attributesLocked) {
			if (!java.util.Objects.equals(document.getAttribute1(), attribute1)
					|| !java.util.Objects.equals(document.getAttribute2(), attribute2)) {
				log.warn("Document[{}]: Änderung an Attributen verworfen – "
						 + "Datei existiert bereits für aktuelle Revision.", id);
			}
			// Attribute NICHT übernehmen – bleiben unverändert
		} else {
			document.setAttribute1(attribute1);
			document.setAttribute2(attribute2);
		}

		DocumentRevision latestForSync = document.getLatestRevision();
		if (latestForSync != null && latestForSync.getReleaseStatus() == ReleaseStatus.WORKING) {
			latestForSync.syncFromParent();
			revisionRepository.save(latestForSync);
		}

		Document saved = documentRepository.save(document);

		// NEU: zentrale Suche aktualisieren
		searchIndexService.index(ObjectType.DOCUMENT, saved);

		return saved;
	}
	
	@Transactional
	public DocumentRevision deleteDocumentFile(Long revisionId) {
		DocumentRevision revision = getRevision(revisionId);

		if (revision.getReleaseStatus() != ReleaseStatus.WORKING) {
			throw new WorkflowException(
					"Nur WORKING-Revisionen erlauben das Löschen der Datei.");
		}
		if (revision.getDocFilePath() == null) {
			throw new WorkflowException(
					"Für diese Revision existiert keine Datei.");
		}

		deleteFile(revision.getDocFilePath());   // vorhandene Helper-Methode
		revision.setDocFilePath(null);
		revision.setDocFileName(null);

		DocumentRevision saved = revisionRepository.save(revision);
		log.info("Datei für Document-Revision {} gelöscht – "
				 + "Revision und Attribute bleiben erhalten.", revisionId);
		return saved;
	}

	@Transactional
	public void deleteDocument(String id) {
		Document document = getDocument(id);

		// Sicherheitsprüfung: keine freigegebene Revision vorhanden?
		boolean hasReleased = document.getRevisions().stream()
				.anyMatch(r -> r.getReleaseStatus() == ReleaseStatus.RELEASED);
		if (hasReleased) {
			throw new WorkflowException(
					"Dokument '" + id + "' kann nicht gelöscht werden, "
					+ "da eine freigegebene Revision existiert.");
		}

		// ── NEU: verwaiste Structure-Links entfernen ──────────────────
		// Verhindert, dass die Structure-View nach dem Löschen auf ein
		// nicht mehr existierendes Dokument zeigt.
		int removedLinks = structureRelationRepository.findByChildId(id).size();
		structureRelationRepository.deleteByChildId(id);
		if (removedLinks > 0) {
			log.info("Document[{}]: {} Structure-Link(s) entfernt vor dem Löschen", id, removedLinks);
		}

		// Dateien vom Dateisystem löschen
		document.getRevisions().forEach(rev -> deleteFile(rev.getDocFilePath()));

		documentRepository.delete(document);
		log.info("Document gelöscht: id={}", id);

		// NEU: aus zentraler Suche entfernen
		searchIndexService.remove(ObjectType.DOCUMENT, id);
	}

    // ─────────────────────────────────────────────
    // DATEI-UPLOAD (finale .doc-Datei)
    // ─────────────────────────────────────────────

    @Transactional
    public DocumentRevision uploadFinalDocument(Long revisionId, MultipartFile docFile) throws IOException {
        validateDocFile(docFile);

        DocumentRevision revision = getRevision(revisionId);
        if (revision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Nur WORKING-Revisionen können aktualisiert werden.");
        }

        String filePath = storeFile(docFile);
        revision.setDocFilePath(filePath);
        revision.setDocFileName(docFile.getOriginalFilename());
        return revisionRepository.save(revision);
    }

    // ─────────────────────────────────────────────
    // TEMPLATE VORAUSFÜLLEN (Apache POI HWPF)
    // ─────────────────────────────────────────────

    /**
     * Liest die .docx-Datei der Template-Revision, ersetzt die Platzhalter
     * {{Attribute1}} und {{Attribute2}} durch die Werte der Dokument-Revision
     * und gibt die befüllte .docx-Datei als Byte-Array zurück.
     */
@Transactional(readOnly = true)
public byte[] generatePrefilledDocument(Long documentRevisionId) throws IOException {
    log.info("---- START generatePrefilledDocument für Revision ID: {} ----", documentRevisionId);

    DocumentRevision docRevision = getRevision(documentRevisionId);
    DocumentTemplateRevision tmplRevision = docRevision.getTemplateRevision();

    if (tmplRevision == null) {
        log.error("FEHLER: Die Dokument-Revision {} ist mit keiner Template-Revision verknüpft!", documentRevisionId);
        throw new ObjectNotFoundException("Kein Template für Revision gefunden: " + documentRevisionId);
    }
    log.info("Template-Revision gefunden: ID {}", tmplRevision.getRevisionId());

    if (tmplRevision.getDocFilePath() == null || tmplRevision.getDocFilePath().isBlank()) {
        log.error("FEHLER: Template-Revision {} hat keinen Dateipfad (docFilePath is null or blank).", tmplRevision.getRevisionId());
        throw new ObjectNotFoundException("Template-Revision hat keine zugeordnete Datei.");
    }
    log.info("Template-Dateipfad: '{}'", tmplRevision.getDocFilePath());

    Path templatePath = Paths.get(tmplRevision.getDocFilePath());
    if (!Files.exists(templatePath)) {
        log.error("FEHLER: Die Template-Datei existiert nicht unter dem Pfad: {}", templatePath);
        throw new IOException("Template-Datei nicht gefunden unter: " + templatePath);
    }
    log.info("Template-Datei existiert. Lese Datei...");

    String attribute1 = nvl(docRevision.getAttribute1());
    String attribute2 = nvl(docRevision.getAttribute2());
    log.info("Attributwerte für Substitution: Attribute1='{}', Attribute2='{}'", attribute1, attribute2);

    try (FileInputStream fis = new FileInputStream(templatePath.toFile());
         XWPFDocument xwpf = new XWPFDocument(fis);
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

        log.info("DOCX-Template erfolgreich geladen. Starte Substitution in Absätzen...");
        substituteInParagraphs(xwpf.getParagraphs(), attribute1, attribute2);

        log.info("Substitution in Absätzen fertig. Starte Substitution in Tabellen...");
        for (XWPFTable table : xwpf.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    substituteInParagraphs(cell.getParagraphs(), attribute1, attribute2);
                }
            }
        }
        log.info("Substitution in Tabellen fertig.");

        xwpf.write(out);
        log.info("---- ENDE generatePrefilledDocument: Prozess erfolgreich. ----");
        return out.toByteArray();
    } catch (Exception e) {
        log.error("---- SCHWERWIEGENDER FEHLER während der Dokument-Generierung ----", e);
        throw e; // wirft die Exception weiter, damit der User eine Fehlermeldung sieht
    }
}

// Fügen Sie diese Methode in Ihrer DocumentService.java hinzu

	@Transactional
	public void generateAndSavePrefilledDocument(Long documentRevisionId) throws IOException {
		log.info("---- START generateAndSavePrefilledDocument für Revision ID: {} ----", documentRevisionId);

		// 1. Generiert das Dokument als Byte-Array im Speicher (Ihre bestehende Logik)
		byte[] documentBytes = generatePrefilledDocument(documentRevisionId);
		
		DocumentRevision docRevision = getRevision(documentRevisionId);
		
		// 2. Definiert einen eindeutigen Dateinamen und Speicherpfad auf dem Server
		String originalFileName = "document_" + docRevision.getDocument().getId() + "_rev" + docRevision.getRevision() + ".docx";
		String uniqueName = UUID.randomUUID().toString() + "_" + originalFileName;
		Path targetPath = Paths.get(storageDir, uniqueName);
		
		// Stellt sicher, dass das Speicherverzeichnis existiert
		Files.createDirectories(targetPath.getParent());
		
		// 3. Schreibt das generierte Dokument auf die Festplatte
		Files.write(targetPath, documentBytes);
		log.info("Dokument erfolgreich generiert und unter {} gespeichert.", targetPath.toString());
		
		// 4. Aktualisiert den Datenbankeintrag mit dem neuen Dateipfad
		docRevision.setDocFilePath(targetPath.toString());
		docRevision.setDocFileName(originalFileName); // Der saubere Name für die Anzeige
		revisionRepository.save(docRevision);
		
		log.info("Datenbank für Revision {} aktualisiert. Dateipfad: {}", documentRevisionId, targetPath.toString());
	}



    /**
     * Robuste Platzhalter-Substitution:
     * Word zerlegt Text intern in Runs, die Platzhalter können über mehrere
     * Runs verteilt sein (z.B. "{{Attr" | "ibute1}}"). Daher werden alle
     * Runs eines Absatzes zu einem String zusammengeführt, die Platzhalter
     * ersetzt und das Ergebnis in den ersten Run zurückgeschrieben.
     */
/*     private void substituteInParagraphs(java.util.List<XWPFParagraph> paragraphs,
                                         String attribute1, String attribute2) {
        for (XWPFParagraph para : paragraphs) {
            java.util.List<XWPFRun> runs = para.getRuns();
            if (runs == null || runs.isEmpty()) continue;

            // Alle Run-Texte zusammenführen
            StringBuilder sb = new StringBuilder();
            for (XWPFRun run : runs) {
                String t = run.getText(0);
                sb.append(t != null ? t : "");
            }

            String combined = sb.toString();
            if (!combined.contains("{{Attribute1}}") && !combined.contains("{{Attribute2}}")) {
                continue; // kein Platzhalter in diesem Absatz → unverändert lassen
            }

            // Platzhalter ersetzen
            combined = combined.replace("{{Attribute1}}", attribute1)
                               .replace("{{Attribute2}}", attribute2);

            // Ergebnis in ersten Run, alle anderen leeren
            runs.get(0).setText(combined, 0);
            for (int i = 1; i < runs.size(); i++) {
                runs.get(i).setText("", 0);
            }
        }
    } */
	
private void substituteInParagraphs(java.util.List<XWPFParagraph> paragraphs,
                                     String attribute1, String attribute2) {
    if (paragraphs == null) {
        log.debug("Paragraph list is null. Skipping substitution.");
        return;
    }

    log.info("Starting substitution. Values: Attribute1='{}', Attribute2='{}'", attribute1, attribute2);
    log.info("Total paragraphs to process: {}", paragraphs.size());

    for (int i = 0; i < paragraphs.size(); i++) {
        XWPFParagraph para = paragraphs.get(i);
        String text = para.getText();

        if (text == null || text.trim().isEmpty()) {
            continue; // Leere Absätze überspringen
        }

        // --- DEBUGGING-AUSGABE ---
        // Gib den exakten Text aus, den POI aus dem Absatz liest.
        log.info("Paragraph #{}: Raw text read by POI: '{}'", i, text);

        // Prüfen, ob der Platzhalter im gelesenen Text vorkommt.
        boolean containsAttr1 = text.contains("{{Attribute1}}");
        boolean containsAttr2 = text.contains("{{Attribute2}}");

        if (!containsAttr1 && !containsAttr2) {
            // Logge, dass hier nichts zu tun ist, und fahre fort.
            log.debug("Paragraph #{}: No placeholders found. Skipping.", i);
            continue;
        }

        log.info("Paragraph #{}: Placeholder found! Processing...", i);

        // Platzhalter ersetzen
        String updatedText = text.replace("{{Attribute1}}", attribute1 != null ? attribute1 : "")
                                 .replace("{{Attribute2}}", attribute2 != null ? attribute2 : "");

        log.info("Paragraph #{}: Text after replacement: '{}'", i, updatedText);

        // Alle alten Runs entfernen
        while (!para.getRuns().isEmpty()) {
            para.removeRun(0);
        }

        // Neuen, sauberen Run erstellen
        XWPFRun newRun = para.createRun();
        newRun.setText(updatedText);

        log.info("Paragraph #{}: Substitution complete for this paragraph.", i);
    }
}






    // ─────────────────────────────────────────────
    // DATEI-DOWNLOAD (finale .doc)
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Resource loadDocFile(Long revisionId) {
        DocumentRevision revision = getRevision(revisionId);
        if (revision.getDocFilePath() == null) {
            throw new ObjectNotFoundException("Keine Datei für Dokument-Revision: " + revisionId);
        }
        try {
            Path path = Paths.get(revision.getDocFilePath());
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ObjectNotFoundException("Dokument-Datei nicht lesbar: " + revision.getDocFilePath());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ObjectNotFoundException("Ungültiger Dateipfad: " + revision.getDocFilePath());
        }
    }

    // ─────────────────────────────────────────────
    // HILFSMETHODEN
    // ─────────────────────────────────────────────

    private String storeFile(MultipartFile file) throws IOException {
        String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(storageDir, uniqueName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, file.getBytes());
        return filePath.toString();
    }

    private void deleteFile(String filePath) {
        if (filePath == null) return;
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("Datei konnte nicht gelöscht werden: {}", filePath);
        }
    }

    private void validateDocFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new WorkflowException("Keine Datei hochgeladen.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".docx")) {
            throw new WorkflowException("Nur .docx-Dateien sind erlaubt.");
        }
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
