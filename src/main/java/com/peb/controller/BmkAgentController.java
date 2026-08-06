// src/main/java/com/peb/controller/BmkAgentController.java
package com.peb.controller;

import com.peb.dto.BmkAgentRequestDTO;
import com.peb.dto.BmkAgentResponseDTO;
import com.peb.dto.BmkExportDTO;
import com.peb.dto.BmkUpdateDTO;
import com.peb.dto.RefDesUpdateDTO;
import com.peb.model.structure.StructureRelation;
import com.peb.service.BmkAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST Endpoints für BMK-Operationen.
 *
 * POST /api/bmk/generate        – In-Process BMK-Generierung (klassisch)
 * POST /api/bmk/generate-ai     – KI-Agent BMK-Generierung (Gemini)
 * PUT  /api/bmk/relations/{id}  – Manuelle Korrektur (Modal)
 * PATCH /api/bmk/relations/{id}/refdes – Inline-Edit RefDes
 */
@RestController
@RequestMapping("/api/bmk")
@RequiredArgsConstructor
@Slf4j
public class BmkAgentController {

 private final BmkAgentService bmkAgentService;

 // ─────────────────────────────────────────────────────────
 // POST /api/bmk/generate  (In-Process – bestehend)
 // ─────────────────────────────────────────────────────────

 @PostMapping("/generate")
 @PreAuthorize("hasAuthority('WRITE')")
 public ResponseEntity<BmkAgentResponseDTO> generateBmk(
         @RequestBody BmkAgentRequestDTO request,
         @AuthenticationPrincipal UserDetails user) {

     String username = user != null ? user.getUsername() : "system";
     log.info("BMK In-Process gestartet von [{}] für Root [{}]",
              username, request.getRootObjectId());

     BmkAgentResponseDTO response = bmkAgentService.generateBmkForTree(
             request.getRootObjectId(),
             request.getRootRevisionId(),
             false,  // useAiAgent = false
             username);

     return ResponseEntity.ok(response);
 }

 // ─────────────────────────────────────────────────────────
 // POST /api/bmk/generate-ai  (NEU – Gemini Agent)
 // ─────────────────────────────────────────────────────────

 /**
  * Startet die BMK-Generierung via Gemini KI-Agent.
  * Identischer Request-Body wie /generate.
  * Separater Endpoint ermöglicht den direkten Vergleich im UI.
  */
 @PostMapping("/generate-ai")
 @PreAuthorize("hasAuthority('WRITE')")
 public ResponseEntity<BmkAgentResponseDTO> generateBmkWithAi(
         @RequestBody BmkAgentRequestDTO request,
         @AuthenticationPrincipal UserDetails user) {

     String username = user != null ? user.getUsername() : "system";
     log.info("BMK KI-Agent gestartet von [{}] für Root [{}]",
              username, request.getRootObjectId());

     BmkAgentResponseDTO response = bmkAgentService.generateBmkForTree(
             request.getRootObjectId(),
             request.getRootRevisionId(),
             true,   // useAiAgent = true
             username);

     return ResponseEntity.ok(response);
 }

 // ─────────────────────────────────────────────────────────
 // GET /api/bmk/export/{rootObjectId}
 // ─────────────────────────────────────────────────────────

 /**
  * Exportiert die BMK-Konfiguration eines Strukturbaums als flache
  * JSON-Liste (Datei-Download). Read-only.
  *
  * @param rootObjectId   Object-ID des Root-Knotens
  * @param revisionId     Revision-ID des Root-Knotens
  */
 @GetMapping("/export/{rootObjectId}")
 @PreAuthorize("hasAuthority('READ')")
 public ResponseEntity<BmkExportDTO> exportBmk(
         @PathVariable String rootObjectId,
         @RequestParam Long   revisionId,
         @AuthenticationPrincipal UserDetails user) {

     String username = user != null ? user.getUsername() : "system";
     log.info("BMK-Export angefordert von [{}] für Root [{}/rev{}]",
              username, rootObjectId, revisionId);

     BmkExportDTO export =
             bmkAgentService.exportBmkConfiguration(rootObjectId, revisionId);

     String filename = "bmk-export_" + rootObjectId + "_rev" + revisionId + ".json";

     return ResponseEntity.ok()
             .header(HttpHeaders.CONTENT_DISPOSITION,
                     "attachment; filename=\"" + filename + "\"")
             .contentType(MediaType.APPLICATION_JSON)
             .body(export);
 }

 // ─────────────────────────────────────────────────────────
 // PUT /api/bmk/relations/{id}
 // ─────────────────────────────────────────────────────────

 @PutMapping("/relations/{relationId}")
 @PreAuthorize("hasAuthority('WRITE')")
 public ResponseEntity<StructureRelation> updateBmkFields(
         @PathVariable Long relationId,
         @RequestBody  BmkUpdateDTO dto,
         @AuthenticationPrincipal UserDetails user) {

     String username = user != null ? user.getUsername() : "system";
     StructureRelation updated = bmkAgentService.updateBmkFields(
             relationId,
             dto.getReferenceDesignator(),
             dto.getBmk(),
             username);

     return ResponseEntity.ok(updated);
 }

 // ─────────────────────────────────────────────────────────
 // PATCH /api/bmk/relations/{id}/refdes
 // ─────────────────────────────────────────────────────────

 @PatchMapping("/relations/{relationId}/refdes")
 @PreAuthorize("hasAuthority('WRITE')")
 public ResponseEntity<StructureRelation> updateReferenceDesignator(
         @PathVariable Long relationId,
         @RequestBody  RefDesUpdateDTO dto,
         @AuthenticationPrincipal UserDetails user) {

     String username = user != null ? user.getUsername() : "system";
     log.info("RefDes Inline-Edit von [{}] für Relation [{}] → '{}'",
              username, relationId, dto.getReferenceDesignator());

     StructureRelation updated = bmkAgentService.updateReferenceDesignator(
             relationId,
             dto.getReferenceDesignator(),
             username);

     return ResponseEntity.ok(updated);
 }
}