package com.peb.service;

import com.peb.dto.DesignStructureNodeDTO;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.cad.DesignStructure;
import com.peb.repository.DesignStructureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignStructureService {

    private final DesignStructureRepository structureRepo;
    private final DesignService             designService;

    // ══════════════════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Builds the full recursive tree starting at the given root Design.
     * Uses the Design's latestRevision as the root revision.
     *
     * @param rootDesignId  Design.id of the root
     * @return              fully populated tree node (root)
     */
	@Transactional(readOnly = true)
	public DesignStructureNodeDTO buildTree(String rootDesignId) {
		Design root = designService.getDesign(rootDesignId);
		return buildNode(root,
						 /* rev          */   root.getLatestRevision(),   // ← Root: immer aktuellster Stand
						 /* structureId  */   null,
						 /* quantity     */   null,
						 /* posNumber    */   null,
						 /* depth        */   0,
						 /* visitedIds   */   new HashSet<>());
	}

    /**
     * Returns all Designs that can legally be added as children
     * under the given parent revision.
     *
     * Exclusion rules:
     *  1. The parent Design itself          (no self-link)
     *  2. Designs already directly linked   (no duplicate)
     *  3. Designs with no revision          (cannot be used in structure)
     *  4. Designs that are ancestors        (would create a cycle)
     *
     * @param parentRevisionId  revisionId of the parent Design revision
     * @param parentDesignId    Design.id  of the parent
     */
    @Transactional(readOnly = true)
    public List<DesignStructureNodeDTO> getAvailableChildren(
            Long   parentRevisionId,
            String parentDesignId) {

        // Build exclusion set: self + already linked children
        Set<String> excluded = new HashSet<>();
        excluded.add(parentDesignId);

        structureRepo.findByParentRevisionId(parentRevisionId)
                     .forEach(link -> excluded.add(link.getChildId()));

        // Collect all ancestors to prevent cycles
        Set<String> ancestors = new HashSet<>();
        collectAncestors(parentDesignId, ancestors);
        excluded.addAll(ancestors);

        List<DesignStructureNodeDTO> candidates = new ArrayList<>();

        designService.getAllDesigns().forEach(d -> {
            if (excluded.contains(d.getId())) return;

            DesignRevision rev = d.getLatestRevision();
            // Designs without any revision cannot be linked
            if (rev == null) return;

            candidates.add(DesignStructureNodeDTO.builder()
                    .id(d.getId())
                    .name(d.getName())
                    .department(d.getDepartment())
                    .revisionId(rev.getRevisionId())
                    .revision(rev.getRevision())
                    .releaseStatus(rev.getReleaseStatus().name())
                    .releaseStatusDisplay(
                            rev.getReleaseStatus().getDisplayName()
                            + " " + rev.getReleaseStatus().getIcon())
                    .build());
        });

        // Sort by ID for a stable, predictable order in the UI
        candidates.sort(Comparator.comparing(DesignStructureNodeDTO::getId));
        return candidates;
    }

    // ══════════════════════════════════════════════════════════════════════
    // WRITE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Creates a parent → child link in the Design structure.
     *
     * @throws IllegalArgumentException if the link already exists
     *                                  or would create a cycle
     */
    @Transactional
    public DesignStructure addChild(String parentId,
                                    Long   parentRevisionId,
                                    String childId,
                                    Long   childRevisionId,
                                    int    quantity,
                                    String positionNumber) {

        // ── Guard: duplicate ──────────────────────────────────────────────
        if (structureRepo.existsByParentRevisionIdAndChildRevisionId(
                parentRevisionId, childRevisionId)) {
            throw new IllegalArgumentException(
                    "Design [" + childId + "] is already a direct "
                    + "child of [" + parentId + "].");
        }

        // ── Guard: self-reference ─────────────────────────────────────────
        if (parentId.equals(childId)) {
            throw new IllegalArgumentException(
                    "A Design cannot be its own child.");
        }

        // ── Guard: cycle ──────────────────────────────────────────────────
        if (wouldCreateCycle(parentId, childId)) {
            throw new IllegalArgumentException(
                    "Adding [" + childId + "] as child of ["
                    + parentId + "] would create a circular reference.");
        }

        String currentUser = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        DesignStructure link = DesignStructure.builder()
                .parentId(parentId)
                .parentRevisionId(parentRevisionId)
                .childId(childId)
                .childRevisionId(childRevisionId)
                .quantity(Math.max(1, quantity))
                .positionNumber(positionNumber)
                .createdBy(currentUser)
                .build();

        DesignStructure saved = structureRepo.save(link);
        log.info("Design structure link created: [{}] → [{}] (structureId={})",
                 parentId, childId, saved.getStructureId());
        return saved;
    }

    /**
     * Deletes a single structure link by its primary key.
     * The child Design itself is NOT deleted.
     *
     * @param structureId  DesignStructure.structureId to remove
     */
    @Transactional
    public void removeChild(Long structureId) {
        DesignStructure link = structureRepo.findById(structureId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Design structure link not found: " + structureId));

        structureRepo.delete(link);
        log.info("Design structure link removed: structureId={}  "
                 + "parent=[{}] → child=[{}]",
                 structureId, link.getParentId(), link.getChildId());
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Recursively builds one DesignStructureNodeDTO.
     *
     * @param design         the Design to represent
     * @param structureId    PK of the DesignStructure row (null for root)
     * @param quantity       from the DesignStructure row  (null for root)
     * @param positionNumber from the DesignStructure row  (null for root)
     * @param depth          nesting level (0 = root)
     * @param visitedIds     guards against infinite loops from bad data
     */
	private DesignStructureNodeDTO buildNode(Design         design,
											  DesignRevision rev,             // ← NEU: konkrete Revision statt design.getLatestRevision()
											  Long           structureId,
											  Integer        quantity,
											  String         positionNumber,
											  int            depth,
											  Set<String>    visitedIds) {

		DesignStructureNodeDTO node = DesignStructureNodeDTO.builder()
				.id(design.getId())
				.name(design.getName())
				.department(design.getDepartment())
				.revisionId(rev  != null ? rev.getRevisionId() : null)
				.revision(rev    != null ? rev.getRevision()   : null)
				.releaseStatus(rev != null
						? rev.getReleaseStatus().name() : null)
				.releaseStatusDisplay(rev != null
						? rev.getReleaseStatus().getDisplayName()
						  + " " + rev.getReleaseStatus().getIcon()
						: "—")
				.structureId(structureId)
				.quantity(quantity)
				.positionNumber(positionNumber)
				.depth(depth)
				.build();

		// Stop recursion: keine Revision → keine weiteren Structure-Links möglich
		if (rev == null) return node;

		// Stop recursion: Zyklus-Schutz bei fehlerhaften Daten
		if (visitedIds.contains(design.getId())) {
			log.warn("Cycle detected in design structure at [{}] "
					 + "– stopping recursion", design.getId());
			return node;
		}

		Set<String> pathVisited = new HashSet<>(visitedIds);
		pathVisited.add(design.getId());

		// Kinder anhand der ÜBERGEBENEN Revision laden (nicht latestRevision!)
		List<DesignStructure> childLinks =
				structureRepo.findByParentRevisionId(rev.getRevisionId());

		for (DesignStructure link : childLinks) {
			try {
				Design child = designService.getDesign(link.getChildId());

				// Die tatsächlich VERBAUTE Revision laden – nicht die neueste!
				DesignRevision childRev =
						designService.getRevision(link.getChildRevisionId());

				DesignStructureNodeDTO childNode = buildNode(
						child,
						childRev,
						link.getStructureId(),
						link.getQuantity(),
						link.getPositionNumber(),
						depth + 1,
						pathVisited);
				node.getChildren().add(childNode);
			} catch (Exception e) {
				// Log und weiter – ein fehlendes Child/Revision soll die View nicht sprengen
				log.error("Cannot resolve child design [{}] revision [{}] "
						  + "referenced by structure link [{}]: {}",
						  link.getChildId(),
						  link.getChildRevisionId(),
						  link.getStructureId(),
						  e.getMessage());
			}
		}

		return node;
	}

    /**
     * Returns true if making {@code potentialChildId} a child of
     * {@code parentId} would introduce a cycle.
     *
     * Strategy: collect all ancestors of parentId.
     * If potentialChildId is among them, a cycle would form.
     */
    private boolean wouldCreateCycle(String parentId,
                                      String potentialChildId) {
        Set<String> ancestors = new HashSet<>();
        collectAncestors(parentId, ancestors);
        return ancestors.contains(potentialChildId);
    }

    /**
     * Recursively walks UP the structure tree from {@code designId},
     * adding every ancestor's Design.id to {@code visited}.
     */
    private void collectAncestors(String designId, Set<String> visited) {
        if (visited.contains(designId)) return;  // already processed
        visited.add(designId);

        structureRepo.findAllByChildDesignId(designId)
                     .forEach(link ->
                             collectAncestors(link.getParentId(), visited));
    }
	
	public List<DesignStructureNodeDTO> flattenTree(
        DesignStructureNodeDTO root) {

    List<DesignStructureNodeDTO> flat = new ArrayList<>();
    flattenNode(root, flat);
    return flat;
}

	private void flattenNode(DesignStructureNodeDTO node,
							  List<DesignStructureNodeDTO> flat) {
		flat.add(node);
		if (node.getChildren() != null) {
			for (DesignStructureNodeDTO child : node.getChildren()) {
				flattenNode(child, flat);
			}
		}
	}
	
	// ══════════════════════════════════════════════════════════════════════
	// WHERE-USED
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Returns all DesignStructure rows where the given Design
	 * appears as a child – i.e. all parent links (where-used).
	 *
	 * @param designId  Design.id to look up
	 */
	@Transactional(readOnly = true)
	public List<DesignStructure> getWhereUsed(String designId) {
		return structureRepo.findAllByChildDesignId(designId);
	}

	// ══════════════════════════════════════════════════════════════════════
	// ALL LINKS (Admin)
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Returns all rows in DESIGN_STRUCTURE.
	 * Used by the Admin overview endpoint.
	 */
	@Transactional(readOnly = true)
	public List<DesignStructure> getAllLinks() {
		return structureRepo.findAll();
	}

}