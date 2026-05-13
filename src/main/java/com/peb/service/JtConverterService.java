package com.peb.service;

import com.peb.model.base.Dataset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.*;

/**
 * Service zur Konvertierung von 3D-Dateien nach GLB.
 *
 * Unterstützte Eingangsformate:
 *   - GLB/GLTF  → direkt durchreichen
 *   - JT        → externes Tool oder Fallback-GLB
 *   - Sonstige  → Fallback-GLB
 *
 * JT-Konverter-Optionen (Priorität):
 *   1. peb.viewer.converter.path  → externes CLI-Tool (jt2gltf, etc.)
 *   2. Fallback-GLB               → Platzhalter-Würfel mit Dateiname
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JtConverterService {

    // Pfad zum externen Konverter-Tool (optional)
    // Beispiel: /opt/jt2gltf/jt2gltf.exe  oder  /usr/local/bin/jt2gltf
    @Value("${peb.viewer.converter.path:}")
    private String converterPath;

    // Zusätzliche Argumente für den Konverter (optional)
    // Beispiel: --output-format glb --lod 0
    @Value("${peb.viewer.converter.args:}")
    private String converterArgs;

    @Value("${peb.viewer.cache-dir:data/viewer-cache/}")
    private String cacheDir;

    // JT Magic Bytes: 0xFF 0xFE  oder  0xFE 0xFF  (UTF-16 BOM)
    // gefolgt von "Version " – JT-Spezifikation §4.1
    private static final byte[] JT_MAGIC_V9  =
        { (byte)0xFF, (byte)0xFE };  // Little-Endian JT
    private static final byte[] JT_MAGIC_V10 =
        { (byte)0xFE, (byte)0xFF };  // Big-Endian JT

    private final DatasetService datasetService;

    // ─────────────────────────────────────────────────────────
    // ÖFFENTLICHE API
    // ─────────────────────────────────────────────────────────

    /**
     * Liefert eine GLB-Datei für das Dataset.
     * Konvertiert bei Bedarf und cached das Ergebnis.
     *
     * @param datasetId ID des Datasets
     * @return Path zur GLB-Datei (immer vorhanden, ggf. Fallback)
     */
    public Path getOrConvertToGlb(Long datasetId) throws IOException {

        Dataset dataset  = datasetService.getDataset(datasetId);
        String  fileName = dataset.getFileName() != null
                           ? dataset.getFileName().toLowerCase()
                           : "";

        // Cache vorbereiten
        Path cacheDirectory = Paths.get(cacheDir);
        Files.createDirectories(cacheDirectory);

        String cacheFileName = "dataset_" + datasetId + ".glb";
        Path   cachedGlb     = cacheDirectory.resolve(cacheFileName);

        // Cache-Treffer
        if (Files.exists(cachedGlb) && Files.size(cachedGlb) > 100) {
            log.debug("GLB aus Cache: datasetId={}", datasetId);
            return cachedGlb;
        }

        Path sourcePath = Paths.get(dataset.getFilePath());

        if (!Files.exists(sourcePath)) {
            log.warn("Quelldatei nicht gefunden: {} – nutze Fallback",
                     sourcePath);
            createFallbackGlb(cachedGlb, dataset.getFileName());
            return cachedGlb;
        }

        // Format-Erkennung
        FileFormat format = detectFormat(sourcePath, fileName);
        log.info("Format erkannt: {} → {} (datasetId={})",
                 fileName, format, datasetId);

        switch (format) {

            case GLB, GLTF -> {
                Files.copy(sourcePath, cachedGlb,
                           StandardCopyOption.REPLACE_EXISTING);
                log.info("GLB/GLTF direkt kopiert: {}", fileName);
            }

            case JT -> {
                boolean converted = attemptJtConversion(
                        sourcePath, cachedGlb, fileName);
                if (!converted) {
                    log.warn("JT-Konvertierung fehlgeschlagen für {} " +
                             "– nutze Fallback-GLB", fileName);
                    createFallbackGlb(cachedGlb, dataset.getFileName());
                }
            }

            default -> {
                log.warn("Unbekanntes Format: {} – nutze Fallback-GLB",
                         fileName);
                createFallbackGlb(cachedGlb, dataset.getFileName());
            }
        }

        return cachedGlb;
    }

    /**
     * Prüft ob eine Datei eine JT-Datei ist (Magic-Byte-Check).
     * Wird vom Controller genutzt um den Konvertierungspfad anzuzeigen.
     */
    public boolean isJtFile(Dataset dataset) {
        if (dataset == null || dataset.getFilePath() == null) return false;
        String name = dataset.getFileName() != null
                      ? dataset.getFileName().toLowerCase() : "";
        if (name.endsWith(".jt")) return true;
        try {
            Path p = Paths.get(dataset.getFilePath());
            return Files.exists(p) && hasJtMagicBytes(p);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cache-Eintrag löschen (nach Dataset-Update aufrufen).
     */
    public void invalidateCache(Long datasetId) throws IOException {
        Path cached = Paths.get(cacheDir,
                                "dataset_" + datasetId + ".glb");
        boolean deleted = Files.deleteIfExists(cached);
        if (deleted) {
            log.info("Cache invalidiert: datasetId={}", datasetId);
        }
    }

    /**
     * Gibt Info über den konfigurierten Konverter zurück.
     */
    public ConverterInfo getConverterInfo() {
        boolean configured = converterPath != null
                             && !converterPath.isBlank();
        boolean available  = configured
                             && Files.exists(Paths.get(converterPath));
        return new ConverterInfo(configured, available,
                                 converterPath, converterArgs);
    }

    // ─────────────────────────────────────────────────────────
    // FORMAT-ERKENNUNG
    // ─────────────────────────────────────────────────────────

    private enum FileFormat { GLB, GLTF, JT, UNKNOWN }

    /**
     * Format-Erkennung: zuerst Dateiendung, dann Magic Bytes.
     */
    private FileFormat detectFormat(Path path, String lowerName) {

        // Dateiendung
        if (lowerName.endsWith(".glb"))  return FileFormat.GLB;
        if (lowerName.endsWith(".gltf")) return FileFormat.GLTF;
        if (lowerName.endsWith(".jt"))   return FileFormat.JT;

        // Magic Bytes (Fallback wenn Endung fehlt/falsch)
        try {
            if (hasGlbMagicBytes(path)) return FileFormat.GLB;
            if (hasJtMagicBytes(path))  return FileFormat.JT;
        } catch (IOException e) {
            log.warn("Magic-Byte-Prüfung fehlgeschlagen: {}", e.getMessage());
        }

        return FileFormat.UNKNOWN;
    }

    /**
     * GLB Magic: erste 4 Bytes = 0x46 0x54 0x6C 0x67 ("glTF")
     */
    private boolean hasGlbMagicBytes(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] magic = is.readNBytes(4);
            return magic.length == 4
                   && magic[0] == 0x46  // 'g'
                   && magic[1] == 0x54  // 'l'  (Hinweis: GLB = 0x46546C67)
                   && magic[2] == 0x6C  // 'T'
                   && magic[3] == 0x67; // 'F'
        }
    }

    /**
     * JT Magic: erste 2 Bytes = BOM (0xFF 0xFE oder 0xFE 0xFF)
     * gefolgt von "V" (JT-Version-String-Anfang)
     * Spec: JT Open File Format Reference §4.1
     */
    private boolean hasJtMagicBytes(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] header = is.readNBytes(8);
            if (header.length < 4) return false;

            // Little-Endian JT (häufiger)
            boolean leJt = header[0] == (byte) 0xFF
                           && header[1] == (byte) 0xFE;
            // Big-Endian JT
            boolean beJt = header[0] == (byte) 0xFE
                           && header[1] == (byte) 0xFF;

            return leJt || beJt;
        }
    }

    // ─────────────────────────────────────────────────────────
    // JT-KONVERTIERUNG
    // ─────────────────────────────────────────────────────────

    /**
     * Versucht JT → GLB Konvertierung.
     * Unterstützt verschiedene CLI-Tools über einheitliches Interface.
     */
    private boolean attemptJtConversion(Path source,
                                         Path target,
                                         String fileName) {
        if (converterPath == null || converterPath.isBlank()) {
            log.info("Kein JT-Konverter konfiguriert " +
                     "(peb.viewer.converter.path)");
            return false;
        }

        if (!Files.exists(Paths.get(converterPath))) {
            log.warn("Konverter nicht gefunden: {}", converterPath);
            return false;
        }

        return runExternalConverter(source, target, fileName);
    }

    /**
     * Führt externes Konverter-Tool aus.
     *
     * Unterstützte Tools (Aufruf-Schema):
     *   jt2gltf:    jt2gltf <input.jt> <output.glb>
     *   JT Open:    jtopen-convert --input <in> --output <out> --format glb
     *
     * Das Tool wird über peb.viewer.converter.args konfiguriert.
     */
    private boolean runExternalConverter(Path source,
                                          Path target,
                                          String fileName) {
        try {
            // Argumente aufbauen
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(converterPath);

            // Zusätzliche Argumente (aus config)
            if (converterArgs != null && !converterArgs.isBlank()) {
                for (String arg : converterArgs.split("\\s+")) {
                    command.add(arg);
                }
            }

            // Input und Output
            command.add(source.toAbsolutePath().toString());
            command.add(target.toAbsolutePath().toString());

            log.info("Konverter-Aufruf: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            // Output loggen (für Fehlerdiagnose)
            StringBuilder output = new StringBuilder();
            Process process = pb.start();

            try (java.io.BufferedReader reader =
                     new java.io.BufferedReader(
                         new java.io.InputStreamReader(
                             process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            // Timeout: 120 Sekunden für große JT-Dateien
            boolean finished = process.waitFor(
                    120, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("Konverter-Timeout nach 120s: {}", fileName);
                return false;
            }

            int exitCode = process.exitValue();

            if (exitCode == 0 && Files.exists(target)
                    && Files.size(target) > 0) {
                log.info("JT erfolgreich konvertiert: {} → GLB ({} Bytes)",
                         fileName, Files.size(target));
                return true;
            }

            log.warn("Konverter Exit-Code={} für {}: {}",
                     exitCode, fileName, output.toString().trim());
            return false;

        } catch (Exception e) {
            log.error("Konverter-Ausführungsfehler für {}: {}",
                      fileName, e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────
    // FALLBACK-GLB GENERATOR
    // ─────────────────────────────────────────────────────────

    /**
     * Erstellt eine minimale gültige GLB-Datei als Platzhalter.
     * Zeigt einen beschrifteten Würfel mit dem Original-Dateinamen.
     */
    private void createFallbackGlb(Path target,
                                    String originalName) throws IOException {

        String safeName = (originalName != null)
                          ? originalName.replace("\"", "'") : "unknown";

        String gltfJson = """
            {
              "asset": {
                "version":   "2.0",
                "generator": "PEB Viewer Fallback",
                "extras":    {"originalFile": "%s", "isFallback": true}
              },
              "scene": 0,
              "scenes": [{"nodes": [0]}],
              "nodes":  [{"mesh": 0, "name": "%s"}],
              "meshes": [{
                "name": "FallbackBox",
                "primitives": [{
                  "attributes": {"POSITION": 0, "NORMAL": 1},
                  "indices": 2,
                  "mode": 4
                }]
              }],
              "accessors": [
                {"bufferView":0,"componentType":5126,
                 "count":24,"type":"VEC3",
                 "min":[-0.5,-0.5,-0.5],"max":[0.5,0.5,0.5]},
                {"bufferView":1,"componentType":5126,
                 "count":24,"type":"VEC3"},
                {"bufferView":2,"componentType":5123,
                 "count":36,"type":"SCALAR"}
              ],
              "bufferViews": [
                {"buffer":0,"byteOffset":0,  "byteLength":288},
                {"buffer":0,"byteOffset":288,"byteLength":288},
                {"buffer":0,"byteOffset":576,"byteLength":72}
              ],
              "buffers": [{"byteLength":648}]
            }
            """.formatted(safeName, safeName);

        byte[] jsonBytes  = gltfJson.getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        int    jsonPadded = (jsonBytes.length + 3) & ~3;
        byte[] binData    = buildBoxGeometry();
        int    binLen     = binData.length;
        int    totalLen   = 12 + 8 + jsonPadded + 8 + binLen;

        java.nio.ByteBuffer buf =
            java.nio.ByteBuffer.allocate(totalLen)
                               .order(ByteOrder.LITTLE_ENDIAN);

        // GLB Header
        buf.putInt(0x46546C67); // "glTF"
        buf.putInt(2);
        buf.putInt(totalLen);

        // JSON Chunk
        buf.putInt(jsonPadded);
        buf.putInt(0x4E4F534A); // "JSON"
        buf.put(jsonBytes);
        for (int i = jsonBytes.length; i < jsonPadded; i++) {
            buf.put((byte) 0x20);
        }

        // BIN Chunk
        buf.putInt(binLen);
        buf.putInt(0x004E4942); // "BIN\0"
        buf.put(binData);

        Files.write(target, buf.array());
        log.info("Fallback-GLB erstellt für: {}", originalName);
    }

    private byte[] buildBoxGeometry() {
        float[] positions = {
            -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,
             0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
             0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f,
            -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
             0.5f,-0.5f, 0.5f,  0.5f,-0.5f,-0.5f,
             0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f, -0.5f, 0.5f,-0.5f,
            -0.5f, 0.5f, 0.5f,  0.5f, 0.5f, 0.5f,
             0.5f, 0.5f,-0.5f, -0.5f, 0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,
             0.5f,-0.5f, 0.5f, -0.5f,-0.5f, 0.5f
        };
        float[] normals = {
             0,0,1,  0,0,1,  0,0,1,  0,0,1,
             0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1,
             1,0,0,  1,0,0,  1,0,0,  1,0,0,
            -1,0,0, -1,0,0, -1,0,0, -1,0,0,
             0,1,0,  0,1,0,  0,1,0,  0,1,0,
             0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0
        };
        short[] indices = {
             0,1,2,  0,2,3,  4,5,6,  4,6,7,
             8,9,10, 8,10,11, 12,13,14, 12,14,15,
            16,17,18,16,18,19, 20,21,22, 20,22,23
        };

        java.nio.ByteBuffer b =
            java.nio.ByteBuffer.allocate(648)
                               .order(ByteOrder.LITTLE_ENDIAN);
        for (float v : positions) b.putFloat(v);
        for (float v : normals)   b.putFloat(v);
        for (short v : indices)   b.putShort(v);
        return b.array();
    }

    // ─────────────────────────────────────────────────────────
    // RECORD
    // ─────────────────────────────────────────────────────────

    public record ConverterInfo(boolean configured,
                                boolean available,
                                String  path,
                                String  args) {}
}