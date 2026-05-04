package com.peb.controller;

import com.peb.dto.EBOMObjectDTO;
import com.peb.model.ebom.*;
import com.peb.service.EBOMService;
import com.peb.service.DesignService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for eBOM operations
 */
@Controller
@RequestMapping("/ebom")
@RequiredArgsConstructor
public class EBOMController {

    private final EBOMService ebomService;
    private final DesignService designService;

    @GetMapping
    public String listEBOM(Model model) {
        model.addAttribute("machines", ebomService.getAllMachines());
        model.addAttribute("systems", ebomService.getAllSystems());
        model.addAttribute("subsystems", ebomService.getAllSubsystems());
        model.addAttribute("modules", ebomService.getAllModules());
        model.addAttribute("submodules", ebomService.getAllSubmodules());
        model.addAttribute("parts", ebomService.getAllParts());
        model.addAttribute("auxiliaries", ebomService.getAllAuxiliaries());
        return "ebom-list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newEBOMForm(Model model) {
        model.addAttribute("ebomObject", new EBOMObjectDTO());
        
        // Filter out DESIGN and DRAWING from ObjectType enum
        List<ObjectType> ebomTypes = Arrays.stream(ObjectType.values())
            .filter(type -> type != ObjectType.DESIGN && type != ObjectType.DRAWING)
            .collect(Collectors.toList());
        
        model.addAttribute("objectTypes", ebomTypes);
        model.addAttribute("designs", designService.getAllDesigns());
        return "ebom-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String createEBOM(@ModelAttribute EBOMObjectDTO ebomDTO, Model model) {
        try {
            EBOMObject ebomObject = ebomService.createEBOMObject(
                ebomDTO.getObjectType(),
                ebomDTO.getName(),
                ebomDTO.getDepartment(),
                ebomDTO.getMass(),
                ebomDTO.getMaterial(),
                ebomDTO.getSupplier(),
                ebomDTO.getLinkedDesignId()
            );
            return "redirect:/ebom/" + ebomObject.getId();
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create eBOM object: " + e.getMessage());
            model.addAttribute("ebomObject", ebomDTO);
            
            // Re-add objectTypes and designs on error
            List<ObjectType> ebomTypes = Arrays.stream(ObjectType.values())
                .filter(type -> type != ObjectType.DESIGN && type != ObjectType.DRAWING)
                .collect(Collectors.toList());
            model.addAttribute("objectTypes", ebomTypes);
            model.addAttribute("designs", designService.getAllDesigns());
            
            return "ebom-form";
        }
    }

    @GetMapping("/{id}")
    public String viewEBOM(@PathVariable String id, Model model) {
        EBOMObject ebomObject = ebomService.getEBOMObject(id);
        model.addAttribute("ebomObject", ebomObject);
        
        // Get linked design if exists
        if (ebomObject.getLinkedDesignId() != null && !ebomObject.getLinkedDesignId().isEmpty()) {
            try {
                model.addAttribute("linkedDesign", designService.getDesign(ebomObject.getLinkedDesignId()));
            } catch (Exception e) {
                // Design not found or deleted
            }
        }
        
        return "ebom-view";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editEBOMForm(@PathVariable String id, Model model) {
        EBOMObject ebomObject = ebomService.getEBOMObject(id);
        EBOMObjectDTO dto = new EBOMObjectDTO();
        dto.setObjectType(ebomObject.getObjectType());
        dto.setName(ebomObject.getName());
        dto.setDepartment(ebomObject.getDepartment());
        dto.setMass(ebomObject.getMass());
        dto.setMaterial(ebomObject.getMaterial());
        dto.setSupplier(ebomObject.getSupplier());
        dto.setLinkedDesignId(ebomObject.getLinkedDesignId());
        
        model.addAttribute("ebomObject", dto);
        model.addAttribute("ebomId", id);
        model.addAttribute("objectType", ebomObject.getObjectType());
        model.addAttribute("designs", designService.getAllDesigns());
        return "ebom-edit";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateEBOM(@PathVariable String id, @ModelAttribute EBOMObjectDTO ebomDTO) {
        ebomService.updateEBOMObject(id, ebomDTO.getName(), ebomDTO.getDepartment(),
                                     ebomDTO.getMass(), ebomDTO.getMaterial(), ebomDTO.getSupplier(),
                                     ebomDTO.getLinkedDesignId());
        return "redirect:/ebom/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteEBOM(@PathVariable String id) {
        ebomService.deleteEBOMObject(id);
        return "redirect:/ebom";
    }
}