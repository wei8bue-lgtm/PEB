package com.peb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LibreOfficeConverterService {

    // ✅ @Value liest application.properties korrekt
    @Value("${app.libreoffice.path:}")
    private String configuredPath;

    private static final int TIMEOUT_SECONDS = 60;

    // ── Verfügbarkeit prüfen (HEAD-Request) ───────────────────────────
    public void checkAvailability() {
        // Wirft RuntimeException wenn nicht gefunden
        String path = resolveLibreOfficePath();
        log.debug("LibreOffice verfügbar: {}", path);
    }

    // ── Excel → PDF Konvertierung ──────────────────────────────────────
    public byte[] convertExcelToPdf(byte[] excelContent,
                                     String originalName)
            throws Exception {

        String libreOfficePath = resolveLibreOfficePath();
        Path   workDir         = Files.createTempDirectory(
                "peb-conv-" + UUID.randomUUID());

        log.info("Konvertierung: {} → PDF ({})",
                originalName, workDir.toAbsolutePath());

        try {
            // ── Datei schreiben ────────────────────────────────────
            Path inputFile = workDir.resolve(originalName);
            Files.write(inputFile, excelContent);

            // ── LibreOffice Prozess ────────────────────────────────
            ProcessBuilder pb = new ProcessBuilder(
                    libreOfficePath,
                    "--headless",
                    "--norestore",
                    "--nofirststartwizard",
                    "--nolockcheck",
                    "--nologo",
                    "--convert-to", "pdf",
                    "--outdir", workDir.toAbsolutePath().toString(),
                    inputFile.toAbsolutePath().toString()
            );

            // ── Isoliertes User-Profil ─────────────────────────────
            Map<String, String> env = pb.environment();
            env.put("UserInstallation",
                    "file:///"
                    + workDir.toAbsolutePath()
                             .toString()
                             .replace("\\", "/")
                    + "/lo-profile");

            pb.redirectErrorStream(true);
            pb.directory(workDir.toFile());

            Process process  = pb.start();
            String  output   = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

            boolean finished = process.waitFor(
                    TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(
                        "LibreOffice Timeout nach "
                        + TIMEOUT_SECONDS + "s");
            }

            int exitCode = process.exitValue();
            log.info("LibreOffice Exit={} | {}", exitCode, output.trim());

            if (exitCode != 0) {
                throw new RuntimeException(
                        "LibreOffice Fehler (Exit "
                        + exitCode + "): " + output);
            }

            // ── PDF einlesen ───────────────────────────────────────
            String pdfName = originalName.replaceAll(
                    "(?i)\\.(xlsx|xls|ods|csv)$", ".pdf");
            Path pdfPath = workDir.resolve(pdfName);

            if (!Files.exists(pdfPath)) {
                String files = Files.list(workDir)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.joining(", "));
                throw new RuntimeException(
                        "PDF nicht gefunden: " + pdfName
                        + " | Dateien: " + files);
            }

            byte[] pdf = Files.readAllBytes(pdfPath);
            log.info("Konvertierung OK: {} KB", pdf.length / 1024);
            return pdf;

        } finally {
            cleanupDirectory(workDir);
        }
    }

    // ── LibreOffice Pfad ermitteln ─────────────────────────────────────
    private String resolveLibreOfficePath() {

        // 1. Aus application.properties via @Value
        if (configuredPath != null && !configuredPath.isBlank()) {
            log.debug("LibreOffice Pfad aus Properties: {}", configuredPath);
            if (!new File(configuredPath).exists()) {
                throw new RuntimeException(
                        "LibreOffice nicht gefunden unter: "
                        + configuredPath);
            }
            return configuredPath;
        }

        // 2. Standard Windows-Pfade
        List<String> candidates = List.of(
                "C:/Program Files/LibreOffice/program/soffice.exe",
                "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
                "C:/Program Files/LibreOffice 7/program/soffice.exe",
                "C:/Program Files/LibreOffice 24/program/soffice.exe"
        );

        for (String path : candidates) {
            if (new File(path).exists()) {
                log.info("LibreOffice gefunden: {}", path);
                return path;
            }
        }

        throw new RuntimeException(
                "LibreOffice nicht gefunden! Bitte in "
                + "application.properties setzen: "
                + "app.libreoffice.path=C:/Program Files/"
                + "LibreOffice/program/soffice.exe");
    }

    // ── Temp-Verzeichnis aufräumen ─────────────────────────────────────
    private void cleanupDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            log.warn("Cleanup fehlgeschlagen: {}", dir, e);
        }
    }
}