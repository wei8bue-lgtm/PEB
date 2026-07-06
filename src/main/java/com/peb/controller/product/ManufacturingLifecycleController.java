// src/main/java/com/peb/controller/product/ManufacturingLifecycleController.java
package com.peb.controller.product;

import com.peb.model.product.*;
import com.peb.service.product.ManufacturingLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/api/manufacturing")
@RequiredArgsConstructor
@Slf4j
public class ManufacturingLifecycleController {

    private final ManufacturingLifecycleService lifecycleService;

    // ══════════════════════════════════════════════════════════════
    //  PRODUCT DATA
    // ══════════════════════════════════════════════════════════════

	@PostMapping("/product-data/{id}/release")
	@PreAuthorize("hasAuthority('EXECUTE')")
	public String releaseProductData(
			@PathVariable String id,
			RedirectAttributes ra) {

		// ── ID bereinigen ─────────────────────────────────────────
		String cleanId = id.trim();

		try {
			lifecycleService.releaseProductData(cleanId);
			ra.addFlashAttribute("successMessage",
					"✅ ProductData " + cleanId
					+ " erfolgreich freigegeben.");
		} catch (Exception e) {
			ra.addFlashAttribute("errorMessage",
					"❌ " + e.getMessage());
		}
		return "redirect:/product-data/" + cleanId;
	}

	@PostMapping("/product-data/{id}/revise")
	@PreAuthorize("hasAuthority('EXECUTE')")
	public String reviseProductData(
			@PathVariable String id,
			RedirectAttributes ra) {

		// ── ID bereinigen ─────────────────────────────────────────
		String cleanId = id.trim();

		try {
			ProductDataRevision newRev =
					lifecycleService.reviseProductData(cleanId);

			ra.addFlashAttribute("successMessage",
					"📝 Neue Revision "
					+ newRev.getRevision()
					+ " erfolgreich erstellt.");

			return "redirect:/product-data/" + cleanId;

		} catch (Exception e) {
			ra.addFlashAttribute("errorMessage",
					"❌ " + e.getMessage());
			return "redirect:/product-data/" + cleanId;
		}
	}

    @PostMapping("/product-data/{id}/saveas")
    @PreAuthorize("hasAuthority('EXECUTE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveAsProductData(
            @PathVariable String id,
            @RequestParam String newName) {
        try {
            // saveAsProductData gibt ProductData zurück
            ProductData copy =
                    lifecycleService.saveAsProductData(
                            id, newName);
            return ResponseEntity.ok(Map.of(
                    "id",   copy.getId(),
                    "name", copy.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PRODUCT BOM
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/product-bom/{id}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String releaseProductBOM(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            lifecycleService.releaseProductBOM(id);
            ra.addFlashAttribute("successMessage",
                    "✅ ProductBOM " + id
                    + " erfolgreich freigegeben.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
        }
        return "redirect:/product-bom/" + id;
    }

    @PostMapping("/product-bom/{id}/revise")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String reviseProductBOM(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            ProductBOMRevision newRev =
                    lifecycleService.reviseProductBOM(id);

            ra.addFlashAttribute("successMessage",
                    "📝 Neue Revision "
                    + newRev.getRevision()
                    + " erfolgreich erstellt.");

            // Redirect zum selben Master
            return "redirect:/product-bom/" + id;

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
            return "redirect:/product-bom/" + id;
        }
    }

    @PostMapping("/product-bom/{id}/saveas")
    @PreAuthorize("hasAuthority('EXECUTE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveAsProductBOM(
            @PathVariable Long id,
            @RequestParam String newName) {
        try {
            ProductBOM copy =
                    lifecycleService.saveAsProductBOM(id, newName);
            return ResponseEntity.ok(Map.of(
                    "id",   copy.getId(),
                    "name", copy.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  MANUFACTURING BOM
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/manufacturing-bom/{id}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String releaseManufacturingBOM(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            lifecycleService.releaseManufacturingBOM(id);
            ra.addFlashAttribute("successMessage",
                    "✅ ManufacturingBOM " + id
                    + " erfolgreich freigegeben.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
        }
        return "redirect:/manufacturing-bom/" + id;
    }

    @PostMapping("/manufacturing-bom/{id}/revise")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String reviseManufacturingBOM(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            ManufacturingBOMRevision newRev =
                    lifecycleService.reviseManufacturingBOM(id);

            ra.addFlashAttribute("successMessage",
                    "📝 Neue Revision "
                    + newRev.getRevision()
                    + " erfolgreich erstellt.");

            return "redirect:/manufacturing-bom/" + id;

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
            return "redirect:/manufacturing-bom/" + id;
        }
    }

    @PostMapping("/manufacturing-bom/{id}/saveas")
    @PreAuthorize("hasAuthority('EXECUTE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveAsManufacturingBOM(
            @PathVariable Long id,
            @RequestParam String newName) {
        try {
            ManufacturingBOM copy =
                    lifecycleService.saveAsManufacturingBOM(
                            id, newName);
            return ResponseEntity.ok(Map.of(
                    "id",   copy.getId(),
                    "name", copy.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PRODUCT BOP
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/product-bop/{id}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String releaseProductBOP(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            lifecycleService.releaseProductBOP(id);
            ra.addFlashAttribute("successMessage",
                    "✅ ProductBOP " + id
                    + " erfolgreich freigegeben.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
        }
        return "redirect:/product-bop/" + id;
    }

    @PostMapping("/product-bop/{id}/revise")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String reviseProductBOP(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            ProductBOPRevision newRev =
                    lifecycleService.reviseProductBOP(id);

            ra.addFlashAttribute("successMessage",
                    "📝 Neue Revision "
                    + newRev.getRevision()
                    + " erfolgreich erstellt.");

            return "redirect:/product-bop/" + id;

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
            return "redirect:/product-bop/" + id;
        }
    }

    @PostMapping("/product-bop/{id}/saveas")
    @PreAuthorize("hasAuthority('EXECUTE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveAsProductBOP(
            @PathVariable Long id,
            @RequestParam String newName) {
        try {
            ProductBOP copy =
                    lifecycleService.saveAsProductBOP(id, newName);
            return ResponseEntity.ok(Map.of(
                    "id",   copy.getId(),
                    "name", copy.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PLANT BOP
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/plant-bop/{id}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String releasePlantBOP(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            lifecycleService.releasePlantBOP(id);
            ra.addFlashAttribute("successMessage",
                    "✅ PlantBOP " + id
                    + " erfolgreich freigegeben.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
        }
        return "redirect:/plant-bop/" + id;
    }

    @PostMapping("/plant-bop/{id}/revise")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String revisePlantBOP(
            @PathVariable Long id,
            RedirectAttributes ra) {
        try {
            PlantBOPRevision newRev =
                    lifecycleService.revisePlantBOP(id);

            ra.addFlashAttribute("successMessage",
                    "📝 Neue Revision "
                    + newRev.getRevision()
                    + " erfolgreich erstellt.");

            return "redirect:/plant-bop/" + id;

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage",
                    "❌ " + e.getMessage());
            return "redirect:/plant-bop/" + id;
        }
    }

    @PostMapping("/plant-bop/{id}/saveas")
    @PreAuthorize("hasAuthority('EXECUTE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveAsPlantBOP(
            @PathVariable Long id,
            @RequestParam String newName) {
        try {
            PlantBOP copy =
                    lifecycleService.saveAsPlantBOP(id, newName);
            return ResponseEntity.ok(Map.of(
                    "id",   copy.getId(),
                    "name", copy.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}