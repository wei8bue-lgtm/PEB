package com.peb.service;

import com.peb.model.eclass.EclassClass;
import com.peb.model.eclass.EclassProperty;
import com.peb.repository.EclassClassRepository;
import com.peb.repository.EclassPropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Import-Service fÃ¼r offizielle ECLASS XML-Release-Dateien.
 *
 * UnterstÃ¼tzte Formate:
 *   - ECLASS Advanced / Basic XML (Version 10.x, 11.x, 12.x) â€“ Einzeldatei
 *   - ZIP-Archiv mit mehreren XML-Dateien (Klassen + Merkmale + Templates)
 *
 * Parsing-Strategie (StAX â€“ speichereffizient fÃ¼r 100 MB+ Dateien):
 *   1. Alle Dateien einlesen â†’ globale Maps aufbauen
 *   2. PropHolder-Map (irdi â†’ Merkmaldefinition)  [nur im Speicher]
 *   3. ClassHolder-List (Klassendaten + IRDI-Referenzen) [nur im Speicher]
 *   4. Template-Map (Klassen-IRDI â†’ Merkmal-IRDIs) aus Application-Template-Dateien
 *   5. Persistierung: Klasse speichern + Merkmale als Cascade (kein separates Speichern der Properties!)
 *
 * KRITISCH: EclassProperty wird NIE ohne EclassClass gespeichert.
 *            Properties werden ausschlieÃŸlich via Cascade der Klasse gespeichert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EclassImportService {

    private static final int BATCH_SIZE = 100;

    private final EclassClassRepository    eclassClassRepository;
    private final EclassPropertyRepository eclassPropertyRepository;

    // â”€â”€ Ã–ffentliche DTOs â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public record ImportResult(
            int classesImported,
            int classesUpdated,
            int propertiesTotal,
            String version,
            String errorMessage,
            List<String> warnings
    ) {
        public boolean isSuccess() { return errorMessage == null; }
    }

    /** Vorschau-Ergebnis ohne DB-Zugriff */
    public record ParsePreview(
            int classesFound,
            int propertiesFound,
            int templatesFound,
            List<String> sampleClasses,
            List<String> sampleProperties,
            List<String> detectedFiles,
            String detectedVersion,
            String rawElementSample
    ) {}

    // â”€â”€ Haupt-Einstiegspunkt â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Importiert aus Einzeldatei (XML) oder ZIP-Archiv.
     *
     * @param inputStream Datei-InputStream
     * @param filename    Originaler Dateiname (fÃ¼r ZIP-Erkennung)
     * @param forceVersion Optionaler Versionsoverride (z.B. "12.0")
     */
    @Transactional
    public ImportResult importFile(InputStream inputStream, String filename, String forceVersion) {
        try {
            byte[] rawBytes = inputStream.readAllBytes();

            if (filename != null && filename.toLowerCase().endsWith(".zip")) {
                return importZip(rawBytes, forceVersion);
            } else {
                return importXmlBytes(rawBytes, forceVersion, filename);
            }
        } catch (Exception e) {
            log.error("ECLASS-Import Fehler", e);
            return new ImportResult(0, 0, 0, null,
                    "Import fehlgeschlagen: " + e.getMessage(), List.of());
        }
    }

    /**
     * Parst die Datei OHNE zu speichern â€“ zeigt was gefunden wurde.
     */
    public ParsePreview previewFile(byte[] rawBytes, String filename) {
        try {
            if (filename != null && filename.toLowerCase().endsWith(".zip")) {
                return previewZip(rawBytes);
            }
            return previewXml(rawBytes, filename);
        } catch (Exception e) {
            log.warn("Preview-Fehler: {}", e.getMessage());
            return new ParsePreview(0, 0, 0, List.of(), List.of(),
                    List.of(filename != null ? filename : "?"),
                    "error", "Fehler: " + e.getMessage());
        }
    }

    // â”€â”€ ZIP-Import â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private ImportResult importZip(byte[] zipBytes, String forceVersion) throws Exception {
        Map<String, PropHolder>     globalProps     = new LinkedHashMap<>();
        List<ClassHolder>           globalClasses   = new ArrayList<>();
        Map<String, List<String>>   globalTemplates = new LinkedHashMap<>();
        List<String>                warnings        = new ArrayList<>();
        String                      detectedVersion = forceVersion;

        List<String> fileNames = new ArrayList<>();

        // ZipInputStream kann keine ZIPs mit Data-Descriptors lesen (size=0 im Local-Header).
        // Deshalb: Temp-Datei schreiben, dann ZipFile nutzen (liest den Central-Directory).
        Path tempZip = Files.createTempFile("eclass_import_", ".zip");
        try {
            Files.write(tempZip, zipBytes);
            try (ZipFile zf = new ZipFile(tempZip.toFile())) {
                java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.toLowerCase().endsWith(".xml")) {
                        continue;
                    }
                    // Mapping- und UnitsML-Dateien sind meist 100+ MB und werden nicht importiert
                    String lowerName = name.toLowerCase();
                    if (lowerName.contains("mapping") || lowerName.contains("unitsml")
                            || lowerName.contains("units_ml")) {
                        log.info("ZIP-Entry uebersprungen (kein Dictionary): {}", name);
                        continue;
                    }

                    byte[] xmlBytes;
                    try (InputStream is = zf.getInputStream(entry)) {
                        xmlBytes = is.readAllBytes();
                    }
                    fileNames.add(name);
                    log.info("ZIP-Entry: {} ({} KB)", name, xmlBytes.length / 1024);

                    // Inhaltstyp erkennen und passend einlesen
                    ContentType type = detectContentType(xmlBytes);
                    log.info("  -> erkannter Typ: {}", type);

                    if (type == ContentType.CLASSES || type == ContentType.MIXED) {
                        globalClasses.addAll(scanClasses(xmlBytes));
                    }
                    if (type == ContentType.PROPERTIES || type == ContentType.MIXED) {
                        globalProps.putAll(scanProperties(xmlBytes));
                    }
                    if (type == ContentType.TEMPLATES || type == ContentType.MIXED) {
                        mergeTemplates(globalTemplates, scanApplicationTemplates(xmlBytes));
                    }
                    if (detectedVersion == null || detectedVersion.isBlank()) {
                        String v = detectVersion(xmlBytes);
                        if (!"unknown".equals(v)) detectedVersion = v;
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempZip);
        }

        log.info("ZIP-Import: {} Dateien, {} Klassen, {} Merkmale, {} Templates",
                fileNames.size(), globalClasses.size(), globalProps.size(), globalTemplates.size());

        if (globalClasses.isEmpty()) {
            return new ImportResult(0, 0, 0, detectedVersion,
                    "Keine Klassen gefunden. Enthaltene Dateien: " + fileNames, warnings);
        }

        // Template-Zuweisungen in ClassHolder Ã¼bertragen
        applyTemplatesToClasses(globalClasses, globalTemplates);

        return persistAll(globalClasses, globalProps,
                detectedVersion != null ? detectedVersion : "unknown", warnings);
    }

    // â”€â”€ Single-XML-Import â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private ImportResult importXmlBytes(byte[] xml, String forceVersion, String filename) throws Exception {
        String version = (forceVersion != null && !forceVersion.isBlank())
                ? forceVersion : detectVersion(xml);

        log.info("ECLASS-Import [{}]: Version={}", filename, version);

        // Parallel-Scanning: Merkmale + Klassen + Templates
        Map<String, PropHolder>   propMap   = scanProperties(xml);
        List<ClassHolder>          classes   = scanClasses(xml);
        Map<String, List<String>> templates = scanApplicationTemplates(xml);

        log.info("  Merkmale gefunden: {}", propMap.size());
        log.info("  Klassen gefunden:  {}", classes.size());
        log.info("  Templates gefunden:{}", templates.size());

        if (!propMap.isEmpty()) {
            log.info("  Beispiel-Merkmal: {}", propMap.values().iterator().next().irdi);
        }
        if (!classes.isEmpty()) {
            ClassHolder sample = classes.get(0);
            log.info("  Beispiel-Klasse: {} â€“ {}", sample.code, sample.name);
        }

        applyTemplatesToClasses(classes, templates);

        if (classes.isEmpty()) {
            return new ImportResult(0, 0, propMap.size(), version,
                    "Keine Klassen in der XML-Datei gefunden. Bitte Datei-Format prÃ¼fen.", List.of());
        }

        return persistAll(classes, propMap, version, new ArrayList<>());
    }

    // â”€â”€ Persistierung (KERN-LOGIK) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Speichert Klassen und ihre Merkmale.
     *
     * WICHTIG: EclassProperty wird IMMER mit gesetztem eclassClass gespeichert
     * (niemals als eigenstÃ¤ndige EntitÃ¤t). Die Speicherung erfolgt via Cascade.
     */
    private ImportResult persistAll(List<ClassHolder> classes,
                                     Map<String, PropHolder> propMap,
                                     String version,
                                     List<String> warnings) {
        int created = 0, updated = 0, totalProps = 0;
        List<EclassClass> batch = new ArrayList<>();

        // Einmalig alle existierenden Klassen dieser Version laden (verhindert N+1 SELECTs)
        Map<String, EclassClass> existingByCode = eclassClassRepository
                .findAllByVersion(version)
                .stream()
                .collect(java.util.stream.Collectors.toMap(EclassClass::getCode, c -> c));
        log.info("persistAll: {} Klassen zu importieren, {} bereits in DB (Version {})",
                classes.size(), existingByCode.size(), version);

        for (ClassHolder ch : classes) {
            if (ch.code == null || ch.code.isBlank()) continue;

            boolean isNew;
            EclassClass cls = existingByCode.get(ch.code);

            if (cls == null) {
                cls = new EclassClass();
                isNew = true;
            } else {
                isNew = false;
            }

            // Basisfelder setzen
            cls.setCode(ch.code);
            cls.setVersion(version);
            cls.setIrdi(ch.irdi);
            cls.setName(ch.name != null ? ch.name : ch.nameEn);
            cls.setNameEn(ch.nameEn);
            cls.setDefinition(ch.definition);
            cls.setKeywords(ch.keywords);
            cls.setClassLevel(ch.classLevel);
            cls.deriveHierarchyFromCode();

            // â”€â”€ Merkmale neu aufbauen (Cascade) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // Alte Properties entfernen (orphanRemoval = true â†’ DB-Delete beim Save)
            cls.getProperties().clear();

            int propOrder = 0;
            for (String irdi : ch.propertyIrdis) {
                PropHolder ph = propMap.get(irdi);
                if (ph == null) continue;

                EclassProperty prop = new EclassProperty();
                prop.setEclassClass(cls);          // â† IMMER setzen vor dem Save!
                prop.setIrdi(ph.irdi);
                prop.setName(ph.name != null ? ph.name : ph.nameEn);
                prop.setNameEn(ph.nameEn);
                prop.setUnit(ph.unit);
                prop.setDataType(ph.dataType);
                prop.setSortOrder(propOrder++);
                cls.getProperties().add(prop);
            }
            totalProps += cls.getProperties().size();

            if (isNew) created++; else updated++;
            batch.add(cls);

            if (batch.size() >= BATCH_SIZE) {
                eclassClassRepository.saveAll(batch);  // Cascade speichert Properties mit
                batch.clear();
                log.debug("ECLASS-Import: {} Klassen gespeichert (bisher: {} neu, {} akt.)",
                        BATCH_SIZE, created, updated);
            }
        }

        if (!batch.isEmpty()) {
            eclassClassRepository.saveAll(batch);
        }

        log.info("ECLASS-Import abgeschlossen: {} neu, {} aktualisiert, {} Merkmale gesamt",
                created, updated, totalProps);

        return new ImportResult(created, updated, totalProps, version, null, warnings);
    }

    // â”€â”€ Parser: Merkmale (Properties) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Map<String, PropHolder> scanProperties(byte[] xml) throws XMLStreamException {
        Map<String, PropHolder> map = new LinkedHashMap<>();
        XMLStreamReader reader = createReader(xml);

        // ECLASS XML 5.0: Properties stehen in <contained_properties><ontoml:property id="...">
        boolean    inContainedProperties = false;
        boolean    inProperty            = false;
        boolean    inPreferredName       = false;
        boolean    inDefinition          = false;
        boolean    inDomain              = false;
        PropHolder current               = null;
        String     pendingLang           = null;
        StringBuilder textBuf            = new StringBuilder();

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ln = reader.getLocalName();
                    textBuf.setLength(0);

                    switch (ln) {
                        case "contained_properties" -> inContainedProperties = true;

                        case "property" -> {
                            // Nur Property-Definitionen (id-Attribut), keine property_ref-Verweise
                            if (inContainedProperties && !inProperty) {
                                String id = reader.getAttributeValue(null, "id");
                                if (id != null && !id.isBlank()) {
                                    current      = new PropHolder();
                                    current.irdi = id.trim();
                                    inProperty   = true;
                                }
                            }
                        }

                        case "preferred_name" -> { if (inProperty) inPreferredName = true; }
                        case "definition"     -> { if (inProperty) inDefinition     = true; }

                        case "domain" -> {
                            if (inProperty) {
                                inDomain = true;
                                String xsiType = reader.getAttributeValue(
                                        "http://www.w3.org/2001/XMLSchema-instance", "type");
                                if (xsiType == null) {
                                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                                        if ("type".equals(reader.getAttributeLocalName(i))) {
                                            xsiType = reader.getAttributeValue(i);
                                            break;
                                        }
                                    }
                                }
                                if (xsiType != null && current != null) {
                                    String dt = xsiType;
                                    int col = dt.indexOf(':');
                                    if (col >= 0) dt = dt.substring(col + 1);
                                    if (dt.endsWith("_Type")) dt = dt.substring(0, dt.length() - 5);
                                    current.dataType = dt;
                                }
                            }
                        }

                        case "unit" -> {
                            if (inProperty && inDomain && current != null) {
                                String unitRef = reader.getAttributeValue(null, "unit_ref");
                                if (unitRef != null) {
                                    current.unitIrdi = unitRef.trim();
                                    current.unit     = unitRef.trim();
                                }
                            }
                        }

                        case "label" -> {
                            if (inProperty && inPreferredName)
                                pendingLang = reader.getAttributeValue(null, "language_code");
                        }

                        case "text" -> {
                            if (inProperty && inDefinition)
                                pendingLang = reader.getAttributeValue(null, "language_code");
                        }
                    }

                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    textBuf.append(reader.getText());

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String ln   = reader.getLocalName();
                    String text = textBuf.toString().trim();

                    switch (ln) {
                        case "contained_properties" -> inContainedProperties = false;

                        case "property" -> {
                            if (inProperty) {
                                if (current != null && current.irdi != null)
                                    map.put(current.irdi, current);
                                inProperty = false; inPreferredName = false;
                                inDefinition = false; inDomain = false;
                                current = null; pendingLang = null;
                            }
                        }

                        case "preferred_name" -> inPreferredName = false;
                        case "definition"     -> inDefinition     = false;
                        case "domain"         -> inDomain         = false;

                        case "label" -> {
                            if (inProperty && inPreferredName && current != null && !text.isEmpty()) {
                                boolean isEn = pendingLang != null && pendingLang.startsWith("en");
                                if (isEn) { current.nameEn = text; current.name = text; }
                                else if (current.name == null) current.name = text;
                            }
                            pendingLang = null;
                        }

                        case "text" -> {
                            if (inProperty && inDefinition && current != null && !text.isEmpty()) {
                                boolean isEn = pendingLang != null && pendingLang.startsWith("en");
                                if ((isEn || current.definition == null) && !text.startsWith("-no def"))
                                    current.definition = text;
                            }
                            pendingLang = null;
                        }
                    }
                    textBuf.setLength(0);
                }
            }
        } finally {
            reader.close();
        }
        return map;
    }

    // ── Parser: Klassen (ECLASS XML 5.0) ──────────────────────────────────

    private List<ClassHolder> scanClasses(byte[] xml) throws XMLStreamException {
        List<ClassHolder> classes = new ArrayList<>();
        XMLStreamReader   reader  = createReader(xml);

        // ECLASS XML 5.0: Klassen stehen in <contained_classes><ontoml:class id="...">
        boolean     inContainedClasses = false;
        boolean     inClass            = false;
        boolean     inPreferredName    = false;
        boolean     inDefinition       = false;
        boolean     inDescribedBy      = false;
        ClassHolder current            = null;
        String      pendingLang        = null;
        StringBuilder textBuf          = new StringBuilder();

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ln = reader.getLocalName();
                    textBuf.setLength(0);

                    switch (ln) {
                        case "contained_classes" -> inContainedClasses = true;

                        case "class" -> {
                            if (inContainedClasses && !inClass) {
                                String id = reader.getAttributeValue(null, "id");
                                if (id != null && !id.isBlank()) {
                                    current      = new ClassHolder();
                                    current.irdi = id.trim();
                                    inClass      = true;
                                }
                            }
                        }

                        case "preferred_name" -> { if (inClass && !inDescribedBy) inPreferredName = true; }
                        case "definition"     -> { if (inClass && !inDescribedBy) inDefinition     = true; }
                        case "described_by"   -> { if (inClass) inDescribedBy = true; }

                        case "property" -> {
                            // Property-Referenzen aus described_by (nicht deprecated)
                            if (inClass && inDescribedBy && current != null) {
                                String propRef    = reader.getAttributeValue(null, "property_ref");
                                String deprecated = reader.getAttributeValue(null, "is_deprecated");
                                if (propRef != null && !"true".equals(deprecated))
                                    current.propertyIrdis.add(propRef.trim());
                            }
                        }

                        case "label" -> {
                            if (inClass && inPreferredName && !inDescribedBy)
                                pendingLang = reader.getAttributeValue(null, "language_code");
                        }

                        case "text" -> {
                            if (inClass && inDefinition)
                                pendingLang = reader.getAttributeValue(null, "language_code");
                        }
                    }

                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    textBuf.append(reader.getText());

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String ln   = reader.getLocalName();
                    String text = textBuf.toString().trim();

                    switch (ln) {
                        case "contained_classes" -> inContainedClasses = false;

                        case "class" -> {
                            if (inClass) {
                                if (current != null && current.code != null && !current.code.isBlank())
                                    classes.add(current);
                                inClass = false; inPreferredName = false;
                                inDefinition = false; inDescribedBy = false;
                                current = null; pendingLang = null;
                            }
                        }

                        case "preferred_name" -> inPreferredName = false;
                        case "definition"     -> inDefinition     = false;
                        case "described_by"   -> inDescribedBy    = false;

                        case "hierarchical_position" -> {
                            // 8-stelliger Code AABBCCDD -> "AA-BB-CC-DD"
                            if (inClass && !inDescribedBy && current != null && text.length() == 8) {
                                String aa = text.substring(0, 2);
                                String bb = text.substring(2, 4);
                                String cc = text.substring(4, 6);
                                String dd = text.substring(6, 8);
                                current.code = aa + "-" + bb + "-" + cc + "-" + dd;
                                // Ebene aus Anzahl trailing Nullpaare
                                if ("00".equals(bb) && "00".equals(cc) && "00".equals(dd))
                                    current.classLevel = 1;
                                else if ("00".equals(cc) && "00".equals(dd))
                                    current.classLevel = 2;
                                else if ("00".equals(dd))
                                    current.classLevel = 3;
                                else
                                    current.classLevel = 4;
                            }
                        }

                        case "label" -> {
                            if (inClass && inPreferredName && !inDescribedBy
                                    && current != null && !text.isEmpty()) {
                                boolean isEn = pendingLang != null && pendingLang.startsWith("en");
                                if (isEn) { current.nameEn = text; current.name = text; }
                                else if (current.name == null) current.name = text;
                            }
                            pendingLang = null;
                        }

                        case "text" -> {
                            if (inClass && inDefinition && current != null && !text.isEmpty()) {
                                boolean isEn = pendingLang != null && pendingLang.startsWith("en");
                                if ((isEn || current.definition == null) && !text.startsWith("-no def"))
                                    current.definition = text;
                            }
                            pendingLang = null;
                        }
                    }
                    textBuf.setLength(0);
                }
            }
        } finally {
            reader.close();
        }
        return classes;
    }

    // OLD scanClasses REPLACED ABOVE -- following stub prevents compile error
    @SuppressWarnings("all")
    private List<ClassHolder> _deadCodeScanClasses_REPLACED() {
        return null;
    }
