package com.peb.controller;

import com.peb.dto.EclassClassDTO;
import com.peb.dto.EclassClassificationDTO;
import com.peb.dto.EclassSimilarResultDTO;
import com.peb.service.EclassImportService;
import com.peb.service.EclassService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * MVC-Controller für ECLASS-Klassifizierung.
 *
 * URL-Struktur:
 *   GET  /eclass                         – Katalog-Browser (Segments)
 *   GET  /eclass/search                  – Klassensuche
 *   GET  /eclass/{classId}               – Klassen-Detail
 *   GET  /eclass/classify/{type}/{id}    – Klassifizierungs-Formular
 *   POST /eclass/classify/{type}/{id}    – Klassifizierung speichern
 *   POST /eclass/remove/{type}/{id}      – Klassifizierung entfernen
 *   GET  /eclass/similar/{type}/{id}     – Ähnliche Objekte anzeigen
 *   GET  /eclass/import                  – Import-Seite (Admin)
 *   POST /eclass/import                  – XML-Datei importieren (Admin)
 *   GET  /eclass/objects/{code}          – Alle Objekte eines ECLASS-Codes
 */
@Controller
@RequestMapping("/eclass")
@RequiredArgsConstructor
@Slf4j
public class EclassController {

    private final EclassService       eclassService;
    private final EclassImportService  eclassImportService;

    // ── Katalog-Browser ─────────────────────────────────────────────────────

    @GetMapping
    public String catalog(Model model) {
        model.addAttribute("segments",      eclassService.getSegments());
        model.addAttribute("versions",      eclassService.getAvailableVersions());
        model.addAttribute("commodityCount", eclassService.getCommodityCount());
        return "eclass/eclass-catalog";
    }

    @GetMapping("/search")
    @ResponseBody
    public List<EclassClassDTO> search(@RequestParam String term) {
        return eclassService.searchClasses(term);
    }

    @GetMapping("/browse")
    @ResponseBody
    public List<EclassClassDTO> browse(@RequestParam String level,
                                        @RequestParam String parent) {
        return switch (level) {
            case "maingroup"  -> eclassService.getMainGroups(parent);
            case "group"      -> eclassService.getGroups(parent);
            case "commodity"  -> eclassService.getCommodities(parent);
            default           -> List.of();
        };
    }

    @GetMapping("/{classId}")
    public String classDetail(@PathVariable Long classId, Model model) {
        model.addAttribute("eclassClass", eclassService.getEclassClass(classId));
        return "eclass/eclass-detail";
    }

    // ── Objekte eines Codes ─────────────────────────────────────────────────

    @GetMapping("/objects/{code}")
    public String objectsByCode(@PathVariable String code, Model model) {
        List<EclassSimilarResultDTO> objects = eclassService.findByEclassCode(code);
        model.addAttribute("code",    code);
        model.addAttribute("objects", objects);
        return "eclass/eclass-objects";
    }

    // ── Klassifizierungs-Formular ───────────────────────────────────────────

    /**
     * Zeigt das Klassifizierungs-Formular für ein PEB-Objekt.
     * URL-Beispiel: /eclass/classify/DESIGN/DS00000001
     */
    @GetMapping("/classify/{objectType}/{objectId}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String classifyForm(@PathVariable String objectType,
                                @PathVariable String objectId,
                                Model model) {
        ObjectType type = ObjectType.valueOf(objectType.toUpperCase());

        // Bestehende Klassifizierung laden (oder null)
        EclassClassificationDTO existing = eclassService.getClassification(objectId, type);
        model.addAttribute("classification", existing);
        model.addAttribute("objectId",       objectId);
        model.addAttribute("objectType",     type);

        // Falls bestehende Klassifizierung → ECLASS-Klasse mit Properties
        if (existing != null && existing.getEclassClassId() != null) {
            model.addAttribute("eclassClass",
                    eclassService.getEclassClass(existing.getEclassClassId()));
        }

        return "eclass/eclass-classify";
    }

    /**
     * Speichert die Klassifizierung.
     */
    @PostMapping("/classify/{objectType}/{objectId}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String saveClassification(@PathVariable String objectType,
                                      @PathVariable String objectId,
                                      @RequestParam Long eclassClassId,
                                      @RequestParam Map<String, String> allParams,
                                      RedirectAttributes redirectAttributes) {
        ObjectType type = ObjectType.valueOf(objectType.toUpperCase());

        // Nur Property-Parameter extrahieren (Format: prop_{id} → value)
        Map<Long, String> propertyValues = new java.util.LinkedHashMap<>();
        allParams.forEach((key, value) -> {
            if (key.startsWith("prop_")) {
                try {
                    Long propId = Long.parseLong(key.substring(5));
                    propertyValues.put(propId, value);
                } catch (NumberFormatException ignored) {}
            }
        });

        try {
            eclassService.classify(objectId, type, eclassClassId, propertyValues);
            redirectAttributes.addFlashAttribute("success",
                    "ECLASS-Klassifizierung erfolgreich gespeichert.");
        } catch (Exception e) {
            log.error("Fehler beim Klassifizieren von {} ({})", objectId, type, e);
            redirectAttributes.addFlashAttribute("error",
                    "Fehler: " + e.getMessage());
        }

        return "redirect:" + resolveDetailUrl(objectId, type);
    }

