package com.peb.controller.api;

import com.peb.model.structure.StructureNode;
import com.peb.model.structure.StructureRelation;
import com.peb.model.variant.OptionClass;
import com.peb.model.variant.ProductConfiguration;
import com.peb.model.variant.VariantOption;
import com.peb.repository.StructureRelationRepository;
import com.peb.service.ConfigurationService;
import com.peb.util.BomRelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for Variant & Configuration Management (150% BOM).
 *
 * Base URL: /api/v1/variant
 *
 * Option Classes:
 *   GET    /api/v1/variant/option-classes                → list all
 *   POST   /api/v1/variant/option-classes                → create
 *   DELETE /api/v1/variant/option-classes/{id}           → delete
 *   GET    /api/v1/variant/option-classes/{id}/options   → list options
 *   POST   /api/v1/variant/option-classes/{id}/options   → add option
 *   DELETE /api/v1/variant/options/{optionId}            → delete option
 *
 * Configurations:
 *   GET    /api/v1/variant/configurations                → list all
 *   GET    /api/v1/variant/configurations?rootObjectId=X → filter by root
 *   POST   /api/v1/variant/configurations                → create
 *   GET    /api/v1/variant/configurations/{id}           → get one
 *   PUT    /api/v1/variant/configurations/{id}/options   → set selected options
 *   DELETE /api/v1/variant/configurations/{id}           → delete
 *
 * BOM Resolution:
 *   GET    /api/v1/variant/configurations/{id}/resolve   → resolve 150%→100% BOM tree
 *   GET    /api/v1/variant/configurations/{id}/validate  → validate completeness
 */
@RestController
@RequestMapping("/api/v1/variant")
@RequiredArgsConstructor
@Slf4j
public class ConfigurationApiController {

    private final ConfigurationService configurationService;
    private final StructureRelationRepository structureRelationRepository;

    // ══════════════════════════════════════════════════════════════════════
    // Option Classes
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/option-classes")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<OptionClass>> getAllOptionClasses() {
        return ResponseEntity.ok(configurationService.getAllOptionClasses());
    }

    @PostMapping("/option-classes")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<OptionClass> createOptionClass(
            @RequestBody OptionClass optionClass) {
        OptionClass saved = configurationService.saveOptionClass(optionClass);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/option-classes/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Void> deleteOptionClass(@PathVariable String id) {
        configurationService.deleteOptionClass(id);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Variant Options
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/option-classes/{classId}/options")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<VariantOption>> getOptions(@PathVariable String classId) {
        return ResponseEntity.ok(configurationService.getOptionsForClass(classId));
    }

    @PostMapping("/option-classes/{classId}/options")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> addOption(
            @PathVariable String classId,
            @RequestBody VariantOption option) {
        try {
            VariantOption saved = configurationService.addOption(classId, option);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/options/{optionId}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Void> deleteOption(@PathVariable Long optionId) {
        configurationService.deleteOption(optionId);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Product Configurations
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/configurations")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<ProductConfiguration>> listConfigurations(
            @RequestParam(required = false) String rootObjectId) {
        List<ProductConfiguration> list = rootObjectId != null
                ? configurationService.getConfigurationsForObject(rootObjectId)
                : configurationService.getAllConfigurations();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/configurations/{id}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<ProductConfiguration> getConfiguration(@PathVariable Long id) {
        return ResponseEntity.ok(configurationService.getConfiguration(id));
    }

    @PostMapping("/configurations")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<ProductConfiguration> createConfiguration(
            @RequestBody ProductConfiguration config) {
        ProductConfiguration saved = configurationService.saveConfiguration(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/configurations/{id}/options")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<ProductConfiguration> setSelectedOptions(
            @PathVariable Long id,
            @RequestBody List<Long> optionIds) {
        return ResponseEntity.ok(configurationService.updateSelectedOptions(id, optionIds));
    }

    @DeleteMapping("/configurations/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Void> deleteConfiguration(@PathVariable Long id) {
        configurationService.deleteConfiguration(id);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // BOM Resolution
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Resolves the 150% BOM tree to a 100% BOM for the given configuration.
     * Returns a filtered StructureNode tree.
     */
    @GetMapping("/configurations/{id}/resolve")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> resolveBOM(
            @PathVariable Long id,
            @RequestParam(required = false) Long revisionId) {
        try {
            ProductConfiguration config = configurationService.getConfiguration(id);
            if (config.getRootObjectId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Configuration has no rootObjectId set."));
            }
            Long effectiveRevisionId = revisionId != null ? revisionId : config.getRootRevisionId();
            StructureNode resolved   = configurationService.resolve150BOM(
                    config.getRootObjectId(), effectiveRevisionId, id);
            return ResponseEntity.ok(resolved);
        } catch (Exception e) {
            log.error("Error resolving 150% BOM for config {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validates that all mandatory option classes are covered by the configuration.
     * Returns a list of validation errors (empty array = valid).
     */
    @GetMapping("/configurations/{id}/validate")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<String>> validateConfiguration(@PathVariable Long id) {
        return ResponseEntity.ok(configurationService.validateConfiguration(id));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Variant fields on StructureRelation (inline edit)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Updates the variant/150%-BOM fields on a single StructureRelation.
     *
     * PATCH /api/v1/variant/relations/{relationId}/rule
     *
     * Body (all fields optional – only provided fields are updated):
     * {
     *   "relationType":     "OPTIONAL",
     *   "configRule":       "ENGINE_TYPE=V8",
    *   "alternativeGroup": "KUEHLER_GRUPPE",
    *   "alternativePriority": "10"
     * }
     */
    @PatchMapping("/relations/{relationId}/rule")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> updateVariantRule(
            @PathVariable Long relationId,
            @RequestBody  Map<String, String> body) {

        return structureRelationRepository.findById(relationId)
                .map(rel -> {
                    if (body.containsKey("relationType")) {
                        String rt = body.get("relationType");
                        try {
                            rel.setRelationType(rt == null || rt.isBlank()
                                    ? BomRelationType.ALWAYS
                                    : BomRelationType.valueOf(rt));
                        } catch (IllegalArgumentException e) {
                            return ResponseEntity.badRequest()
                                    .body(Map.of("error", "Invalid relationType: " + rt));
                        }
                    }
                    if (body.containsKey("configRule")) {
                        String rule = body.get("configRule");
                        rel.setConfigRule(rule == null || rule.isBlank() ? null : rule.trim());
                    }
                    if (body.containsKey("alternativeGroup")) {
                        String ag = body.get("alternativeGroup");
                        rel.setAlternativeGroup(ag == null || ag.isBlank() ? null : ag.trim());
                    }
                    if (body.containsKey("alternativePriority")) {
                        String priorityRaw = body.get("alternativePriority");
                        if (priorityRaw == null || priorityRaw.isBlank()) {
                            rel.setAlternativePriority(null);
                        } else {
                            try {
                                rel.setAlternativePriority(Integer.parseInt(priorityRaw.trim()));
                            } catch (NumberFormatException e) {
                                return ResponseEntity.badRequest()
                                        .body(Map.of("error", "Invalid alternativePriority: " + priorityRaw));
                            }
                        }
                    }
                    StructureRelation saved = structureRelationRepository.save(rel);
                    return ResponseEntity.ok((Object) saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
