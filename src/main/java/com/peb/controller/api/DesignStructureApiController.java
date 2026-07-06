package com.peb.controller.api;

import com.peb.dto.DesignStructureNodeDTO;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignStructure;
import com.peb.service.DesignService;
import com.peb.service.DesignStructureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST API für Design-Struktur-Operationen.
 *
 * Basis-URL: /api/v1/designs/{designId}/structure
 *
 * Endpunkte:
 *   GET    /api/v1/designs/{designId}/structure              → Baum (hierarchisch)
 *   GET    /api/v1/designs/{designId}/structure/flat         → Baum (flach, pre-order)
 *   GET    /api/v1/designs/{designId}/structure/children     → Direkte Kinder
 *   GET    /api/v1/designs/{designId}/structure/available    → Verfügbare Kinder (zum Hinzufügen)
 *   GET    /api/v1/designs/{designId}/structure/where-used   → Wo wird dieses Design verwendet?
 *   POST   /api/v1/designs/{designId}/structure              → Kind hinzufügen
 *   DELETE /api/v1/designs/{designId}/structure/{structureId}→ Link entfernen
 *   GET    /api/v1/designs/structure/links                   → Alle Links (Admin)
 */
@RestController
@RequestMapping("/api/v1/designs")
@RequiredArgsConstructor
@Slf4j
public class DesignStructureApiController {

    private final DesignStructureService structureService;
    private final DesignService          designService;

