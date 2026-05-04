package com.peb.service;

import com.peb.exception.StructureException;
import com.peb.model.cad.Design;
import com.peb.model.cad.Drawing;
import com.peb.model.ebom.*;
import com.peb.model.structure.StructureNode;
import com.peb.model.structure.StructureRelation;
import com.peb.repository.*;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing structure relationships
 * Supports both CAD Design and eBOM structures with validation rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StructureService {

    private final StructureRelationRepository structureRelationRepository;
    private final EBOMService ebomService;
    private final DesignService designService;
    private final DrawingService drawingService;

    @Transactional
    public StructureRelation addToStructure(String parentId, Long parentRevisionId,
                                           String childId, Long childRevisionId,
                                           Integer quantity, String positionNumber) {
        // Validate structure rules
        validateStructureRelation(parentId, childId);

        // Check if relation already exists
        List<StructureRelation> existing = structureRelationRepository
            .findByParentIdAndParentRevisionId(parentId, parentRevisionId);
        
        boolean alreadyExists = existing.stream()
            .anyMatch(rel -> rel.getChildId().equals(childId) && 
                           rel.getChildRevisionId().equals(childRevisionId));
        
        if (alreadyExists) {
            throw new StructureException("This child is already in the structure");
        }

        StructureRelation relation = new StructureRelation();
        relation.setParentId(parentId);
        relation.setParentRevisionId(parentRevisionId);
        relation.setChildId(childId);
        relation.setChildRevisionId(childRevisionId);
        relation.setQuantity(quantity != null ? quantity : 1);
        relation.setPositionNumber(positionNumber);

        log.info("Added structure relation: {} -> {}", parentId, childId);
        return structureRelationRepository.save(relation);
    }

    @Transactional(readOnly = true)
    public StructureNode getStructureTree(String objectId, Long revisionId) {
        return buildStructureNode(objectId, revisionId, 0);
    }

    private StructureNode buildStructureNode(String objectId, Long revisionId, int depth) {
        if (depth > 20) { // Prevent infinite recursion
            throw new StructureException("Structure depth exceeds maximum (20 levels)");
        }

        StructureNode node = new StructureNode();
        node.setObjectId(objectId);
        node.setRevisionId(revisionId);

        // Determine object type and get details
        String prefix = objectId.substring(0, 2);
        
        try {
            switch (prefix) {
                case "DS" -> {
                    Design design = designService.getDesign(objectId);
                    node.setName(design.getName());
                    node.setObjectType("Design");
                    if (design.getLatestRevision() != null) {
                        node.setRevision(design.getLatestRevision().getRevision());
                        node.setReleaseStatus(design.getLatestRevision().getReleaseStatus().name());
                        node.setReleased(design.getLatestRevision().getReleaseStatus() == ReleaseStatus.RELEASED);
                    }
                }
                case "DR" -> {
                    Drawing drawing = drawingService.getDrawing(objectId);
                    node.setName(drawing.getName());
                    node.setObjectType("Drawing");
                    if (drawing.getLatestRevision() != null) {
                        node.setRevision(drawing.getLatestRevision().getRevision());
                        node.setReleaseStatus(drawing.getLatestRevision().getReleaseStatus().name());
                        node.setReleased(drawing.getLatestRevision().getReleaseStatus() == ReleaseStatus.RELEASED);
                    }
                }
                default -> {
                    EBOMObject ebomObject = ebomService.getEBOMObject(objectId);
                    node.setName(ebomObject.getName());
                    node.setObjectType(ebomObject.getObjectType().getDisplayName());
                    if (ebomObject.getLatestRevision() != null) {
                        node.setRevision(ebomObject.getLatestRevision().getRevision());
                        node.setReleaseStatus(ebomObject.getLatestRevision().getReleaseStatus().name());
                        node.setReleased(ebomObject.getLatestRevision().getReleaseStatus() == ReleaseStatus.RELEASED);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting object details for {}: {}", objectId, e.getMessage());
            node.setName("Unknown");
            node.setObjectType("Unknown");
        }

        // Get children
        List<StructureRelation> children = structureRelationRepository
            .findByParentIdAndParentRevisionId(objectId, revisionId);

        for (StructureRelation child : children) {
            StructureNode childNode = buildStructureNode(child.getChildId(), child.getChildRevisionId(), depth + 1);
            childNode.setQuantity(child.getQuantity());
            childNode.setPositionNumber(child.getPositionNumber());
            node.getChildren().add(childNode);
        }

        return node;
    }

    private void validateStructureRelation(String parentId, String childId) {
        String parentPrefix = parentId.substring(0, 2);
        String childPrefix = childId.substring(0, 2);

        // Validate based on parent type
        switch (parentPrefix) {
            case "MA" -> { // Machine can contain: System, Part, Auxiliary
                if (!childPrefix.equals("SY") && !childPrefix.equals("PT") && 
                    !childPrefix.equals("AX") && !childPrefix.equals("DR")) {
                    throw new StructureException(
                        "Machine can only contain System, Part, Auxiliary, or Drawing. Cannot add: " + childPrefix);
                }
            }
            case "SY" -> { // System can contain: Subsystem, Part, Auxiliary
                if (!childPrefix.equals("SU") && !childPrefix.equals("PT") && 
                    !childPrefix.equals("AX") && !childPrefix.equals("DR")) {
                    throw new StructureException(
                        "System can only contain Subsystem, Part, Auxiliary, or Drawing. Cannot add: " + childPrefix);
                }
            }
            case "SU" -> { // Subsystem can contain: Module, Part, Auxiliary
                if (!childPrefix.equals("MO") && !childPrefix.equals("PT") && 
                    !childPrefix.equals("AX") && !childPrefix.equals("DR")) {
                    throw new StructureException(
                        "Subsystem can only contain Module, Part, Auxiliary, or Drawing. Cannot add: " + childPrefix);
                }
            }
            case "MO" -> { // Module can contain: Submodule, Part, Auxiliary
                if (!childPrefix.equals("MU") && !childPrefix.equals("PT") && 
                    !childPrefix.equals("AX") && !childPrefix.equals("DR")) {
                    throw new StructureException(
                        "Module can only contain Submodule, Part, Auxiliary, or Drawing. Cannot add: " + childPrefix);
                }
            }
            case "MU" -> { // Submodule can contain: Part, Auxiliary (leaf level)
                if (!childPrefix.equals("PT") && !childPrefix.equals("AX") && !childPrefix.equals("DR")) {
                    throw new StructureException(
                        "Submodule can only contain Part, Auxiliary, or Drawing. Cannot add: " + childPrefix);
                }
            }
            case "PT", "AX" -> { // Part and Auxiliary can contain: Drawings only
                if (!childPrefix.equals("DR")) {
                    throw new StructureException(
                        "Part/Auxiliary can only contain Drawing. Cannot add: " + childPrefix);
                }
            }
            case "DS" -> { // Design objects can be used in eBOM structure
                if (!childPrefix.equals("DS") && !childPrefix.equals("DR") && 
                    !childPrefix.equals("PT") && !childPrefix.equals("AX")) {
                    throw new StructureException(
                        "Design can contain Design, Drawing, Part, or Auxiliary. Cannot add: " + childPrefix);
                }
            }
            default -> throw new StructureException("Unknown parent type: " + parentPrefix);
        }
    }

    @Transactional(readOnly = true)
    public boolean canReleaseStructure(String objectId, Long revisionId) {
        StructureNode tree = getStructureTree(objectId, revisionId);
        return areAllChildrenReleased(tree);
    }

    private boolean areAllChildrenReleased(StructureNode node) {
        if (!node.isReleased()) {
            return false;
        }
        for (StructureNode child : node.getChildren()) {
            if (!areAllChildrenReleased(child)) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<StructureNode> getUnreleasedChildren(String objectId, Long revisionId) {
        List<StructureNode> unreleased = new ArrayList<>();
        StructureNode tree = getStructureTree(objectId, revisionId);
        collectUnreleased(tree, unreleased);
        return unreleased;
    }

    private void collectUnreleased(StructureNode node, List<StructureNode> unreleased) {
        if (!node.isReleased()) {
            unreleased.add(node);
        }
        for (StructureNode child : node.getChildren()) {
            collectUnreleased(child, unreleased);
        }
    }

    @Transactional
    public void removeFromStructure(Long relationId) {
        structureRelationRepository.deleteById(relationId);
        log.info("Removed structure relation: {}", relationId);
    }

    @Transactional(readOnly = true)
    public List<StructureRelation> getChildRelations(String parentId, Long parentRevisionId) {
        return structureRelationRepository.findByParentIdAndParentRevisionId(parentId, parentRevisionId);
    }

    @Transactional(readOnly = true)
    public List<String> getAllowedChildTypes(String parentId) {
        String parentPrefix = parentId.substring(0, 2);
        
        return switch (parentPrefix) {
            case "MA" -> List.of("SY", "PT", "AX", "DR");
            case "SY" -> List.of("SU", "PT", "AX", "DR");
            case "SU" -> List.of("MO", "PT", "AX", "DR");
            case "MO" -> List.of("MU", "PT", "AX", "DR");
            case "MU" -> List.of("PT", "AX", "DR");
            case "PT", "AX" -> List.of("DR");
            case "DS" -> List.of("DS", "DR", "PT", "AX");
            default -> List.of();
        };
    }
}