/*
                        // Top-Level Property-Liste (auÃŸerhalb von Klassen)
                        case "PR_LIST", "PRPLIST", "PROPERTY_LIST",
                             "CHARACTERISTIC_LIST", "CHAR_LIST" -> {
                            if (!inCl) inTopPrList = true;
                        }

                        case "PR", "PROPERTY", "CHARACTERISTIC", "CHAR" -> {
                            if (inTopPrList) {
                                inPr    = true;
                                current = new PropHolder();
                                // IRDI kann als Attribut kommen
                                String a = readIrdiAttribute(reader);
                                if (a != null) current.irdi = a;
                            }
                        }

                        case "PREFERRED_NAME", "PREF_NAME",
                             "SHORT_NAME", "NAME" -> {
                            if (inPr) currentLang = readLangAttribute(reader);
                        }
                    }

                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    textBuf.append(reader.getText());

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String ln   = reader.getLocalName().toUpperCase();
                    String text = textBuf.toString().trim();

                    switch (ln) {
                        case "CL", "CLASS" -> inCl = false;

                        case "PR_LIST", "PRPLIST", "PROPERTY_LIST",
                             "CHARACTERISTIC_LIST", "CHAR_LIST" -> {
                            if (!inCl) inTopPrList = false;
                        }

                        case "PR", "PROPERTY", "CHARACTERISTIC", "CHAR" -> {
                            if (inPr && current != null && current.irdi != null) {
                                map.put(current.irdi, current);
                            }
                            inPr = false; current = null;
                        }

                        case "IRDI", "ID" -> {
                            if (inPr && current != null && current.irdi == null && !text.isEmpty())
                                current.irdi = text;
                        }

                        case "PREFERRED_NAME", "PREF_NAME", "SHORT_NAME", "NAME" -> {
                            if (inPr && current != null && !text.isEmpty()) {
                                final PropHolder ph = current;
                                final String    tx = text;
                                assignByLang(currentLang, tx,
                                        n -> ph.name   = n,
                                        n -> ph.nameEn = n,
                                        () -> { if (ph.name == null) ph.name = tx; });
                            }
                        }

                        // Einheit: entweder direkt als String oder als IRDI
                        case "UN_IRDI", "UNIT_IRDI" -> {
                            if (inPr && current != null && !text.isEmpty())
                                current.unitIrdi = text;
                        }
                        case "UNIT", "UNIT_NAME", "UNIT_SHORT" -> {
                            if (inPr && current != null && !text.isEmpty())
                                current.unit = text;
                        }

                        case "DATA_TYPE", "TYPE", "DATATYPE",
                             "VALUE_FORMAT" -> {
                            if (inPr && current != null && !text.isEmpty())
                                current.dataType = text;
                        }
                    }
                    textBuf.setLength(0);
                }
            }
        } finally {
            reader.close();
        }
        return map;
    }

    // â”€â”€ Parser: Klassen â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<ClassHolder> scanClasses(byte[] xml) throws XMLStreamException {
        List<ClassHolder> classes = new ArrayList<>();
        XMLStreamReader   reader  = createReader(xml);

        boolean     inCl        = false;
        boolean     inClPrList  = false;
        ClassHolder current     = null;
        String      currentLang = null;
        StringBuilder textBuf   = new StringBuilder();

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ln = reader.getLocalName().toUpperCase();
                    textBuf.setLength(0);

                    switch (ln) {
                        case "CL", "CLASS" -> {
                            inCl    = true;
                            current = new ClassHolder();
                            // IRDI kann als Attribut kommen
                            String a = readIrdiAttribute(reader);
                            if (a != null) current.irdi = a;
                        }

                        case "PR_LIST", "PROPERTY_REF_LIST",
                             "CHAR_LIST", "CHARACTERISTIC_LIST",
                             "APPLICABLE_PROPERTIES" -> {
                            if (inCl) inClPrList = true;
                        }

                        case "PR", "PROPERTY", "CHARACTERISTIC", "CHAR",
                             "PROPERTY_REF", "CHAR_REF" -> {
                            if (inCl && inClPrList && current != null) {
                                String irdiAttr = readIrdiAttribute(reader);
                                if (irdiAttr != null) current.propertyIrdis.add(irdiAttr);
                            }
                        }

                        case "PREFERRED_NAME", "PREF_NAME",
                             "SHORT_NAME", "NAME" -> {
                            currentLang = readLangAttribute(reader);
                        }
                    }

                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    textBuf.append(reader.getText());

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String ln   = reader.getLocalName().toUpperCase();
                    String text = textBuf.toString().trim();

                    switch (ln) {
                        case "CL", "CLASS" -> {
                            if (current != null && current.code != null && !current.code.isBlank()) {
                                classes.add(current);
                            }
                            inCl = false; current = null;
                        }

                        case "PR_LIST", "PROPERTY_REF_LIST",
                             "CHAR_LIST", "CHARACTERISTIC_LIST",
                             "APPLICABLE_PROPERTIES" -> inClPrList = false;

                        // Klassen-Code (das wichtigste Feld!)
                        case "CODED_NAME", "CLASS_CODE", "CODE",
                             "ECLASS_CODE" -> {
                            if (inCl && !inClPrList && current != null && !text.isEmpty())
                                current.code = text;
                        }

                        case "IRDI", "ID" -> {
                            if (inCl && !inClPrList && current != null
                                    && current.irdi == null && !text.isEmpty())
                                current.irdi = text;
                            // IRDI als Property-Referenz innerhalb PR_LIST
                            if (inCl && inClPrList && current != null && !text.isEmpty())
                                current.propertyIrdis.add(text);
                        }

                        case "CLASS_LEVEL", "LEVEL", "HIER_LEVEL" -> {
                            if (inCl && current != null) {
                                try { current.classLevel = Integer.parseInt(text); }
                                catch (NumberFormatException ignored) {}
                            }
                        }

                        case "PREFERRED_NAME", "PREF_NAME",
                             "SHORT_NAME", "NAME" -> {
                            if (inCl && !inClPrList && current != null && !text.isEmpty()) {
                                final ClassHolder ch = current;
                                final String      tx = text;
                                assignByLang(currentLang, tx,
                                        n -> ch.name   = n,
                                        n -> ch.nameEn = n,
                                        () -> { if (ch.name == null) ch.name = tx; });
                            }
                        }

                        case "DEFINITION", "DEF", "NOTE" -> {
                            if (inCl && current != null
                                    && current.definition == null && !text.isEmpty())
                                current.definition = text;
                        }

*/

    // â”€â”€ Parser: Application Templates (Klasse â†’ Merkmal-Zuweisungen) â”€â”€â”€â”€â”€

    private Map<String, List<String>> scanApplicationTemplates(byte[] xml) throws XMLStreamException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        XMLStreamReader reader = createReader(xml);

        boolean inTemplate   = false;
        boolean inTmplPrList = false;
        String  currentClIrdi = null;
        List<String> currentProps = null;
        StringBuilder textBuf = new StringBuilder();

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ln = reader.getLocalName().toUpperCase();
                    textBuf.setLength(0);

                    switch (ln) {
                        case "APPLICATION_TEMPLATE", "APPL_TEMPLATE",
                             "TEMPLATE", "CLASS_APPLICATION_TEMPLATE" -> {
                            inTemplate    = true;
                            currentClIrdi = null;
                            currentProps  = new ArrayList<>();
                        }
                        case "PR_LIST", "PROPERTY_LIST", "CHAR_LIST" -> {
                            if (inTemplate) inTmplPrList = true;
                        }
                        case "PR", "PROPERTY", "CHAR" -> {
                            if (inTemplate && inTmplPrList) {
                                String a = readIrdiAttribute(reader);
                                if (a != null && currentProps != null) currentProps.add(a);
                            }
                        }
                    }
                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    textBuf.append(reader.getText());

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String ln   = reader.getLocalName().toUpperCase();
                    String text = textBuf.toString().trim();

                    switch (ln) {
                        case "APPLICATION_TEMPLATE", "APPL_TEMPLATE",
                             "TEMPLATE", "CLASS_APPLICATION_TEMPLATE" -> {
                            if (currentClIrdi != null && currentProps != null
                                    && !currentProps.isEmpty()) {
                                map.computeIfAbsent(currentClIrdi, k -> new ArrayList<>())
                                   .addAll(currentProps);
                            }
                            inTemplate = false; currentClIrdi = null; currentProps = null;
                        }
                        case "PR_LIST", "PROPERTY_LIST", "CHAR_LIST" -> inTmplPrList = false;

                        case "CL_IRDI", "CLASS_IRDI" -> {
                            if (inTemplate && !inTmplPrList && currentClIrdi == null)
                                currentClIrdi = text;
                        }

                        case "IRDI" -> {
                            if (inTemplate && !inTmplPrList && currentClIrdi == null)
                                currentClIrdi = text;
                            else if (inTemplate && inTmplPrList && currentProps != null)
                                currentProps.add(text);
                        }
                    }
                    textBuf.setLength(0);
                }
            }
        } finally {
            reader.close();
        }
        return map;
    }

    // â”€â”€ Template-Merge â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Template-Merkmals-Zuweisungen in ClassHolder Ã¼bertragen (per Klassen-IRDI) */
    private void applyTemplatesToClasses(List<ClassHolder> classes,
                                          Map<String, List<String>> templates) {
        if (templates.isEmpty()) return;
        Map<String, ClassHolder> byIrdi = new HashMap<>();
        for (ClassHolder ch : classes) {
            if (ch.irdi != null) byIrdi.put(ch.irdi, ch);
        }
        templates.forEach((clsIrdi, propIrdis) -> {
            ClassHolder ch = byIrdi.get(clsIrdi);
            if (ch != null) {
                // Template-Zuweisungen ergÃ¤nzen (Duplikate vermeiden)
                for (String irdi : propIrdis) {
                    if (!ch.propertyIrdis.contains(irdi)) ch.propertyIrdis.add(irdi);
                }
            }
        });
    }

    private void mergeTemplates(Map<String, List<String>> target,
                                 Map<String, List<String>> source) {
        source.forEach((k, v) ->
                target.computeIfAbsent(k, x -> new ArrayList<>()).addAll(v));
    }

    // â”€â”€ Inhaltstyp-Erkennung â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private enum ContentType { CLASSES, PROPERTIES, TEMPLATES, MIXED, UNKNOWN }

    private ContentType detectContentType(byte[] xml) {
        // ECLASS XML 5.0: contained_classes / contained_properties
        String head;
        try {
            int len = Math.min(xml.length, 16384);
            head = new String(xml, 0, len, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ContentType.UNKNOWN;
        }

        boolean hasClasses    = head.contains("contained_classes")
                             || head.contains("eclass_dictionary");
        boolean hasProperties = head.contains("contained_properties");
        boolean hasTemplates  = head.contains("APPLICATION_TEMPLATE")
                             || head.contains("APPL_TEMPLATE");

        if (hasClasses && hasProperties) return ContentType.MIXED;
        if (hasClasses)                  return ContentType.CLASSES;
        if (hasProperties)               return ContentType.PROPERTIES;
        if (hasTemplates)                return ContentType.TEMPLATES;
        return ContentType.UNKNOWN;
    }

    // â”€â”€ Versions-Erkennung â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    String detectVersion(byte[] xml) {
        // ECLASS XML 5.0: Version steht in <revision>16.0</revision>
        try {
            XMLStreamReader reader = createReader(xml);
            boolean inRevision = false;
            StringBuilder buf = new StringBuilder();
            int elementCount = 0;

            while (reader.hasNext() && elementCount < 80) {
                int ev = reader.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    inRevision = "revision".equals(reader.getLocalName());
                    if (inRevision) buf.setLength(0);
                    elementCount++;
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if (inRevision && "revision".equals(reader.getLocalName())) {
                        String text = buf.toString().trim();
                        if (text.matches("\\d+\\.\\d+")) { reader.close(); return text; }
                        inRevision = false;
                    }
                } else if ((ev == XMLStreamConstants.CHARACTERS
                        || ev == XMLStreamConstants.CDATA) && inRevision) {
                    buf.append(reader.getText());
                }
            }
            reader.close();
        } catch (Exception ignored) {}

        // Fallback: Regex im Header
        try {
            String head = new String(xml, 0, Math.min(xml.length, 4096), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("ECLASS(\\d+[._]\\d+)").matcher(head);
            if (m.find()) return m.group(1).replace('_', '.');
            m = java.util.regex.Pattern
                    .compile("<revision>(\\d+\\.\\d+)</revision>").matcher(head);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}

        return "unknown";
    }

    // â”€â”€ Preview (ohne DB-Zugriff) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private ParsePreview previewXml(byte[] xml, String filename) throws Exception {
        Map<String, PropHolder> propMap  = scanProperties(xml);
        List<ClassHolder>       classes  = scanClasses(xml);
        String                  version  = detectVersion(xml);

        // Erste paar Element-Namen aus der XML lesen (Diagnose)
        String rawSample = extractElementSample(xml, 15);

        List<String> sampleClasses = classes.stream()
                .limit(5)
                .map(c -> c.code + " â€“ " + (c.name != null ? c.name : c.nameEn))
                .toList();

        List<String> sampleProps = propMap.values().stream()
                .limit(5)
                .map(p -> p.irdi + " â€“ " + (p.name != null ? p.name : p.nameEn))
                .toList();

        return new ParsePreview(
                classes.size(), propMap.size(), 0,
                sampleClasses, sampleProps,
                List.of(filename != null ? filename : "?"),
                version, rawSample
        );
    }

    private ParsePreview previewZip(byte[] zipBytes) throws Exception {
        int classes = 0, props = 0, templates = 0;
        List<String> fileNames = new ArrayList<>();
        List<String> sampleCls = new ArrayList<>(), samplePrp = new ArrayList<>();
        String version = "unknown";

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
                    byte[] xml = zis.readAllBytes();
                    fileNames.add(entry.getName());
                    ContentType t = detectContentType(xml);
                    if (t == ContentType.CLASSES || t == ContentType.MIXED) {
                        List<ClassHolder> c = scanClasses(xml);
                        classes += c.size();
                        c.stream().limit(3).map(ch -> ch.code + " â€“ " + ch.name)
                         .forEach(sampleCls::add);
                    }
                    if (t == ContentType.PROPERTIES || t == ContentType.MIXED) {
                        Map<String, PropHolder> p = scanProperties(xml);
                        props += p.size();
                        p.values().stream().limit(3).map(ph -> ph.irdi + " â€“ " + ph.name)
                         .forEach(samplePrp::add);
                    }
                    if (t == ContentType.TEMPLATES) templates++;
                    if ("unknown".equals(version)) version = detectVersion(xml);
                }
                zis.closeEntry();
            }
        }

        return new ParsePreview(classes, props, templates,
                sampleCls, samplePrp, fileNames, version, "");
    }

    /** Liest die ersten N Element-LocalNames aus der XML fÃ¼r Diagnose */
    private String extractElementSample(byte[] xml, int maxElements) {
        StringBuilder sb = new StringBuilder();
        try {
            XMLStreamReader r = createReader(xml);
            int count = 0;
            while (r.hasNext() && count < maxElements) {
                if (r.next() == XMLStreamConstants.START_ELEMENT) {
                    sb.append("<").append(r.getLocalName()).append("> ");
                    count++;
                }
            }
            r.close();
        } catch (Exception ignored) {}
        return sb.toString();
    }

    // â”€â”€ Hilfsmethoden â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Liest IRDI aus Attributen (verschiedene Namen je ECLASS-Version) */
    private String readIrdiAttribute(XMLStreamReader r) {
        for (String name : new String[]{"IRDI","irdi","ID","id"}) {
            String v = r.getAttributeValue(null, name);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    /**
     * Liest Sprach-Attribut.
     * ECLASS nutzt sowohl "language" (Ã¤lter) als auch "LANGUAGE_IDENTIFIER" (neu).
     */
    private String readLangAttribute(XMLStreamReader r) {
        for (String name : new String[]{
                "LANGUAGE_IDENTIFIER","language","lang","Language","LANG"}) {
            String v = r.getAttributeValue(null, name);
            if (v != null && !v.isBlank()) return v.trim().toLowerCase();
        }
        return null;
    }

    /** Weist Text dem passenden Sprachfeld zu */
    @FunctionalInterface interface LangConsumer { void accept(String value); }

    private void assignByLang(String lang, String text,
                               LangConsumer setDe, LangConsumer setEn,
                               Runnable fallback) {
        if (lang == null) { fallback.run(); return; }
        if (lang.startsWith("de")) { setDe.accept(text); }
        else if (lang.startsWith("en")) { setEn.accept(text); }
        else fallback.run();
    }

    private XMLStreamReader createReader(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // XXE-Schutz (OWASP)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    // â”€â”€ Interne Holder-Klassen â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    static class PropHolder {
        String irdi, name, nameEn, unit, unitIrdi, dataType, definition;
    }

    static class ClassHolder {
        String       code, irdi, name, nameEn, definition, keywords;
        Integer      classLevel;
        List<String> propertyIrdis = new ArrayList<>();
    }
}
