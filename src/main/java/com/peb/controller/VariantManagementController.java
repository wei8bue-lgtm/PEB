package com.peb.controller;

import com.peb.model.variant.OptionClass;
import com.peb.model.variant.ProductConfiguration;
import com.peb.model.variant.VariantOption;
import com.peb.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * MVC controller for the Variant & Configuration Management UI.
 *
 * Routes:
 *   GET  /variant/options                  → Option-Class list & management
 *   POST /variant/options/classes          → Create new OptionClass
 *   POST /variant/options/classes/{id}/add → Add VariantOption to class
 *   GET  /variant/configurations           → List all ProductConfigurations
 *   GET  /variant/configurations/new       → Create-configuration form
 *   POST /variant/configurations           → Save new configuration
 *   GET  /variant/configurations/{id}      → View/edit a configuration
 *   POST /variant/configurations/{id}/options → Update selected options
 *   GET  /variant/configurations/{id}/resolve → Show resolved 100% BOM
 */
@Controller
@RequestMapping("/variant")
@RequiredArgsConstructor
@Slf4j
public class VariantManagementController {

    private final ConfigurationService configurationService;

    // ══════════════════════════════════════════════════════════════════════
    // Option Classes & Options
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('READ')")
    public String optionClasses(Model model) {
        model.addAttribute("optionClasses", configurationService.getAllOptionClasses());
        model.addAttribute("newOptionClass", new OptionClass());
        return "variant/option-class-list";
    }

    @PostMapping("/options/classes")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createOptionClass(
            @ModelAttribute OptionClass optionClass,
            RedirectAttributes redirectAttributes) {
        configurationService.saveOptionClass(optionClass);
        redirectAttributes.addFlashAttribute("success", "Option class '" + optionClass.getId() + "' created.");
        return "redirect:/variant/options";
    }

    @PostMapping("/options/classes/{classId}/add")
    @PreAuthorize("hasAuthority('WRITE')")
    public String addOption(
            @PathVariable String classId,
            @ModelAttribute VariantOption option,
            RedirectAttributes redirectAttributes) {
        try {
            configurationService.addOption(classId, option);
            redirectAttributes.addFlashAttribute("success", "Option '" + option.getCode() + "' added.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/variant/options";
    }

    @PostMapping("/options/classes/{classId}/delete")
    @PreAuthorize("hasAuthority('WRITE')")
    public String deleteOptionClass(
            @PathVariable String classId,
            RedirectAttributes redirectAttributes) {
        configurationService.deleteOptionClass(classId);
        redirectAttributes.addFlashAttribute("success", "Option class '" + classId + "' deleted.");
        return "redirect:/variant/options";
    }

    @PostMapping("/options/{optionId}/delete")
    @PreAuthorize("hasAuthority('WRITE')")
    public String deleteOption(@PathVariable Long optionId, RedirectAttributes redirectAttributes) {
        configurationService.deleteOption(optionId);
        redirectAttributes.addFlashAttribute("success", "Option deleted.");
        return "redirect:/variant/options";
    }

    // ══════════════════════════════════════════════════════════════════════
    // Product Configurations
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/configurations")
    @PreAuthorize("hasAuthority('READ')")
    public String listConfigurations(Model model) {
        model.addAttribute("configurations", configurationService.getAllConfigurations());
        return "variant/configuration-list";
    }

    @GetMapping("/configurations/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newConfigurationForm(Model model) {
        model.addAttribute("config", new ProductConfiguration());
        model.addAttribute("optionClasses", configurationService.getAllOptionClasses());
        return "variant/configuration-form";
    }

    @PostMapping("/configurations")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createConfiguration(
            @ModelAttribute ProductConfiguration config,
            @RequestParam(value = "selectedOptionIds", required = false) List<Long> selectedOptionIds,
            RedirectAttributes redirectAttributes) {

        ProductConfiguration saved = configurationService.saveConfiguration(config);
        if (selectedOptionIds != null && !selectedOptionIds.isEmpty()) {
            configurationService.updateSelectedOptions(saved.getId(), selectedOptionIds);
        }
        redirectAttributes.addFlashAttribute("success", "Configuration '" + saved.getName() + "' created.");
        return "redirect:/variant/configurations/" + saved.getId();
    }

    @GetMapping("/configurations/{id}")
    @PreAuthorize("hasAuthority('READ')")
    public String viewConfiguration(@PathVariable Long id, Model model) {
        ProductConfiguration config = configurationService.getConfiguration(id);
        List<String>         errors = configurationService.validateConfiguration(id);
        model.addAttribute("config", config);
        model.addAttribute("optionClasses", configurationService.getAllOptionClasses());
        model.addAttribute("validationErrors", errors);
        return "variant/configuration-form";
    }

    @PostMapping("/configurations/{id}/options")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateOptions(
            @PathVariable Long id,
            @RequestParam(value = "selectedOptionIds", required = false) List<Long> selectedOptionIds,
            RedirectAttributes redirectAttributes) {
        configurationService.updateSelectedOptions(id, selectedOptionIds != null ? selectedOptionIds : List.of());
        redirectAttributes.addFlashAttribute("success", "Options updated.");
        return "redirect:/variant/configurations/" + id;
    }

    @GetMapping("/configurations/{id}/resolve")
    @PreAuthorize("hasAuthority('READ')")
    public String resolveConfiguration(
            @PathVariable Long id,
            @RequestParam(required = false) Long revisionId,
            Model model) {
        ProductConfiguration config = configurationService.getConfiguration(id);
        Long effectiveRevision      = revisionId != null ? revisionId : config.getRootRevisionId();

        if (config.getRootObjectId() != null) {
            try {
                model.addAttribute("resolvedTree",
                        configurationService.resolve150BOM(
                                config.getRootObjectId(), effectiveRevision, id));
            } catch (Exception e) {
                model.addAttribute("resolveError", e.getMessage());
            }
        }

        model.addAttribute("config", config);
        model.addAttribute("validationErrors", configurationService.validateConfiguration(id));
        return "variant/configuration-resolve";
    }

    @PostMapping("/configurations/{id}/delete")
    @PreAuthorize("hasAuthority('WRITE')")
    public String deleteConfiguration(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        configurationService.deleteConfiguration(id);
        redirectAttributes.addFlashAttribute("success", "Configuration deleted.");
        return "redirect:/variant/configurations";
    }
}
