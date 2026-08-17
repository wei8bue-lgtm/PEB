package com.peb.controller;

import com.peb.dto.DesignDTO;
import com.peb.dto.EclassClassificationDTO;
import com.peb.model.base.Dataset;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import com.peb.service.DesignService;
import com.peb.service.EclassService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import com.peb.service.MaterialService;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/designs")
@RequiredArgsConstructor
public class DesignController {

    private final DesignService  designService;
    private final DatasetService datasetService;
    private final EclassService  eclassService;
	private final MaterialService materialService;

	// Rein informativ (Tooltip) – der Server kann diesen Pfad nicht ausführen,
	// da VisView.exe lokal beim Anwender läuft.
	@Value("${peb.viewer.tcvis.exe-path:}")
	private String tcvisExePath;

    @GetMapping
    public String listDesigns(Model model) {
        model.addAttribute("designs", designService.getAllDesigns());
        return "designs/design-list";
    }

	@GetMapping("/new")
	public String newDesignForm(Model model) {
		model.addAttribute("design", new DesignDTO());
		model.addAttribute("releasedMaterials", materialService.getReleasedActiveMaterials());  // ← NEU
		return "designs/design-form";
	}
	
    @PostMapping
	@PreAuthorize("hasAuthority('WRITE')")
	public String createDesign(@ModelAttribute DesignDTO designDTO, Model model) {
		try {
			Design design = designService.createDesign(
					designDTO.getName(),
					designDTO.getDepartment(),
					designDTO.getMass(),          // ← NEU
					designDTO.getMaterialId());   // ← NEU
			return "redirect:/designs/" + design.getId();
		} catch (Exception e) {
			model.addAttribute("error",
					"Failed to create design: " + e.getMessage());
			model.addAttribute("design", designDTO);
			model.addAttribute("releasedMaterials",
					materialService.getReleasedActiveMaterials());   // ← NEU (Formular bei Fehler neu rendern)
			return "designs/design-form";
		}
	}

	@GetMapping("/{id}")
	public String viewDesign(@PathVariable String id, Model model,HttpServletResponse response) {

		Design design = designService.getDesign(id);
		model.addAttribute("design", design);

		// ── NEU: pro Revision ermitteln, ob ein 3D-Viewer-fähiges Dataset
		//         vorhanden ist (category.viewerCompatible == true) ─────────
		Map<Long, Boolean> viewerAvailableByRevision = new HashMap<>();

		// ── NEU: pro Revision das neueste DESIGN_JT-Dataset ermitteln
		//         (für den "Open in TCVIS"-Button) ─────────────────────────
		Map<Long, Dataset> jtDatasetByRevision = new HashMap<>();

		for (DesignRevision revision : design.getRevisions()) {
			List<Dataset> revisionDatasets =
					datasetService.getDatasetsByRevisionId(revision.getRevisionId());

			boolean hasViewerDataset = revisionDatasets.stream()
					.anyMatch(ds -> ds.getCategory() != null
							&& ds.getCategory().isViewerCompatible());
			viewerAvailableByRevision.put(revision.getRevisionId(), hasViewerDataset);

			revisionDatasets.stream()
					.filter(ds -> ds.getCategory() == DatasetCategory.DESIGN_JT)
					.max(Comparator.comparing(Dataset::getCreatedDate,
							Comparator.nullsFirst(Comparator.naturalOrder())))
					.ifPresent(ds -> jtDatasetByRevision.put(revision.getRevisionId(), ds));
		}
		model.addAttribute("viewerAvailableByRevision", viewerAvailableByRevision);
		model.addAttribute("jtDatasetByRevision",       jtDatasetByRevision);
		model.addAttribute("tcvisExePath",              tcvisExePath);

		if (design.getLatestRevision() != null) {

			Long   revisionId   = design.getLatestRevision().getRevisionId();
			String revisionType = "DESIGN";

			List<Dataset> allDatasets =
					datasetService.getDatasets(revisionId, revisionType);

			// ── PDF Dataset (PDF_DOCUMENT > PDF_DRAWING) ───────────────
			Dataset pdfDataset = allDatasets.stream()
					.filter(ds -> ds.getCategory() == DatasetCategory.PDF_DOCUMENT
							   || ds.getCategory() == DatasetCategory.PDF_DRAWING)
					.min(java.util.Comparator.comparingInt(
							ds -> ds.getCategory().ordinal()))
					.orElse(null);

			// ── Excel Dataset ──────────────────────────────────────────
			Dataset excelDataset = allDatasets.stream()
					.filter(ds -> ds.getCategory() == DatasetCategory.EXCEL_TABLE)
					.findFirst()
					.orElse(null);

			model.addAttribute("pdfDataset",   pdfDataset);
			model.addAttribute("excelDataset", excelDataset);

			// ── Rückwärtskompatibilität: embeddedDataset bleibt ────────
			// Priorität: PDF > Excel
			Dataset embeddedDataset = pdfDataset != null
					? pdfDataset : excelDataset;
			model.addAttribute("embeddedDataset",  embeddedDataset);
			model.addAttribute("embeddedCategory",
					embeddedDataset != null
							? embeddedDataset.getCategory() : null);

		} else {
			model.addAttribute("pdfDataset",   null);
			model.addAttribute("excelDataset", null);
			model.addAttribute("embeddedDataset",  null);
			model.addAttribute("embeddedCategory", null);
		}

		// ECLASS-Klassifizierung laden
		EclassClassificationDTO eclassClassification =
				eclassService.getClassification(id, ObjectType.DESIGN);
		model.addAttribute("eclassClassification", eclassClassification);

		return "designs/design-view";
	}

	@GetMapping("/{id}/edit")
	@PreAuthorize("hasAuthority('WRITE')")
	public String editDesignForm(@PathVariable String id, Model model) {
		Design design = designService.getDesign(id);

		DesignDTO dto = new DesignDTO();
		dto.setName(design.getName());
		dto.setDepartment(design.getDepartment());
		dto.setMass(design.getMass());
		dto.setMaterialId(design.getLinkedMaterialId());
		dto.setChildView(design.getChildView());

		model.addAttribute("design",    dto);
		model.addAttribute("designId",  id);
		model.addAttribute("currentMaterialName", design.getMaterial());              // ← NEU (für Anzeige, falls Link mal fehlt)
		model.addAttribute("releasedMaterials", materialService.getReleasedActiveMaterials()); // ← NEU
		return "designs/design-edit";
	}

	@PostMapping("/{id}")
	@PreAuthorize("hasAuthority('WRITE')")
	public String updateDesign(@PathVariable String id,
							   @ModelAttribute DesignDTO designDTO) {
		designService.updateDesign(
				id,
				designDTO.getName(),
				designDTO.getDepartment(),
				designDTO.getMass(),
				designDTO.getMaterialId(),
				designDTO.getChildView());
		return "redirect:/designs/" + id;
	}

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteDesign(@PathVariable String id) {
        designService.deleteDesign(id);
        return "redirect:/designs";
    }
}