// src/main/java/com/peb/controller/product/PlantBOPController.java
package com.peb.controller.product;

import com.peb.model.product.*;
import com.peb.repository.product.*;
import com.peb.service.product.BottleneckAnalysisService;
import com.peb.service.product.LineBalancingService;
import com.peb.service.product.RevisionChainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/plant-bop")
@RequiredArgsConstructor
@Slf4j
public class PlantBOPController {

    // ── Dependencies (alle final – @RequiredArgsConstructor) ─────
    private final PlantBOPRepository         plantBOPRepo;
    private final PlantBOPNodeRepository     nodeRepo;
    private final PlantBOPRevisionRepository plantRevRepo;
    private final ProductBOPRepository       productBOPRepo;
    private final ManufacturingBOMRepository mfgBomRepo;
    private final LineBalancingService       balancingService;
    private final BottleneckAnalysisService  bottleneckService; // NEU
    private final RevisionChainService       revisionChainService;

        @Value("${peb.linebalancing.ai-agent-url:https://vertexaisearch.cloud.google.com/eu/home/cid/bcb383f1-26d8-41fd-9a55-623f7e93de92/r/agent/379611006448038179/session/-?pli=1}")
        private String lineBalancingAiAgentUrl;

    // ── Liste ────────────────────────────────────────────────────
    @GetMapping
    public String list(Model model) {
        model.addAttribute("plantBOPs", plantBOPRepo.findAll());
        return "plant-bop/list";
    }

