package com.peb.service;

import com.peb.exception.StructureException;
import com.peb.model.cad.Design;
import com.peb.model.cad.Drawing;
import com.peb.model.document.Document;
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
	private final DocumentService documentService;

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

	@Transactional(readOnly = true, noRollbackFor = com.peb.exception.ObjectNotFoundException.class)
	public StructureNode getStructureTree(String objectId, Long revisionId) {
		return buildStructureNode(objectId, revisionId, 0);
	}

	private StructureNode buildStructureNode(String objectId, Long revisionId, int depth) {
		if (depth > 20) {
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
						node.setReleaseStatus(design.getLatestRevision()
								.getReleaseStatus().name());
						node.setReleased(design.getLatestRevision()
								.getReleaseStatus() == ReleaseStatus.RELEASED);
					}
				}
				case "DR" -> {
					Drawing drawing = drawingService.getDrawing(objectId);
					node.setName(drawing.getName());
					node.setObjectType("Drawing");
					if (drawing.getLatestRevision() != null) {
						node.setRevision(drawing.getLatestRevision().getRevision());
						node.setReleaseStatus(drawing.getLatestRevision()
								.getReleaseStatus().name());
						node.setReleased(drawing.getLatestRevision()
								.getReleaseStatus() == ReleaseStatus.RELEASED);
					}
				}
				case "DC" -> {                                                    // ← NEU
					Document document = documentService.getDocument(objectId);
					node.setName(document.getName());
					node.setObjectType("Document");
					if (document.getLatestRevision() != null) {
						node.setRevision(document.getLatestRevision().getRevision());
						node.setReleaseStatus(document.getLatestRevision()
								.getReleaseStatus().name());
						node.setReleased(document.getLatestRevision()
								.getReleaseStatus() == ReleaseStatus.RELEASED);
					}
				}
				default -> {
					EBOMObject ebomObject = ebomService.getEBOMObject(objectId);
					node.setName(ebomObject.getName());
					node.setObjectType(ebomObject.getObjectType().getDisplayName());
					if (ebomObject.getLatestRevision() != null) {
						node.setRevision(ebomObject.getLatestRevision().getRevision());
						node.setReleaseStatus(ebomObject.getLatestRevision()
								.getReleaseStatus().name());
						node.setReleased(ebomObject.getLatestRevision()
								.getReleaseStatus() == ReleaseStatus.RELEASED);
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
			StructureNode childNode = buildStructureNode(
					child.getChildId(), child.getChildRevisionId(), depth + 1);

			// ── Relation-Metadaten auf den Node übertragen ──────────────────
			// FIX: Diese drei Zeilen fehlten – sie sind die Ursache des Fehlers
			childNode.setRelationId(child.getRelationId());                  // ← NEU
			childNode.setReferenceDesignator(child.getReferenceDesignator()); // ← NEU
			childNode.setBmk(child.getBmk());                                // ← NEU
			// ────────────────────────────────────────────────────────────────

			childNode.setQuantity(child.getQuantity());
			childNode.setPositionNumber(child.getPositionNumber());
			node.getChildren().add(childNode);
		}

		return node;
	}

		private void validateStructureRelation(String parentId, String childId) {
			String parentPrefix = parentId.substring(0, 2);
			String childPrefix = childId.substring(0, 2);

			switch (parentPrefix) {
				case "MA" -> {
					if (!childPrefix.equals("SY") && !childPrefix.equals("PT") &&
						!childPrefix.equals("AX") && !childPrefix.equals("DR") &&
						!childPrefix.equals("DC")) {                                    // ← NEU
						throw new StructureException(
							"Machine can only contain System, Part, Auxiliary, Drawing, or Document. Cannot add: " + childPrefix);
					}
				}
				case "SY" -> {
					if (!childPrefix.equals("SU") && !childPrefix.equals("PT") &&
						!childPrefix.equals("AX") && !childPrefix.equals("DR") &&
						!childPrefix.equals("DC")) {                                    // ← NEU
						throw new StructureException(
							"System can only contain Subsystem, Part, Auxiliary, Drawing, or Document. Cannot add: " + childPrefix);
					}
				}
				case "SU" -> {
					if (!childPrefix.equals("MO") && !childPrefix.equals("PT") &&
						!childPrefix.equals("AX") && !childPrefix.equals("DR") &&
						!childPrefix.equals("DC")) {                                    // ← NEU
						throw new StructureException(
							"Subsystem can only contain Module, Part, Auxiliary, Drawing, or Document. Cannot add: " + childPrefix);
					}
				}
				case "MO" -> {
					if (!childPrefix.equals("MU") && !childPrefix.equals("PT") &&
						!childPrefix.equals("AX") && !childPrefix.equals("DR") &&
						!childPrefix.equals("DC")) {                                    // ← NEU
						throw new StructureException(
							"Module can only contain Submodule, Part, Auxiliary, Drawing, or Document. Cannot add: " + childPrefix);
					}
				}
				case "MU" -> {
					if (!childPrefix.equals("PT") && !childPrefix.equals("AX") &&
						!childPrefix.equals("DR") && !childPrefix.equals("DC")) {       // ← NEU
						throw new StructureException(
							"Submodule can only contain Part, Auxiliary, Drawing, or Document. Cannot add: " + childPrefix);
					}
				}
				case "PT", "AX" -> {
					if (!childPrefix.equals("DR") && !childPrefix.equals("DC")) {      // ← NEU
						throw new StructureException(
							"Part/Auxiliary can only contain Drawing or Document. Cannot add: " + childPrefix);
					}
				}
				case "DS" -> {
					if (!childPrefix.equals("DS") && !childPrefix.equals("DR") &&
						!childPrefix.equals("PT") && !childPrefix.equals("AX") &&
						!childPrefix.equals("DC")) {                                    // ← NEU
						throw new StructureException(
							"Design can contain Design, Drawing, Part, Auxiliary, or Document. Cannot add: " + childPrefix);
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
			case "MA" -> List.of("SY", "PT", "AX", "DR", "DC");
			case "SY" -> List.of("SU", "PT", "AX", "DR", "DC");
			case "SU" -> List.of("MO", "PT", "AX", "DR", "DC");
			case "MO" -> List.of("MU", "PT", "AX", "DR", "DC");
			case "MU" -> List.of("PT", "AX", "DR", "DC");
			case "PT", "AX" -> List.of("DR", "DC");
			case "DS" -> List.of("DS", "DR", "PT", "AX", "DC");
			default -> List.of();
		};
	}
	
	// In StructureService – Signatur erweitern:
	@Transactional
	public StructureRelation addToStructure(
			String parentId,    Long parentRevisionId,
			String childId,     Long childRevisionId,
			Integer quantity,   String positionNumber,
			String referenceDesignator) {          // ← NEU

		validateStructureRelation(parentId, childId);

		List<StructureRelation> existing =
			structureRelationRepository
				.findByParentIdAndParentRevisionId(parentId, parentRevisionId);

		boolean alreadyExists = existing.stream().anyMatch(rel ->
			rel.getChildId().equals(childId) &&
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
		relation.setReferenceDesignator(referenceDesignator);   // ← NEU

		log.info("Added structure relation: {} → {} (refDes: {})",
				 parentId, childId, referenceDesignator);
		return structureRelationRepository.save(relation);
	}
}