    // ══════════════════════════════════════════════════════════════════════
    // GET TREE (hierarchisch)
    // GET /api/v1/designs/{designId}/structure
    // GET /api/v1/designs/{designId}/structure?revisionId=123
    //
    // Liefert den vollständigen Baum als verschachtelte JSON-Struktur.
    // Ohne revisionId wird latestRevision verwendet.
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{designId}/structure")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getStructureTree(
            @PathVariable String designId) {
        try {
            Design design = designService.getDesign(designId);

            if (design.getLatestRevision() == null) {
                return ResponseEntity.ok(noRevisionResponse(designId));
            }

            DesignStructureNodeDTO tree = structureService.buildTree(designId);

            Map<String, Object> response = new HashMap<>();
            response.put("success",    true);
            response.put("designId",   designId);
            response.put("designName", design.getName());
            response.put("revisionId",
                    design.getLatestRevision().getRevisionId());
            response.put("revision",
                    design.getLatestRevision().getRevision());
            response.put("nodeCount",  countNodes(tree));
            response.put("maxDepth",   calculateMaxDepth(tree));
            response.put("tree",       toTreeMap(tree));

            log.info("GET /api/v1/designs/{}/structure → tree built",
                     designId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler beim Laden der Design-Struktur für {}: {}",
                      designId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + designId));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET TREE (flach / pre-order)
    // GET /api/v1/designs/{designId}/structure/flat
    //
    // Liefert denselben Baum als flache Liste (pre-order traversal).
    // Jeder Eintrag enthält depth und paddingLeft für einfache Darstellung.
    // Ideal für Excel-Export, tabellarische Auswertungen, VBScript.
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{designId}/structure/flat")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getStructureFlat(
            @PathVariable String designId) {
        try {
            Design design = designService.getDesign(designId);

            if (design.getLatestRevision() == null) {
                return ResponseEntity.ok(noRevisionResponse(designId));
            }

            DesignStructureNodeDTO tree     = structureService.buildTree(designId);
            List<DesignStructureNodeDTO> flat = structureService.flattenTree(tree);

            List<Map<String, Object>> flatList = flat.stream()
                    .map(this::toFlatNodeMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",    true);
            response.put("designId",   designId);
            response.put("designName", design.getName());
            response.put("revisionId",
                    design.getLatestRevision().getRevisionId());
            response.put("revision",
                    design.getLatestRevision().getRevision());
            response.put("nodeCount",  flatList.size());
            response.put("maxDepth",   calculateMaxDepth(tree));
            response.put("nodes",      flatList);

            log.info("GET /api/v1/designs/{}/structure/flat → {} nodes",
                     designId, flatList.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei flacher Struktur für {}: {}",
                      designId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + designId));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET DIRECT CHILDREN
    // GET /api/v1/designs/{designId}/structure/children
    //
    // Liefert nur die direkten Kinder (eine Ebene) ohne weitere Rekursion.
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{designId}/structure/children")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getDirectChildren(
            @PathVariable String designId) {
        try {
            Design design = designService.getDesign(designId);

            if (design.getLatestRevision() == null) {
                return ResponseEntity.ok(noRevisionResponse(designId));
            }

            // Root-Baum bauen, nur erste Ebene extrahieren
            DesignStructureNodeDTO tree = structureService.buildTree(designId);

            List<Map<String, Object>> children = tree.getChildren()
                    .stream()
                    .map(this::toFlatNodeMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",    true);
            response.put("designId",   designId);
            response.put("designName", design.getName());
            response.put("revisionId",
                    design.getLatestRevision().getRevisionId());
            response.put("childCount", children.size());
            response.put("children",   children);

            log.info("GET /api/v1/designs/{}/structure/children → {} children",
                     designId, children.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei Kindern für {}: {}",
                      designId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + designId));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET AVAILABLE CHILDREN
    // GET /api/v1/designs/{designId}/structure/available
    //
    // Liefert alle Designs die als Kind hinzugefügt werden dürfen.
    // (Bereits verlinkte, Vorfahren und sich selbst sind ausgeschlossen)
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{designId}/structure/available")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAvailableChildren(
            @PathVariable String designId) {
        try {
            Design design = designService.getDesign(designId);

            if (design.getLatestRevision() == null) {
                return ResponseEntity.ok(noRevisionResponse(designId));
            }

            List<DesignStructureNodeDTO> candidates =
                    structureService.getAvailableChildren(
                            design.getLatestRevision().getRevisionId(),
                            designId);

            List<Map<String, Object>> candidateList = candidates.stream()
                    .map(this::toFlatNodeMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",        true);
            response.put("designId",       designId);
            response.put("parentRevisionId",
                    design.getLatestRevision().getRevisionId());
            response.put("availableCount", candidateList.size());
            response.put("available",      candidateList);

            log.info("GET /api/v1/designs/{}/structure/available → {} candidates",
                     designId, candidateList.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei verfügbaren Kindern für {}: {}",
                      designId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + designId));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET WHERE-USED
    // GET /api/v1/designs/{designId}/structure/where-used
    //
    // Zeigt in welchen übergeordneten Designs dieses Design verwendet wird.
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{designId}/structure/where-used")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getWhereUsed(
            @PathVariable String designId) {
        try {
            Design design = designService.getDesign(designId);

            List<Map<String, Object>> parents =
                    structureService.getWhereUsed(designId)
                            .stream()
                            .map(this::toWhereUsedMap)
                            .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",     true);
            response.put("designId",    designId);
            response.put("designName",  design.getName());
            response.put("usageCount",  parents.size());
            response.put("usedIn",      parents);

            log.info("GET /api/v1/designs/{}/structure/where-used → {} parents",
                     designId, parents.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei where-used für {}: {}",
                      designId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + designId));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST – Kind hinzufügen
    // POST /api/v1/designs/{designId}/structure
    //
    // Body (JSON):
    // {
    //   "childId":         "DS00000002",
    //   "childRevisionId": 42,           ← optional, sonst latestRevision
    //   "quantity":        1,            ← optional, default 1
    //   "positionNumber":  "10"          ← optional
    // }
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/{designId}/structure")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> addChild(
            @PathVariable String              designId,
            @RequestBody  AddChildApiRequest  req) {
        try {
            Design parent = designService.getDesign(designId);

            if (parent.getLatestRevision() == null) {
                return ResponseEntity.badRequest()
                        .body(errorMap("Parent Design hat keine Revision."));
            }

            // childRevisionId: falls nicht angegeben → latestRevision des Kindes
            Long childRevisionId = req.childRevisionId();
            if (childRevisionId == null) {
                Design child = designService.getDesign(req.childId());
                if (child.getLatestRevision() == null) {
                    return ResponseEntity.badRequest()
                            .body(errorMap(
                                "Child Design ["
                                + req.childId()
                                + "] hat keine Revision."));
                }
                childRevisionId = child.getLatestRevision().getRevisionId();
            }

            int quantity = (req.quantity() != null && req.quantity() > 0)
                    ? req.quantity() : 1;

            DesignStructure link = structureService.addChild(
                    designId,
                    parent.getLatestRevision().getRevisionId(),
                    req.childId(),
                    childRevisionId,
                    quantity,
                    req.positionNumber());

            Map<String, Object> response = new HashMap<>();
            response.put("success",          true);
            response.put("message",          "Child Design erfolgreich hinzugefügt.");
            response.put("structureId",      link.getStructureId());
            response.put("parentId",         link.getParentId());
            response.put("parentRevisionId", link.getParentRevisionId());
            response.put("childId",          link.getChildId());
            response.put("childRevisionId",  link.getChildRevisionId());
            response.put("quantity",         link.getQuantity());
            response.put("positionNumber",   link.getPositionNumber() != null
                                             ? link.getPositionNumber() : "");
            response.put("createdBy",        link.getCreatedBy());
            response.put("createdDate",      link.getCreatedDate() != null
                                             ? link.getCreatedDate().toString() : "");

            log.info("POST /api/v1/designs/{}/structure → link {} created: [{}]→[{}]",
                     designId, link.getStructureId(),
                     designId, req.childId());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("addChild abgelehnt für {}: {}", designId, e.getMessage());
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        } catch (Exception e) {
            log.error("Fehler beim Hinzufügen von Kind zu {}: {}",
                      designId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE – Struktur-Link entfernen
    // DELETE /api/v1/designs/{designId}/structure/{structureId}
    //
    // Entfernt nur den Link – das Kind-Design wird NICHT gelöscht.
    // ══════════════════════════════════════════════════════════════════════

    @DeleteMapping("/{designId}/structure/{structureId}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> removeChild(
            @PathVariable String designId,
            @PathVariable Long   structureId) {
        try {
            structureService.removeChild(structureId);

            Map<String, Object> response = new HashMap<>();
            response.put("success",     true);
            response.put("message",     "Struktur-Link entfernt.");
            response.put("structureId", structureId);
            response.put("designId",    designId);

            log.info("DELETE /api/v1/designs/{}/structure/{} → removed",
                     designId, structureId);
            return ResponseEntity.ok(response);

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(errorMap("Struktur-Link nicht gefunden: "
                                   + structureId));
        } catch (Exception e) {
            log.error("Fehler beim Entfernen von Link {}: {}",
                      structureId, e.getMessage());
            return ResponseEntity.status(500)
                    .body(errorMap("Fehler: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET ALL LINKS (Admin / Übersicht)
    // GET /api/v1/designs/structure/links
    //
    // Gibt alle vorhandenen DESIGN_STRUCTURE-Zeilen zurück.
    // Nützlich für Debugging, Migrations-Checks und Admin-UIs.
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/structure/links")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllLinks() {
        try {
            List<Map<String, Object>> links =
                    structureService.getAllLinks()
                            .stream()
                            .map(this::toLinkMap)
                            .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",    true);
            response.put("totalLinks", links.size());
            response.put("links",      links);

            log.info("GET /api/v1/designs/structure/links → {} links",
                     links.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler beim Abrufen aller Links: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(errorMap("Fehler: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MAPPING – Hilfsmethoden
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Rekursiver Baum-Node → verschachtelte Map.
     * Kinder werden als "children"-Array eingebettet.
     */
    private Map<String, Object> toTreeMap(DesignStructureNodeDTO node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("designId",            node.getId());
        map.put("name",                node.getName());
        map.put("department",          node.getDepartment() != null
                                       ? node.getDepartment() : "");
        map.put("revisionId",          node.getRevisionId());
        map.put("revision",            node.getRevision() != null
                                       ? node.getRevision() : "");
        map.put("releaseStatus",       node.getReleaseStatus() != null
                                       ? node.getReleaseStatus() : "");
        map.put("releaseStatusDisplay",node.getReleaseStatusDisplay() != null
                                       ? node.getReleaseStatusDisplay() : "");
        map.put("structureId",         node.getStructureId());
        map.put("quantity",            node.getQuantity() != null
                                       ? node.getQuantity() : 1);
        map.put("positionNumber",      node.getPositionNumber() != null
                                       ? node.getPositionNumber() : "");
        map.put("depth",               node.getDepth());
        map.put("isLeaf",              node.isLeaf());
        map.put("childCount",          node.getChildren().size());

        // Kinder rekursiv einbetten
        List<Map<String, Object>> children = node.getChildren()
                .stream()
                .map(this::toTreeMap)
                .toList();
        map.put("children", children);

        return map;
    }

    /**
     * Node → flache Map ohne children-Array.
     * Enthält depth für tabellarische Darstellung.
     */
    private Map<String, Object> toFlatNodeMap(DesignStructureNodeDTO node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("designId",            node.getId());
        map.put("name",                node.getName());
        map.put("department",          node.getDepartment() != null
                                       ? node.getDepartment() : "");
        map.put("revisionId",          node.getRevisionId());
        map.put("revision",            node.getRevision() != null
                                       ? node.getRevision() : "");
        map.put("releaseStatus",       node.getReleaseStatus() != null
                                       ? node.getReleaseStatus() : "");
        map.put("releaseStatusDisplay",node.getReleaseStatusDisplay() != null
                                       ? node.getReleaseStatusDisplay() : "");
        map.put("structureId",         node.getStructureId());
        map.put("quantity",            node.getQuantity() != null
                                       ? node.getQuantity() : 1);
        map.put("positionNumber",      node.getPositionNumber() != null
                                       ? node.getPositionNumber() : "");
        map.put("depth",               node.getDepth());
        map.put("isLeaf",              node.isLeaf());
        map.put("childCount",          node.getChildren() != null
                                       ? node.getChildren().size() : 0);
        return map;
    }

    /**
     * DesignStructure-Entity → kompakte Link-Map für Admin-Endpunkt.
     */
    private Map<String, Object> toLinkMap(DesignStructure link) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("structureId",      link.getStructureId());
        map.put("parentId",         link.getParentId());
        map.put("parentRevisionId", link.getParentRevisionId());
        map.put("childId",          link.getChildId());
        map.put("childRevisionId",  link.getChildRevisionId());
        map.put("quantity",         link.getQuantity());
        map.put("positionNumber",   link.getPositionNumber() != null
                                    ? link.getPositionNumber() : "");
        map.put("createdDate",      link.getCreatedDate() != null
                                    ? link.getCreatedDate().toString() : "");
        map.put("createdBy",        link.getCreatedBy() != null
                                    ? link.getCreatedBy() : "");
        return map;
    }

    /**
     * Where-used Eintrag: DesignStructure-Link aus Elternsicht.
     */
    private Map<String, Object> toWhereUsedMap(DesignStructure link) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("structureId",      link.getStructureId());
        map.put("parentDesignId",   link.getParentId());
        map.put("parentRevisionId", link.getParentRevisionId());

        // Parent-Design-Name nachladen für bessere Lesbarkeit
        try {
            Design parent = designService.getDesign(link.getParentId());
            map.put("parentDesignName", parent.getName());
            map.put("parentDepartment", parent.getDepartment() != null
                                        ? parent.getDepartment() : "");
            if (parent.getLatestRevision() != null) {
                map.put("parentLatestRevision",
                        parent.getLatestRevision().getRevision());
                map.put("parentLatestStatus",
                        parent.getLatestRevision().getReleaseStatus().name());
            }
        } catch (Exception e) {
            map.put("parentDesignName", "– nicht gefunden –");
        }

        map.put("quantity",       link.getQuantity());
        map.put("positionNumber", link.getPositionNumber() != null
                                  ? link.getPositionNumber() : "");
        map.put("createdDate",    link.getCreatedDate() != null
                                  ? link.getCreatedDate().toString() : "");
        map.put("createdBy",      link.getCreatedBy() != null
                                  ? link.getCreatedBy() : "");
        return map;
    }

    // ══════════════════════════════════════════════════════════════════════
    // STATISCHE HILFSMETHODEN
    // ══════════════════════════════════════════════════════════════════════

    /** Zählt alle Nodes im Baum (inkl. Root). */
    private int countNodes(DesignStructureNodeDTO node) {
        if (node == null) return 0;
        int count = 1;
        if (node.getChildren() != null) {
            for (DesignStructureNodeDTO child : node.getChildren()) {
                count += countNodes(child);
            }
        }
        return count;
    }

    /** Berechnet die maximale Tiefe des Baums. */
    private int calculateMaxDepth(DesignStructureNodeDTO node) {
        if (node == null || node.isLeaf()) return 0;
        int maxChildDepth = 0;
        for (DesignStructureNodeDTO child : node.getChildren()) {
            maxChildDepth = Math.max(maxChildDepth,
                                     calculateMaxDepth(child));
        }
        return maxChildDepth + 1;
    }

    /** Einheitliche Response wenn keine Revision existiert. */
    private Map<String, Object> noRevisionResponse(String designId) {
        Map<String, Object> map = new HashMap<>();
        map.put("success",  true);
        map.put("designId", designId);
        map.put("message",  "Design hat noch keine Revision – "
                            + "keine Struktur vorhanden.");
        map.put("nodeCount", 0);
        map.put("tree",      null);
        map.put("nodes",     List.of());
        return map;
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error",   message);
        return map;
    }

    // ══════════════════════════════════════════════════════════════════════
    // REQUEST RECORDS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Request-Body für POST /{designId}/structure.
     *
     * childRevisionId ist optional – wird weggelassen, wird die
     * latestRevision des Child-Designs automatisch verwendet.
     */
    record AddChildApiRequest(
            String  childId,
            Long    childRevisionId,   // optional
            Integer quantity,          // optional, default 1
            String  positionNumber     // optional
    ) {}
}