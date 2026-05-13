package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporärer Debug-Controller
 * NUR für Entwicklung/Fehleranalyse!
 * Nach Bugfix wieder löschen oder deaktivieren.
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final DatasetService datasetService;

    /**
     * Test 1: Ist der Controller überhaupt erreichbar?
     * GET /debug/ping
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",    "OK");
        result.put("message",   "Server läuft!");
        result.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(result);
    }

    /**
     * Test 2: Datasets direkt aus Service abrufen
     * GET /debug/datasets?revisionId=1&revisionType=DESIGN
     */
    @GetMapping("/datasets")
    public ResponseEntity<Map<String, Object>> debugDatasets(
            @RequestParam(defaultValue = "1")      Long   revisionId,
            @RequestParam(defaultValue = "DESIGN") String revisionType) {

        Map<String, Object> result = new LinkedHashMap<>();

        try {
            List<Dataset> datasets = datasetService.getDatasets(revisionId, revisionType);

            result.put("success",      true);
            result.put("revisionId",   revisionId);
            result.put("revisionType", revisionType);
            result.put("count",        datasets.size());

            // Jedes Dataset einzeln ausgeben
            List<Map<String, Object>> datasetList = datasets.stream()
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("datasetId",   d.getDatasetId());
                        m.put("fileName",    d.getFileName());
                        m.put("fileSize",    d.getFileSize());
                        m.put("mimeType",    d.getMimeType());
                        m.put("category",    d.getCategory());
                        m.put("filePath",    d.getFilePath());
                        m.put("createdDate", d.getCreatedDate());
                        m.put("createdBy",   d.getCreatedBy());
                        return m;
                    })
                    .toList();

            result.put("datasets", datasetList);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getClass().getName());
            result.put("message", e.getMessage());

            // Cause Chain
            Throwable cause = e.getCause();
            int i = 1;
            while (cause != null && i <= 5) {
                result.put("cause_" + i, cause.getClass().getName()
                                         + ": " + cause.getMessage());
                cause = cause.getCause();
                i++;
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Test 3: Dataset-View Controller testen
     * GET /debug/view?revisionId=1&revisionType=DESIGN
     */
    @GetMapping("/view")
    public ResponseEntity<Map<String, Object>> debugView(
            @RequestParam(defaultValue = "1")      Long   revisionId,
            @RequestParam(defaultValue = "DESIGN") String revisionType) {

        Map<String, Object> result = new LinkedHashMap<>();

        try {
            List<Dataset> datasets = datasetService.getDatasets(revisionId, revisionType);

            // Gleiche Berechnung wie im Controller
            long totalBytes = datasets.stream()
                    .mapToLong(d -> d.getFileSize() != null ? d.getFileSize() : 0L)
                    .sum();

            long countWithCategory = datasets.stream()
                    .filter(d -> d.getCategory() != null)
                    .count();

            String totalSizeFormatted;
            if (totalBytes >= 1048576) {
                totalSizeFormatted = String.format("%.1f MB", totalBytes / 1048576.0);
            } else if (totalBytes >= 1024) {
                totalSizeFormatted = String.format("%.1f KB", totalBytes / 1024.0);
            } else {
                totalSizeFormatted = totalBytes + " Bytes";
            }

            result.put("success",           true);
            result.put("datasetCount",       datasets.size());
            result.put("totalSize",          totalSizeFormatted);
            result.put("countWithCategory",  countWithCategory);
            result.put("categories",
                       List.of(DatasetCategory.values()).stream()
                           .map(c -> Map.of(
                               "name",        c.name(),
                               "displayName", c.getDisplayName()))
                           .toList());

        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getClass().getName());
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Test 4: Datei-Pfad prüfen
     * GET /debug/file/{datasetId}
     */
    @GetMapping("/file/{datasetId}")
    public ResponseEntity<Map<String, Object>> debugFile(
            @PathVariable Long datasetId) {

        Map<String, Object> result = new LinkedHashMap<>();

        try {
            Dataset dataset = datasetService.getDataset(datasetId);

            java.nio.file.Path path = java.nio.file.Paths.get(dataset.getFilePath());

            result.put("success",    true);
            result.put("datasetId",  datasetId);
            result.put("fileName",   dataset.getFileName());
            result.put("filePath",   dataset.getFilePath());
            result.put("fileExists", java.nio.file.Files.exists(path));
            result.put("isReadable", java.nio.file.Files.isReadable(path));
            result.put("fileSize",   dataset.getFileSize());

            if (java.nio.file.Files.exists(path)) {
                result.put("actualSize",
                           java.nio.file.Files.size(path));
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getClass().getName());
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Test 5: Upload-Verzeichnis prüfen
     * GET /debug/uploaddir
     */
    @GetMapping("/uploaddir")
    public ResponseEntity<Map<String, Object>> debugUploadDir() {

        Map<String, Object> result = new LinkedHashMap<>();

        String uploadDir = "data/uploads/";
        java.nio.file.Path path = java.nio.file.Paths.get(uploadDir);

        result.put("uploadDir",     uploadDir);
        result.put("absolutePath",  path.toAbsolutePath().toString());
        result.put("exists",        java.nio.file.Files.exists(path));
        result.put("isDirectory",   java.nio.file.Files.isDirectory(path));
        result.put("isWritable",    java.nio.file.Files.isWritable(path));

        try {
            if (java.nio.file.Files.exists(path)) {
                long fileCount = java.nio.file.Files.list(path).count();
                result.put("fileCount", fileCount);
            }
        } catch (Exception e) {
            result.put("listError", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
	/**
 * Testet die komplette Excel → PDF Pipeline
 * GET /debug/excel-convert-test
 */
@GetMapping("/excel-convert-test")
public ResponseEntity<Map<String, Object>> testExcelConvert() {

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("timestamp", java.time.LocalDateTime.now().toString());

    // ── Schritt 1: LibreOffice Pfad prüfen ────────────────────────
    List<String> candidates = List.of(
            "C:/Program Files/LibreOffice/program/soffice.exe",
            "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
            "C:/Program Files/LibreOffice 7/program/soffice.exe",
            "C:/Program Files/LibreOffice 24/program/soffice.exe"
    );

    String foundPath = null;
    Map<String, Boolean> pathResults = new LinkedHashMap<>();
    for (String p : candidates) {
        boolean exists = new java.io.File(p).exists();
        pathResults.put(p, exists);
        if (exists && foundPath == null) foundPath = p;
    }
    result.put("step1_pathChecks", pathResults);
    result.put("step1_foundPath",  foundPath);

    if (foundPath == null) {
        result.put("status",  "FAILED");
        result.put("message", "LibreOffice nicht gefunden!");
        return ResponseEntity.ok(result);
    }

    // ── Schritt 2: Laufende Instanzen prüfen ──────────────────────
    try {
        ProcessBuilder pb = new ProcessBuilder(
                "cmd", "/c", "tasklist", "/FI",
                "IMAGENAME eq soffice.exe", "/NH");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String tasks = new String(p.getInputStream().readAllBytes());
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

        boolean isRunning = tasks.toLowerCase().contains("soffice");
        result.put("step2_libreofficeRunning", isRunning);
        result.put("step2_tasklistOutput",     tasks.trim());

        if (isRunning) {
            result.put("step2_warning",
                    "⚠️ LibreOffice läuft bereits — "
                    + "das verursacht 'Verbindung verweigert'!");
        }
    } catch (Exception e) {
        result.put("step2_error", e.getMessage());
    }

    // ── Schritt 3: Temp-Verzeichnis prüfen ───────────────────────
    try {
        java.nio.file.Path tmpDir =
                java.nio.file.Files.createTempDirectory("peb-test-");
        result.put("step3_tempDir",      tmpDir.toAbsolutePath().toString());
        result.put("step3_tempWritable", true);
        java.nio.file.Files.delete(tmpDir);
    } catch (Exception e) {
        result.put("step3_tempWritable", false);
        result.put("step3_error",        e.getMessage());
    }

    // ── Schritt 4: Mini Excel-Datei erstellen und konvertieren ────
    try {
        // Minimales XLSX als Byte-Array (echtes XLSX via Apache POI)
        // Alternativ: Einfache CSV-Datei testen
        java.nio.file.Path workDir =
                java.nio.file.Files.createTempDirectory(
                        "peb-conv-test-"
                        + java.util.UUID.randomUUID());

        // Erstelle eine CSV-Test-Datei (LibreOffice kann auch CSV → PDF)
        java.nio.file.Path testFile = workDir.resolve("test.csv");
        java.nio.file.Files.writeString(testFile,
                "Spalte1,Spalte2,Spalte3\n"
                + "Wert1,Wert2,Wert3\n"
                + "Test,123,ABC\n");

        result.put("step4_testFile",
                testFile.toAbsolutePath().toString());

        // LibreOffice starten
        ProcessBuilder pb = new ProcessBuilder(
                foundPath,
                "--headless",
                "--norestore",
                "--nofirststartwizard",
                "--nolockcheck",
                "--nologo",
                "--convert-to", "pdf",
                "--outdir", workDir.toAbsolutePath().toString(),
                testFile.toAbsolutePath().toString()
        );

        // Isoliertes Profil
        Map<String, String> env = pb.environment();
        env.put("UserInstallation",
                "file:///"
                + workDir.toAbsolutePath()
                         .toString()
                         .replace("\\", "/")
                + "/lo-profile");

        pb.redirectErrorStream(true);
        pb.directory(workDir.toFile());

        long startTime = java.lang.System.currentTimeMillis();
        Process process = pb.start();

        String output = new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        boolean finished = process.waitFor(
                30, java.util.concurrent.TimeUnit.SECONDS);
        long duration = java.lang.System.currentTimeMillis() - startTime;

        result.put("step4_finished",  finished);
        result.put("step4_exitCode",  finished ? process.exitValue() : -1);
        result.put("step4_output",    output.trim());
        result.put("step4_durationMs", duration);

        if (!finished) {
            process.destroyForcibly();
            result.put("step4_status", "TIMEOUT");
        } else if (process.exitValue() == 0) {
            // PDF vorhanden?
            java.nio.file.Path pdfPath = workDir.resolve("test.pdf");
            boolean pdfExists = java.nio.file.Files.exists(pdfPath);
            result.put("step4_pdfCreated", pdfExists);
            result.put("step4_status",
                    pdfExists ? "SUCCESS" : "PDF_MISSING");

            if (pdfExists) {
                result.put("step4_pdfSize",
                        java.nio.file.Files.size(pdfPath) + " bytes");
            }
        } else {
            result.put("step4_status", "FAILED");
        }

        // Aufräumen
        java.nio.file.Files.walk(workDir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(java.io.File::delete);

    } catch (Exception e) {
        result.put("step4_status",    "EXCEPTION");
        result.put("step4_exception", e.getClass().getName());
        result.put("step4_message",   e.getMessage());
    }

    // ── Gesamtstatus ──────────────────────────────────────────────
    String step4Status = (String) result.getOrDefault(
            "step4_status", "UNKNOWN");
    result.put("overallStatus", step4Status);

    return ResponseEntity.ok(result);
}
// Im DebugController ergänzen:

/**
 * Testet /datasets/{id}/as-pdf direkt
 * GET /debug/test-as-pdf/{datasetId}
 */
@GetMapping("/test-as-pdf/{datasetId}")
public ResponseEntity<Map<String, Object>> testAsPdf(
        @PathVariable Long datasetId) {

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("datasetId", datasetId);
    result.put("timestamp",
            java.time.LocalDateTime.now().toString());

    // ── Dataset prüfen ────────────────────────────────────────
    try {
        com.peb.model.base.Dataset dataset =
                datasetService.getDataset(datasetId);

        result.put("fileName",    dataset.getFileName());
        result.put("category",    dataset.getCategory());
        result.put("filePath",    dataset.getFilePath());
        result.put("fileExists",
                java.nio.file.Files.exists(
                        java.nio.file.Paths.get(
                                dataset.getFilePath())));

        // Ist es Excel?
        boolean isExcel = dataset.getCategory() ==
                com.peb.model.ebom.DatasetCategory.EXCEL_TABLE;
        result.put("isExcel", isExcel);

        if (!isExcel) {
            result.put("status",  "FAILED");
            result.put("message",
                    "Dataset ist kein EXCEL_TABLE! "
                    + "Kategorie: " + dataset.getCategory());
            return ResponseEntity.ok(result);
        }

    } catch (Exception e) {
        result.put("status",  "FAILED");
        result.put("message", "Dataset nicht gefunden: "
                + e.getMessage());
        return ResponseEntity.ok(result);
    }

    // ── HEAD-Request simulieren ───────────────────────────────
    result.put("headSimulation", "OK – Dataset ist EXCEL_TABLE");

    // ── GET /as-pdf simulieren ────────────────────────────────
    try {
        com.peb.model.base.Dataset dataset =
                datasetService.getDataset(datasetId);
        byte[] content = java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(dataset.getFilePath()));

        result.put("fileSizeBytes", content.length);
        result.put("conversionStart",
                java.time.LocalDateTime.now().toString());

        // Direkt konvertieren
        // (DatasetStreamController Logik nachbauen)
        java.nio.file.Path workDir =
                java.nio.file.Files.createTempDirectory(
                        "peb-debug-conv-");

        java.nio.file.Path inputFile =
                workDir.resolve(dataset.getFileName());
        java.nio.file.Files.write(inputFile, content);

        String libreOfficePath =
                "C:/Program Files/LibreOffice/program/soffice.exe";

        ProcessBuilder pb = new ProcessBuilder(
                libreOfficePath,
                "--headless",
                "--norestore",
                "--nofirststartwizard",
                "--nolockcheck",
                "--nologo",
                "--convert-to", "pdf",
                "--outdir",
                workDir.toAbsolutePath().toString(),
                inputFile.toAbsolutePath().toString()
        );

        java.util.Map<String, String> env = pb.environment();
        env.put("UserInstallation",
                "file:///"
                + workDir.toAbsolutePath()
                         .toString()
                         .replace("\\", "/")
                + "/lo-profile");

        pb.redirectErrorStream(true);
        pb.directory(workDir.toFile());

        long start   = java.lang.System.currentTimeMillis();
        Process proc = pb.start();
        String output = new String(
                proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        boolean done = proc.waitFor(
                60, java.util.concurrent.TimeUnit.SECONDS);
        long duration = java.lang.System.currentTimeMillis() - start;

        result.put("conversionDurationMs", duration);
        result.put("conversionOutput",     output.trim());
        result.put("conversionFinished",   done);
        result.put("conversionExitCode",
                done ? proc.exitValue() : -1);

        if (done && proc.exitValue() == 0) {
            String pdfName = dataset.getFileName()
                    .replaceAll("(?i)\\.(xlsx|xls|ods)$", ".pdf");
            java.nio.file.Path pdfPath = workDir.resolve(pdfName);
            boolean pdfExists =
                    java.nio.file.Files.exists(pdfPath);
            result.put("pdfCreated",  pdfExists);
            result.put("status",
                    pdfExists ? "SUCCESS" : "PDF_MISSING");
            if (pdfExists) {
                result.put("pdfSizeBytes",
                        java.nio.file.Files.size(pdfPath));
            }
        } else {
            result.put("status", done ? "CONVERT_FAILED" : "TIMEOUT");
        }

        // Aufräumen
        java.nio.file.Files.walk(workDir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(java.io.File::delete);

    } catch (Exception e) {
        result.put("status",    "EXCEPTION");
        result.put("exception", e.getClass().getName());
        result.put("message",   e.getMessage());

        // Stack Trace für Diagnose
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        result.put("stackTrace", sw.toString());
    }

    return ResponseEntity.ok(result);
}
}