    /**
     * Entfernt die Klassifizierung eines Objekts.
     */
    @PostMapping("/remove/{objectType}/{objectId}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String removeClassification(@PathVariable String objectType,
                                        @PathVariable String objectId,
                                        RedirectAttributes redirectAttributes) {
        ObjectType type = ObjectType.valueOf(objectType.toUpperCase());
        eclassService.removeClassification(objectId, type);
        redirectAttributes.addFlashAttribute("info",
                "ECLASS-Klassifizierung wurde entfernt.");
        return "redirect:" + resolveDetailUrl(objectId, type);
    }

    // ── Ähnliche Objekte ────────────────────────────────────────────────────

    @GetMapping("/similar/{objectType}/{objectId}")
    public String similarObjects(@PathVariable String objectType,
                                  @PathVariable String objectId,
                                  Model model) {
        ObjectType type = ObjectType.valueOf(objectType.toUpperCase());
        List<EclassSimilarResultDTO> similar = eclassService.findSimilar(objectId, type);
        EclassClassificationDTO classification = eclassService.getClassification(objectId, type);

        model.addAttribute("objectId",         objectId);
        model.addAttribute("objectType",        type);
        model.addAttribute("classification",    classification);
        model.addAttribute("similarObjects",    similar);
        model.addAttribute("detailUrl",         resolveDetailUrl(objectId, type));
        return "eclass/eclass-similar";
    }

    // ── Import (nur ADMIN) ──────────────────────────────────────────────────

    @GetMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public String importPage(Model model) {
        model.addAttribute("versions",       eclassService.getAvailableVersions());
        model.addAttribute("commodityCount", eclassService.getCommodityCount());
        return "eclass/eclass-import";
    }

    /**
     * Vorschau-Analyse: Datei parsen OHNE zu speichern.
     * Gibt JSON zurück (AJAX-Aufruf vom Import-Formular).
     */
    @PostMapping("/import/preview")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> previewImport(@RequestParam MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Keine Datei übergeben."));
        }
        try {
            byte[] bytes   = file.getBytes();
            String fname   = file.getOriginalFilename() != null
                           ? file.getOriginalFilename() : "upload.xml";
            EclassImportService.ParsePreview preview =
                    eclassImportService.previewFile(bytes, fname);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("ECLASS-Preview Fehler", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** Import-Endpunkt — akzeptiert XML und ZIP */
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public String importXml(@RequestParam MultipartFile file,
                             @RequestParam(required = false) String forceVersion,
                             RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Keine Datei ausgewählt.");
            return "redirect:/eclass/import";
        }

        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        if (!filename.endsWith(".xml") && !filename.endsWith(".zip")) {
            redirectAttributes.addFlashAttribute("error",
                    "Nur XML- oder ZIP-Dateien werden akzeptiert.");
            return "redirect:/eclass/import";
        }

        log.info("ECLASS-Import gestartet: {} ({} KB)",
                file.getOriginalFilename(), file.getSize() / 1024);

        try {
            EclassImportService.ImportResult result =
                    eclassImportService.importFile(
                            file.getInputStream(),
                            file.getOriginalFilename(),
                            forceVersion);

            if (result.isSuccess()) {
                String msg = String.format(
                        "Import abgeschlossen (Version %s): %d Klassen neu, " +
                        "%d aktualisiert, %d Merkmale gesamt.",
                        result.version(),
                        result.classesImported(),
                        result.classesUpdated(),
                        result.propertiesTotal());
                if (!result.warnings().isEmpty()) {
                    msg += " Hinweise: " + String.join("; ", result.warnings());
                }
                redirectAttributes.addFlashAttribute("success", msg);
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Import fehlgeschlagen: " + result.errorMessage());
            }
        } catch (Exception e) {
            log.error("ECLASS-Import Fehler", e);
            redirectAttributes.addFlashAttribute("error",
                    "Import-Fehler: " + e.getMessage());
        }

        return "redirect:/eclass/import";
    }

    /** Korrigiert segment/mainGroup/groupCode/classLevel aller importierten Klassen (einmalig nach Erstimport) */
    @PostMapping("/admin/fix-hierarchy")
    @PreAuthorize("hasRole('ADMIN')")
    public String fixHierarchy(RedirectAttributes ra) {
        try {
            int count = eclassService.fixHierarchyData();
            ra.addFlashAttribute("success",
                    "Hierarchie-Felder korrigiert: " + count + " Klassen aktualisiert.");
        } catch (Exception e) {
            log.error("Fehler beim Reparieren der Hierarchie", e);
            ra.addFlashAttribute("error", "Fehler: " + e.getMessage());
        }
        return "redirect:/eclass/import";
    }

    // ── Hilfsmethode ───────────────────────────────────────────────────────

    private String resolveDetailUrl(String objectId, ObjectType type) {
        return switch (type) {
            case DESIGN    -> "/designs/" + objectId;
            case DRAWING   -> "/drawings/" + objectId;
            case MACHINE   -> "/ebom/machine/" + objectId;
            case SYSTEM    -> "/ebom/system/" + objectId;
            case SUBSYSTEM -> "/ebom/subsystem/" + objectId;
            case MODULE    -> "/ebom/module/" + objectId;
            case SUBMODULE -> "/ebom/submodule/" + objectId;
            case PART      -> "/ebom/part/" + objectId;
            case AUXILIARY -> "/ebom/auxiliary/" + objectId;
            case MATERIAL  -> "/materials/" + objectId;
            default        -> "/eclass";
        };
    }
}
