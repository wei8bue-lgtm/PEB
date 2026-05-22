// src/main/java/com/peb/controller/product/PlantBOPController.java
package com.peb.controller.product;

import com.peb.model.product.*;
import com.peb.repository.product.*;
import com.peb.service.product.LineBalancingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/plant-bop")
@RequiredArgsConstructor
@Slf4j
public class PlantBOPController {

    private final PlantBOPRepository       plantBOPRepo;
    private final PlantBOPNodeRepository   nodeRepo;
    private final ProductBOPRepository     productBOPRepo;
    private final ManufacturingBOMRepository mfgBomRepo;
    private final LineBalancingService     balancingService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("plantBOPs", plantBOPRepo.findAll());
        return "plant-bop/list";
    }

// src/main/java/com/peb/controller/product/PlantBOPController.java
// detail() Methode anpassen:

@GetMapping("/{id}")
public String detail(@PathVariable Long id, Model model) {
    PlantBOP plantBOP = plantBOPRepo.findById(id)
            .orElseThrow();

    // Root-Knoten mit allen Details laden
    List<PlantBOPNode> rootNodes = nodeRepo
            .findByPlantBOPIdAndParentIsNullOrderBySortOrder(id);

    // ManufacturingBOM-Inhalte fuer jeden Knoten nachladen
    // (verhindert LazyInitializationException im Template)
    enrichNodes(rootNodes);

    model.addAttribute("plantBOP",    plantBOP);
    model.addAttribute("rootNodes",   rootNodes);
    model.addAttribute("nodeTypes",   PlantBOPNode.NodeType.values());
    model.addAttribute("availableProductBOPs",
            productBOPRepo.findAll());
    model.addAttribute("availableMfgBOMs",
            mfgBomRepo.findAll());
    model.addAttribute("allNodes",
            nodeRepo.findAllByPlantBOPIdWithDetails(id));

    return "plant-bop/detail";
}

/**
 * Laedt Inhalte von ProductBOP und ManufacturingBOM
 * fuer jeden Knoten nach (rekursiv).
 */
private void enrichNodes(List<PlantBOPNode> nodes) {
    for (PlantBOPNode node : nodes) {

        // ManufacturingBOM mit Details laden
        if (node.getManufacturingBOM() != null) {
            mfgBomRepo.findByIdWithDetails(
                    node.getManufacturingBOM().getId())
                    .ifPresent(node::setManufacturingBOM);
        }

        // ProductBOP: ManufacturingBOMs mit Details laden
        if (node.getProductBOP() != null) {
            List<ManufacturingBOM> enriched =
                    node.getProductBOP()
                        .getManufacturingBOMs()
                        .stream()
                        .map(mfg -> mfgBomRepo
                                .findByIdWithDetails(mfg.getId())
                                .orElse(mfg))
                        .toList();
            node.getProductBOP()
                .getManufacturingBOMs()
                .clear();
            node.getProductBOP()
                .getManufacturingBOMs()
                .addAll(enriched);
        }

        // Rekursiv fuer Kinder
        if (!node.getChildren().isEmpty()) {
            enrichNodes(node.getChildren());
        }
    }
}


    /**
     * Baut den Baum im Memory auf.
     * Weist jedem Knoten seine Kinder zu –
     * verhindert LazyInitializationException im Template.
     */
    private void buildTreeInMemory(List<PlantBOPNode> allNodes) {
        // Map: nodeId → node
        java.util.Map<Long, PlantBOPNode> nodeMap =
                new java.util.LinkedHashMap<>();
        for (PlantBOPNode node : allNodes) {
            // children Liste leeren und neu befüllen
            node.getChildren().clear();
            nodeMap.put(node.getId(), node);
        }

        // Kinder den Eltern zuweisen
        for (PlantBOPNode node : allNodes) {
            if (node.getParent() != null) {
                PlantBOPNode parent =
                        nodeMap.get(node.getParent().getId());
                if (parent != null
                        && !parent.getChildren()
                                  .contains(node)) {
                    parent.getChildren().add(node);
                }
            }
        }

        // Kinder sortieren
        for (PlantBOPNode node : allNodes) {
            node.getChildren().sort(
                    java.util.Comparator.comparingInt(
                            n -> n.getSortOrder() != null
                                    ? n.getSortOrder() : 0));
        }
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("plantBOP", new PlantBOP());
        return "plant-bop/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            @ModelAttribute PlantBOP plantBOP,
            RedirectAttributes ra) {
        PlantBOP saved = plantBOPRepo.save(plantBOP);
        ra.addFlashAttribute("successMessage",
                "✅ Plant BOP '" + saved.getName()
                + "' erstellt.");
        return "redirect:/plant-bop/" + saved.getId();
    }

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
            default -> { /* FOLDER – keine Referenz */ }
        }

        // Sort-Order
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

    @GetMapping("/{id}/balancing")
    public String balancing(@PathVariable Long id, Model model) {
        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow();
        LineBalancingService.LineBalancingResult result =
                balancingService.calculate(id, plantBOP.getName());
        model.addAttribute("result",   result);
        model.addAttribute("plantBOP", plantBOP);
        return "plant-bop/balancing";
    }
}