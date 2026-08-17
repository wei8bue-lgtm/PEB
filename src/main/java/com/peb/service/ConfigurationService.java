package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.VariantResolutionConflictException;
import com.peb.model.ebom.EBOMObject;
import com.peb.model.structure.StructureNode;
import com.peb.model.variant.OptionClass;
import com.peb.model.variant.ProductConfiguration;
import com.peb.model.variant.VariantOption;
import com.peb.service.EBOMService;
import com.peb.repository.OptionClassRepository;
import com.peb.repository.ProductConfigurationRepository;
import com.peb.repository.VariantOptionRepository;
import com.peb.util.BomRelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Variant & Configuration Management Service.
 *
 * Core responsibilities:
 *   1. CRUD for OptionClass and VariantOption
 *   2. CRUD for ProductConfiguration
 *   3. Configuration rule evaluation (simple expression engine)
 *   4. 150% → 100% BOM resolution (filter StructureNode tree)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {

    private final OptionClassRepository         optionClassRepository;
    private final VariantOptionRepository       variantOptionRepository;
    private final ProductConfigurationRepository configurationRepository;
    private final StructureService              structureService;
    private final EBOMService                   ebomService;

    // ══════════════════════════════════════════════════════════════════════
    // Option Class CRUD
    // ══════════════════════════════════════════════════════════════════════

    public List<OptionClass> getAllOptionClasses() {
        return optionClassRepository.findAllByOrderByIdAsc();
    }

    public OptionClass getOptionClass(String id) {
        return optionClassRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("OptionClass not found: " + id));
    }

    @Transactional
    public OptionClass saveOptionClass(OptionClass optionClass) {
        return optionClassRepository.save(optionClass);
    }

    @Transactional
    public void deleteOptionClass(String id) {
        OptionClass optionClass = getOptionClass(id);
        Set<Long> optionIds = optionClass.getOptions().stream()
                .map(VariantOption::getId)
                .collect(Collectors.toSet());

        if (!optionIds.isEmpty()) {
            detachOptionsFromConfigurations(optionIds);
        }

        optionClassRepository.delete(optionClass);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Variant Option CRUD
    // ══════════════════════════════════════════════════════════════════════

    public List<VariantOption> getOptionsForClass(String optionClassId) {
        return variantOptionRepository
                .findByOptionClassIdOrderBySortOrderAscCodeAsc(optionClassId);
    }

    @Transactional
    public VariantOption addOption(String optionClassId, VariantOption option) {
        OptionClass oc = getOptionClass(optionClassId);
        if (variantOptionRepository.existsByOptionClassIdAndCode(optionClassId, option.getCode())) {
            throw new IllegalArgumentException(
                    "Option code '" + option.getCode() + "' already exists in class " + optionClassId);
        }
        option.setOptionClass(oc);
        return variantOptionRepository.save(option);
    }

    @Transactional
    public void deleteOption(Long optionId) {
        detachOptionsFromConfigurations(Set.of(optionId));
        variantOptionRepository.deleteById(optionId);
    }

    private void detachOptionsFromConfigurations(Set<Long> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return;
        }

        List<ProductConfiguration> configurations = configurationRepository.findAll();
        for (ProductConfiguration configuration : configurations) {
            boolean changed = configuration.getSelectedOptions()
                    .removeIf(option -> option.getId() != null && optionIds.contains(option.getId()));
            if (changed) {
                configurationRepository.save(configuration);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Product Configuration CRUD
    // ══════════════════════════════════════════════════════════════════════

    public List<ProductConfiguration> getAllConfigurations() {
        return configurationRepository.findAll();
    }

    public List<ProductConfiguration> getConfigurationsForObject(String rootObjectId) {
        return configurationRepository.findByRootObjectIdOrderByCreatedDateDesc(rootObjectId);
    }

    public ProductConfiguration getConfiguration(Long id) {
        return configurationRepository.findByIdWithOptions(id)
                .orElseThrow(() -> new ObjectNotFoundException("ProductConfiguration not found: " + id));
    }

    @Transactional
    public ProductConfiguration saveConfiguration(ProductConfiguration config) {
        return configurationRepository.save(config);
    }

    @Transactional
    public ProductConfiguration updateSelectedOptions(Long configId, List<Long> optionIds) {
        ProductConfiguration config = getConfiguration(configId);
        List<VariantOption> options = variantOptionRepository.findAllById(optionIds);
        config.setSelectedOptions(options);
        return configurationRepository.save(config);
    }

    @Transactional
    public void deleteConfiguration(Long id) {
        configurationRepository.deleteById(id);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 150% BOM → 100% BOM Resolution
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Resolves the 150% BOM rooted at {@code rootObjectId} for the given
     * {@code configuration} and returns a filtered {@link StructureNode} tree
     * that contains only the items applicable to that configuration.
     *
     * @param rootObjectId  ID of the root eBOM object (e.g. a Machine)
     * @param revisionId    Revision of the root object; null = latest
     * @param configId      ID of the ProductConfiguration to apply
     * @return              Filtered tree (deep copy with non-matching nodes removed)
     */
    @Transactional(readOnly = true)
    public StructureNode resolve150BOM(String rootObjectId, Long revisionId, Long configId) {
        ProductConfiguration config   = getConfiguration(configId);
        Set<String> selectedTokens    = buildTokenSet(config.getSelectedOptions());
        Long effectiveRevisionId      = resolveEffectiveRevisionId(rootObjectId, revisionId);
        StructureNode fullTree        = structureService.getStructureTree(rootObjectId, effectiveRevisionId);

        log.info("Resolving 150% BOM for {} rev={} with config '{}' (tokens: {})",
                rootObjectId, effectiveRevisionId, config.getName(), selectedTokens);

        return filterNode(fullTree, selectedTokens);
    }

    private Long resolveEffectiveRevisionId(String rootObjectId, Long revisionId) {
        if (revisionId != null) {
            return revisionId;
        }

        EBOMObject rootObject = ebomService.getEBOMObject(rootObjectId);
        if (rootObject.getLatestRevision() == null) {
            throw new ObjectNotFoundException(
                    "Root object has no revision: " + rootObjectId);
        }

        return rootObject.getLatestRevision().getRevisionId();
    }

    /**
     * Validates that a configuration satisfies all mandatory OptionClasses.
     *
     * @return List of validation error messages (empty = valid)
     */
    public List<String> validateConfiguration(Long configId) {
        ProductConfiguration config   = getConfiguration(configId);
        Set<String> selectedClassIds  = config.getSelectedOptions().stream()
                .map(o -> o.getOptionClass().getId())
                .collect(Collectors.toSet());

        List<String> errors = new ArrayList<>();
        for (OptionClass oc : optionClassRepository.findAll()) {
            if (oc.isMandatory() && !selectedClassIds.contains(oc.getId())) {
                errors.add("Mandatory option class '" + oc.getName()
                        + "' (" + oc.getId() + ") has no option selected.");
            }
            if (oc.isSingleSelect()) {
                long count = config.getSelectedOptions().stream()
                        .filter(o -> o.getOptionClass().getId().equals(oc.getId()))
                        .count();
                if (count > 1) {
                    errors.add("Option class '" + oc.getName()
                            + "' is single-select but " + count + " options were chosen.");
                }
            }
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Rule Engine
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Evaluates a configRule expression against the set of selected option tokens.
     *
     * Supported syntax:
     *   <TOKEN>                 →  ENGINE_TYPE=V8
     *   <EXPR> AND <EXPR>       →  ENGINE_TYPE=V8 AND COLOR=RED
     *   <EXPR> OR  <EXPR>       →  COLOR=RED OR COLOR=BLUE
     *   ( <EXPR> )              →  (ENGINE_TYPE=V6 OR ENGINE_TYPE=V8) AND OPTION_AC=TRUE
     *
     * A null or blank rule always evaluates to TRUE (ALWAYS semantics).
     *
     * @param rule           Expression string from StructureRelation.configRule
     * @param selectedTokens Set of "CLASSID=CODE" strings from the configuration
     * @return               true if the node should be included
     */
    public boolean evaluateRule(String rule, Set<String> selectedTokens) {
        if (rule == null || rule.isBlank()) {
            return true;
        }
        try {
            return parseExpression(rule.trim(), selectedTokens);
        } catch (Exception e) {
            log.warn("Could not evaluate configRule '{}': {} – defaulting to INCLUDE", rule, e.getMessage());
            return true;
        }
    }

    // ── Internal parser ────────────────────────────────────────────────────

    /** Builds the "CLASS=CODE" token set from the selected options. */
    private Set<String> buildTokenSet(List<VariantOption> options) {
        return options.stream()
                .map(VariantOption::toRuleToken)
                .collect(Collectors.toSet());
    }

    /**
     * Recursive descent parser for the rule expression language.
     * Grammar:
     *   expr    ::= andExpr ( "OR" andExpr )*
     *   andExpr ::= atom   ( "AND" atom   )*
     *   atom    ::= "(" expr ")" | TOKEN
     *   TOKEN   ::= [A-Z0-9_]+ "=" [A-Z0-9_]+
     */
    private boolean parseExpression(String expr, Set<String> tokens) {
        // Split on top-level OR
        List<String> orParts = splitOnTopLevel(expr, "OR");
        if (orParts.size() > 1) {
            for (String part : orParts) {
                if (parseExpression(part.trim(), tokens)) return true;
            }
            return false;
        }
        // Split on top-level AND
        List<String> andParts = splitOnTopLevel(expr, "AND");
        if (andParts.size() > 1) {
            for (String part : andParts) {
                if (!parseExpression(part.trim(), tokens)) return false;
            }
            return true;
        }
        // Parenthesised group
        if (expr.startsWith("(") && expr.endsWith(")")) {
            return parseExpression(expr.substring(1, expr.length() - 1).trim(), tokens);
        }
        // Leaf token: CLASS=VALUE
        return tokens.contains(expr);
    }

    /**
     * Splits {@code expr} on occurrences of {@code op} that are not inside
     * parentheses.  Returns a list with at least one element.
     */
    private List<String> splitOnTopLevel(String expr, String op) {
        List<String> parts  = new ArrayList<>();
        int          depth  = 0;
        int          start  = 0;
        String       padOp  = " " + op + " ";

        for (int i = 0; i <= expr.length() - padOp.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (depth == 0 && expr.startsWith(padOp, i)) {
                parts.add(expr.substring(start, i));
                i    += padOp.length() - 1;
                start = i + 1;
            }
        }
        parts.add(expr.substring(start));
        return parts;
    }

    // ── Tree filter ─────────────────────────────────────────────────────────

    /**
     * Returns a filtered copy of the tree.  A node is included if its
     * configRule evaluates to true for the given tokens.
     * The root node is always included (it has no configRule on a relation).
     */
    private StructureNode filterNode(StructureNode node, Set<String> selectedTokens) {
        StructureNode filtered = shallowCopy(node);

        // Group children by alternativeGroup
        Map<String, List<StructureNode>> altGroups   = new LinkedHashMap<>();
        List<StructureNode>              ungrouped   = new ArrayList<>();

        for (StructureNode child : node.getChildren()) {
            if (child.getAlternativeGroup() != null && !child.getAlternativeGroup().isBlank()) {
                altGroups.computeIfAbsent(child.getAlternativeGroup(), k -> new ArrayList<>())
                         .add(child);
            } else {
                ungrouped.add(child);
            }
        }

        // Process ungrouped children
        for (StructureNode child : ungrouped) {
            if (evaluateRule(child.getConfigRule(), selectedTokens)) {
                filtered.getChildren().add(filterNode(child, selectedTokens));
            }
        }

        // Process alternative groups: include the first child whose rule matches
        for (Map.Entry<String, List<StructureNode>> entry : altGroups.entrySet()) {
            String groupName = entry.getKey();
            List<StructureNode> group = entry.getValue();

            List<StructureNode> matches = group.stream()
                    .filter(candidate -> evaluateRule(candidate.getConfigRule(), selectedTokens))
                    .toList();

            if (matches.isEmpty()) {
                continue;
            }

            List<StructureNode> sortedMatches = new ArrayList<>(matches);
            sortedMatches.sort(Comparator
                    .comparingInt(this::resolveAlternativePriority)
                    .thenComparingInt(this::resolvePositionSortOrder)
                    .thenComparing(candidateNode -> candidateNode.getObjectId() != null ? candidateNode.getObjectId() : ""));

            StructureNode selected = sortedMatches.get(0);
            int selectedPriority = resolveAlternativePriority(selected);

            List<StructureNode> samePriorityMatches = sortedMatches.stream()
                    .filter(candidateNode -> resolveAlternativePriority(candidateNode) == selectedPriority)
                    .toList();

            if (samePriorityMatches.size() > 1) {
                List<String> candidates = samePriorityMatches.stream()
                        .map(candidateNode -> formatAlternativeCandidate(candidateNode))
                        .toList();
                log.warn("Ambiguous alternative resolution in group '{}': {}", groupName, String.join(", ", candidates));
                throw new VariantResolutionConflictException(groupName, selectedPriority, candidates);
            }

            if (sortedMatches.size() > 1) {
                log.warn("Multiple matching alternatives in group '{}' resolved by priority. Selected: {} (priority {}), candidates: {}",
                        groupName,
                        selected.getObjectId(),
                        selectedPriority,
                        sortedMatches.stream()
                            .map(candidateNode -> (candidateNode.getObjectId() != null ? candidateNode.getObjectId() : "UNKNOWN")
                                + "@" + resolveAlternativePriority(candidateNode))
                                .collect(Collectors.joining(", ")));
            }

            filtered.getChildren().add(filterNode(selected, selectedTokens));
        }

        return filtered;
    }

    private int resolveAlternativePriority(StructureNode node) {
        return node.getAlternativePriority() != null ? node.getAlternativePriority() : 1000;
    }

    private int resolvePositionSortOrder(StructureNode node) {
        if (node.getPositionNumber() == null || node.getPositionNumber().isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(node.getPositionNumber().trim());
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private String formatAlternativeCandidate(StructureNode node) {
        String objectId = node.getObjectId() != null ? node.getObjectId() : "UNKNOWN";
        int priority = resolveAlternativePriority(node);
        String position = node.getPositionNumber() != null ? node.getPositionNumber() : "-";
        String rule = node.getConfigRule() != null && !node.getConfigRule().isBlank() ? node.getConfigRule() : "<no rule>";
        return objectId + " [priority=" + priority + ", pos=" + position + ", rule=" + rule + "]";
    }

    private StructureNode shallowCopy(StructureNode src) {
        StructureNode copy = new StructureNode();
        copy.setObjectId(src.getObjectId());
        copy.setRevisionId(src.getRevisionId());
        copy.setName(src.getName());
        copy.setRevision(src.getRevision());
        copy.setReleaseStatus(src.getReleaseStatus());
        copy.setObjectType(src.getObjectType());
        copy.setQuantity(src.getQuantity());
        copy.setPositionNumber(src.getPositionNumber());
        copy.setReleased(src.isReleased());
        copy.setRelationId(src.getRelationId());
        copy.setReferenceDesignator(src.getReferenceDesignator());
        copy.setBmk(src.getBmk());
        copy.setRelationType(src.getRelationType());
        copy.setConfigRule(src.getConfigRule());
        copy.setAlternativeGroup(src.getAlternativeGroup());
        copy.setAlternativePriority(src.getAlternativePriority());
        return copy;
    }
}
