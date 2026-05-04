package com.peb.controller;

import com.peb.dto.DesignDTO;
import com.peb.model.cad.Design;
import com.peb.service.DesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Design operations
 */
@Controller
@RequestMapping("/designs")
@RequiredArgsConstructor
public class DesignController {

    private final DesignService designService;

    @GetMapping
    public String listDesigns(Model model) {
        List<Design> designs = designService.getAllDesigns();
        model.addAttribute("designs", designs);
        return "design-list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newDesignForm(Model model) {
        model.addAttribute("design", new DesignDTO());
        return "design-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String createDesign(@ModelAttribute DesignDTO designDTO, Model model) {
        try {
            Design design = designService.createDesign(
                designDTO.getName(),
                designDTO.getDepartment()
            );
            return "redirect:/designs/" + design.getId();
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create design: " + e.getMessage());
            model.addAttribute("design", designDTO);
            return "design-form";
        }
    }

    @GetMapping("/{id}")
    public String viewDesign(@PathVariable String id, Model model) {
        Design design = designService.getDesign(id);
        model.addAttribute("design", design);
        return "design-view";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editDesignForm(@PathVariable String id, Model model) {
        Design design = designService.getDesign(id);
        DesignDTO dto = new DesignDTO();
        dto.setName(design.getName());
        dto.setDepartment(design.getDepartment());
        dto.setMass(design.getMass());
        dto.setMaterial(design.getMaterial());
        model.addAttribute("design", dto);
        model.addAttribute("designId", id);
        return "design-edit";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateDesign(@PathVariable String id, @ModelAttribute DesignDTO designDTO) {
        designService.updateDesign(id, designDTO.getName(), designDTO.getDepartment(), 
                                   designDTO.getMass(), designDTO.getMaterial());
        return "redirect:/designs/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteDesign(@PathVariable String id) {
        designService.deleteDesign(id);
        return "redirect:/designs";
    }
}