    // ── Neu (Formular) ───────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("plantBOP", new PlantBOP());
        model.addAttribute("isEdit",   false);
        return "plant-bop/form";
    }

    // ── Neu (Speichern) ──────────────────────────────────────────
    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            @ModelAttribute PlantBOP plantBOP,
            RedirectAttributes ra) {

        PlantBOP saved = plantBOPRepo.save(plantBOP);

        PlantBOPRevision initRev = new PlantBOPRevision();
        initRev.setPlantBOP(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        plantRevRepo.save(initRev);

        ra.addFlashAttribute("successMessage",
                "✅ Plant BOP '" + saved.getName() + "' erstellt.");
        return "redirect:/plant-bop/" + saved.getId();
    }

    // ── Detail ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {

        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plant BOP nicht gefunden: " + id));

        PlantBOPRevision latestRevision =
                plantBOP.getLatestRevision();
        model.addAttribute("latestRevision", latestRevision);

        String statusName =
                (latestRevision != null
                 && latestRevision.getStatus() != null)
                ? latestRevision.getStatus().name()
                : "KEINE_REVISION";
        model.addAttribute("statusName", statusName);

        List<PlantBOPNode> rootNodes = nodeRepo
                .findByPlantBOPIdAndParentIsNullOrderBySortOrder(id);
        enrichNodes(rootNodes);

        List<PlantBOPNode> allNodes =
                nodeRepo.findAllByPlantBOPIdWithDetails(id);

        model.addAttribute("plantBOP",             plantBOP);
        model.addAttribute("rootNodes",            rootNodes);
        model.addAttribute("nodeTypes",
                PlantBOPNode.NodeType.values());
        model.addAttribute("availableProductBOPs",
                productBOPRepo.findAll());
        model.addAttribute("availableMfgBOMs",
                mfgBomRepo.findAll());
        model.addAttribute("allNodes",             allNodes);
        model.addAttribute("revisionChain",
                plantBOP.getRevisions());

        return "plant-bop/detail";
    }

    // ── Edit (Formular) ──────────────────────────────────────────
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editForm(@PathVariable Long id, Model model) {

        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plant BOP nicht gefunden: " + id));

        PlantBOPRevision latestRevision =
                plantBOP.getLatestRevision();
        String statusName =
                (latestRevision != null
                 && latestRevision.getStatus() != null)
                ? latestRevision.getStatus().name()
                : "KEINE_REVISION";

        model.addAttribute("plantBOP",       plantBOP);
        model.addAttribute("isEdit",         true);
        model.addAttribute("statusName",     statusName);
        model.addAttribute("latestRevision", latestRevision);
        return "plant-bop/form";
    }

    // ── Edit (Speichern) ─────────────────────────────────────────
    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editSave(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String plantCode,
            @RequestParam(required = false) String bopVersion,
            @RequestParam(required = false) String description,
            RedirectAttributes ra) {

        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plant BOP nicht gefunden: " + id));

        plantBOP.setName(name);
        plantBOP.setPlantCode(plantCode);
        plantBOP.setBopVersion(
                bopVersion != null ? bopVersion : "01");
        plantBOP.setDescription(description);
        plantBOPRepo.save(plantBOP);

        ra.addFlashAttribute("successMessage",
                "✅ Plant BOP '" + plantBOP.getName()
                + "' aktualisiert.");
        return "redirect:/plant-bop/" + id;
    }

    // ── Delete ───────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes ra) {

        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plant BOP nicht gefunden: " + id));

        if (!plantBOP.getNodes().isEmpty()) {
            ra.addFlashAttribute("errorMessage",
                    "❌ Löschen nicht möglich: Plant BOP '"
                    + plantBOP.getName()
                    + "' enthält noch "
                    + plantBOP.getNodes().size()
                    + " Knoten. Bitte zuerst alle Knoten löschen.");
            return "redirect:/plant-bop/" + id;
        }

        String name = plantBOP.getName();
        plantBOPRepo.delete(plantBOP);
        ra.addFlashAttribute("successMessage",
                "🗑️ Plant BOP '" + name + "' erfolgreich gelöscht.");
        return "redirect:/plant-bop";
    }

    // ── Knoten hinzufügen ────────────────────────────────────────
    @PostMapping("/{plantBopId}/nodes/add")
    @PreAuthorize("hasAuthority('WRITE')")
    public String addNode(
            @PathVariable Long plantBopId,
            @RequestParam PlantBOPNode.NodeType nodeType,
            @RequestParam String nodeName,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Long productBopId,
            @RequestParam(required = false) Long mfgBomId,
            @RequestParam(required = false) Long boeId,
            @RequestParam(required = false) String boeName,
            RedirectAttributes ra) {

        PlantBOP plantBOP = plantBOPRepo.findById(plantBopId)
                .orElseThrow();

        PlantBOPNode node = new PlantBOPNode();
        node.setName(nodeName);
        node.setNodeType(nodeType);
        node.setPlantBOP(plantBOP);

        if (parentId != null) {
            nodeRepo.findById(parentId)
                    .ifPresent(node::setParent);
        }

        switch (nodeType) {
            case PRODUCT_BOP -> {
                if (productBopId != null) {
                    productBOPRepo.findById(productBopId)
                            .ifPresent(node::setProductBOP);
                }
            }
            case MANUFACTURING_BOM -> {
                if (mfgBomId != null) {
                    mfgBomRepo.findById(mfgBomId)
                            .ifPresent(node::setManufacturingBOM);
                }
            }
            case BOE -> {
                node.setBoeId(boeId);
                node.setBoeName(boeName);
            }
            default -> { /* FOLDER */ }
        }

        List<PlantBOPNode> siblings = parentId != null
                ? nodeRepo.findByParentIdOrderBySortOrder(parentId)
                : nodeRepo
                    .findByPlantBOPIdAndParentIsNullOrderBySortOrder(
                            plantBopId);
        int maxSort = siblings.stream()
                .mapToInt(n -> n.getSortOrder() != null
                        ? n.getSortOrder() : 0)
                .max().orElse(0);
        node.setSortOrder(maxSort + 10);

        nodeRepo.save(node);
        log.info("Knoten '{}' ({}) zu PlantBOP {} hinzugefügt.",
                nodeName, nodeType, plantBopId);

        ra.addFlashAttribute("successMessage",
                "✅ Knoten '" + nodeName + "' hinzugefügt.");
        return "redirect:/plant-bop/" + plantBopId;
    }

    // ── Knoten löschen ───────────────────────────────────────────
    @PostMapping("/{plantBopId}/nodes/{nodeId}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteNode(
            @PathVariable Long plantBopId,
            @PathVariable Long nodeId,
            RedirectAttributes ra) {

        nodeRepo.deleteById(nodeId);
        ra.addFlashAttribute("successMessage",
                "🗑️ Knoten gelöscht.");
        return "redirect:/plant-bop/" + plantBopId;
    }

    // ── Line Balancing (ERSETZT die alte Methode) ─────────────────
    @GetMapping("/{id}/balancing")
    public String balancing(@PathVariable Long id, Model model) {

        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow();

        LineBalancingService.LineBalancingResult result =
                balancingService.calculate(id, plantBOP.getName());

        BottleneckAnalysisService.BottleneckReport report =
                bottleneckService.analyse(result);

        model.addAttribute("result",   result);
        model.addAttribute("plantBOP", plantBOP);
        model.addAttribute("report",   report);
        model.addAttribute("lineBalancingAiAgentUrl",
                lineBalancingAiAgentUrl);
        return "plant-bop/balancing";
    }

    // ── Hilfsmethoden ────────────────────────────────────────────
    private void enrichNodes(List<PlantBOPNode> nodes) {
        for (PlantBOPNode node : nodes) {
            if (node.getManufacturingBOM() != null) {
                mfgBomRepo.findByIdWithDetails(
                        node.getManufacturingBOM().getId())
                        .ifPresent(node::setManufacturingBOM);
            }
            if (node.getProductBOP() != null) {
                List<ManufacturingBOM> enriched =
                        node.getProductBOP()
                            .getManufacturingBOMs()
                            .stream()
                            .map(mfg -> mfgBomRepo
                                    .findByIdWithDetails(mfg.getId())
                                    .orElse(mfg))
                            .toList();
                node.getProductBOP().getManufacturingBOMs().clear();
                node.getProductBOP().getManufacturingBOMs()
                        .addAll(enriched);
            }
            if (!node.getChildren().isEmpty()) {
                enrichNodes(node.getChildren());
            }
        }
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}