package com.peb.controller.cim;

import com.peb.model.cim.*;
import com.peb.model.ebom.EBOMObject;
import com.peb.model.structure.StructureNode;
import com.peb.model.variant.ProductConfiguration;
import com.peb.exception.VariantResolutionConflictException;
import com.peb.service.EBOMService;
import com.peb.service.ConfigurationService;
import com.peb.service.cim.CimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/cim")
@RequiredArgsConstructor
@Slf4j
public class CimPageController {

    private final CimService cimService;
    private final EBOMService ebomService;
    private final ConfigurationService configurationService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "top") String scope,
                       Model model) {
        boolean showAll = "all".equalsIgnoreCase(scope);
        model.addAttribute("cimObjects", showAll
                ? cimService.getAllCimObjects()
                : cimService.getTopLevelMachineCimObjects());
        model.addAttribute("listScope", showAll ? "all" : "top");
        model.addAttribute("maeObjects", ebomService.getAllEBOMObjects());
        return "cim/list";
    }

    @PostMapping("/from-mae")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createFromMae(@RequestParam String maeObjectId,
                                RedirectAttributes ra) {
        try {
            CimObject object = cimService.createFromMae(maeObjectId);
            ra.addFlashAttribute("successMessage", "CIM object " + object.getId() + " created/synced from " + maeObjectId);
            return "redirect:/cim/" + object.getId();
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cim";
        }
    }

    @GetMapping("/{cimId}")
    public String detail(@PathVariable String cimId,
                         @RequestParam(required = false) Long selectedConfigId,
                         Model model) {
        CimObject object = cimService.getCimObject(cimId);
        List<CimRevision> revisions = cimService.getRevisions(cimId);
        CimRevision latest = revisions.isEmpty() ? null : revisions.get(0);
        EBOMObject maeObject = resolveMae(object);
        List<ProductConfiguration> sourceConfigurations = maeObject != null
                ? configurationService.getConfigurationsForObject(maeObject.getId())
                : List.of();

        ProductConfiguration selectedConfiguration = resolveSelectedConfiguration(sourceConfigurations, selectedConfigId);
        List<String> configurationValidationErrors = selectedConfiguration != null
            ? configurationService.validateConfiguration(selectedConfiguration.getId())
            : List.of();

        StructureNode resolvedImportTree = null;
        String configurationResolveError = null;
        String configurationResolveConflictGroup = null;
        Integer configurationResolveConflictPriority = null;
        List<String> configurationResolveConflictCandidates = List.of();
        if (latest != null && selectedConfiguration != null) {
            try {
            resolvedImportTree = configurationService.resolve150BOM(
                latest.getSourceMaeObjectId(),
                latest.getSourceMaeRevisionId(),
                selectedConfiguration.getId());
            } catch (VariantResolutionConflictException ex) {
                configurationResolveError = ex.getMessage();
                configurationResolveConflictGroup = ex.getAlternativeGroup();
                configurationResolveConflictPriority = ex.getPriority();
                configurationResolveConflictCandidates = ex.getCandidates() != null ? ex.getCandidates() : List.of();
                log.warn("Configuration resolution conflict for CIM {} / config {}: {}",
                        cimId, selectedConfiguration.getId(), ex.getMessage());
            } catch (IllegalStateException ex) {
            configurationResolveError = ex.getMessage();
            log.warn("Configuration resolution conflict for CIM {} / config {}: {}",
                cimId, selectedConfiguration.getId(), ex.getMessage());
            }
        }

        model.addAttribute("cimObject", object);
        model.addAttribute("revisions", revisions);
        model.addAttribute("latestRevision", latest);
        model.addAttribute("importModes", CimImportMode.values());
        model.addAttribute("validations", latest != null
                ? cimService.getValidationResults(latest.getRevisionId())
                : List.of());
        model.addAttribute("manufacturingLinks", latest != null
                ? cimService.getManufacturingLinks(latest.getRevisionId())
                : List.of());
        model.addAttribute("structureTree", latest != null
            ? cimService.getStructureTree(latest.getRevisionId())
            : null);
        model.addAttribute("parentRelations", latest != null
            ? cimService.getParentRelations(latest.getRevisionId())
                : List.of());
        model.addAttribute("maeObject", maeObject);
        model.addAttribute("sourceConfigurations", sourceConfigurations);
        model.addAttribute("selectedConfigurationId", selectedConfiguration != null ? selectedConfiguration.getId() : null);
        model.addAttribute("selectedConfiguration", selectedConfiguration);
        model.addAttribute("configurationValidationErrors", configurationValidationErrors);
        model.addAttribute("configurationResolveError", configurationResolveError);
        model.addAttribute("configurationResolveConflictGroup", configurationResolveConflictGroup);
        model.addAttribute("configurationResolveConflictPriority", configurationResolveConflictPriority);
        model.addAttribute("configurationResolveConflictCandidates", configurationResolveConflictCandidates);
        model.addAttribute("resolvedImportTree", resolvedImportTree);

        return "cim/detail";
    }

    @PostMapping("/{cimId}/sync")
    @PreAuthorize("hasAuthority('WRITE')")
    public String sync(@PathVariable String cimId,
                       RedirectAttributes ra) {
        try {
            CimRevision revision = cimService.syncFromMae(cimId);
            ra.addFlashAttribute("successMessage", "CIM synced. Current revision: " + revision.getRevision());
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cim/" + cimId;
    }

    @PostMapping("/revisions/{revisionId}/review")
    @PreAuthorize("hasAuthority('WRITE')")
    public String review(@PathVariable Long revisionId,
                         @RequestParam String cimId,
                         RedirectAttributes ra) {
        try {
            cimService.submitForReview(revisionId);
            ra.addFlashAttribute("successMessage", "Revision submitted for review.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cim/" + cimId;
    }

    @PostMapping("/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('WRITE')")
    public String release(@PathVariable Long revisionId,
                          @RequestParam String cimId,
                          @RequestParam(required = false) String releaseComment,
                          RedirectAttributes ra) {
        try {
            cimService.releaseRevision(revisionId, releaseComment);
            ra.addFlashAttribute("successMessage", "Revision released.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cim/" + cimId;
    }

    @PostMapping("/revisions/{revisionId}/validate")
    @PreAuthorize("hasAuthority('WRITE')")
    public String validate(@PathVariable Long revisionId,
                           @RequestParam String cimId,
                           RedirectAttributes ra) {
        try {
            cimService.runValidation(revisionId);
            ra.addFlashAttribute("successMessage", "Validation finished.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cim/" + cimId;
    }

    @PostMapping("/revisions/{revisionId}/import")
    @PreAuthorize("hasAuthority('WRITE')")
    public String importToManufacturing(@PathVariable Long revisionId,
                                        @RequestParam String cimId,
                                        @RequestParam CimImportMode importMode,
                        @RequestParam(required = false) Long configurationId,
                                        @RequestParam(required = false) String productDataName,
                                        @RequestParam(required = false) String productBomName,
                                        @RequestParam(required = false) String manufacturingBomName,
                                        RedirectAttributes ra) {
        try {
            cimService.importToManufacturing(
                    revisionId,
                    importMode,
                configurationId,
                    productDataName,
                    productBomName,
                    manufacturingBomName
            );
            ra.addFlashAttribute("successMessage", "Import to Manufacturing successful.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cim/" + cimId;
    }

    private EBOMObject resolveMae(CimObject object) {
        try {
            return ebomService.getEBOMObject(object.getSourceMaeObjectId());
        } catch (Exception ex) {
            log.warn("Could not resolve MAE object {}", object.getSourceMaeObjectId());
            return null;
        }
    }

    private ProductConfiguration resolveSelectedConfiguration(List<ProductConfiguration> sourceConfigurations,
                                                              Long selectedConfigId) {
        if (selectedConfigId == null || sourceConfigurations.isEmpty()) {
            return null;
        }

        return sourceConfigurations.stream()
                .filter(config -> config.getId().equals(selectedConfigId))
                .findFirst()
                .orElse(null);
    }
}
