// src/main/java/com/peb/service/BmkAgentService.java
package com.peb.service;

import com.peb.client.DialogflowCxAgentClient;
import com.peb.dto.BmkAgentResponseDTO;
import com.peb.dto.BmkAgentTreeDTO;
import com.peb.model.structure.StructureRelation;
import com.peb.repository.StructureRelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BmkAgentService
 * ───────────────
 * Verwaltet zwei Modi der BMK-Generierung:
 *
 *  Modus A (In-Process / klassisch):
 *    Traversiert den Baum rekursiv und konkateniert RefDes-Segmente.
 *    Bewährt, deterministisch, kein externer Aufruf.
 *
 *  Modus B (KI-Agent / Gemini):
 *    Serialisiert den gesamten Baum als JSON, sendet ihn an den
 *    Dialogflow CX / Gemini Agenten (bcb383f1-...) und schreibt
 *    die zurückgegebenen BMKs in die Datenbank.
 *
 * Vergleich möglich durch zwei separate UI-Buttons.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BmkAgentService {

 private final StructureRelationRepository structureRelationRepository;
 private final DialogflowCxAgentClient     agentClient;

 @Value("${peb.bmk.ai-agent-enabled:true}")
 private boolean aiAgentEnabled;

 // ═════════════════════════════════════════════════════════
 // PUBLIC API
 // ═════════════════════════════════════════════════════════

 /**
  * Einstiegspunkt für BEIDE Modi.
  *
  * @param rootObjectId   Object-ID des Root-Knotens
  * @param rootRevisionId Revision-ID
  * @param useAiAgent     true → Gemini Agent, false → In-Process
  * @param username       Authenticated user
  */
 @Transactional
public BmkAgentResponseDTO generateBmkForTree(
        String  rootObjectId,
        Long    rootRevisionId,
        boolean useAiAgent,
        String  username) {

    LocalDateTime runAt    = LocalDateTime.now();
    List<String>  warnings = new ArrayList<>();

    log.info("BMK-Generierung gestartet. Modus=[{}], Root=[{}/rev{}], User=[{}]",
             useAiAgent ? "KI-AGENT" : "IN-PROCESS",
             rootObjectId, rootRevisionId, username);

    BmkAgentResponseDTO response = new BmkAgentResponseDTO();
    response.setTriggeredAt(runAt);

    try {
        // ✅ FIX: Mit 0 initialisieren – sicherer Default
        int updatedCount = 0;

        if (useAiAgent && aiAgentEnabled) {

            // KI-Agent Pfad
            if (!agentClient.isAvailable()) {
                log.warn("KI-Agent angefordert aber nicht verfügbar. " +
                         "Fallback auf In-Process.");
                warnings.add(
                    "⚠ KI-Agent nicht verfügbar (Service Account fehlt " +
                    "oder nicht konfiguriert). " +
                    "In-Process Logik wurde stattdessen verwendet.");

                AtomicInteger counter = new AtomicInteger(0);
                traverseAndAssign(
                        rootObjectId, rootRevisionId,
                        "", warnings, counter, runAt, username);
                updatedCount = counter.get();
                response.setMessage(
                    "⚠ KI-Agent nicht verfügbar – " +
                    "In-Process Logik als Fallback verwendet.");

            } else {
                updatedCount = generateViaAiAgent(
                        rootObjectId, rootRevisionId,
                        warnings, runAt, username);
                response.setMessage(
                    "✅ KI-Agent (Gemini) hat BMKs generiert.");
            }

        } else {
            // In-Process Pfad (useAiAgent=false ODER aiAgentEnabled=false)
            if (useAiAgent && !aiAgentEnabled) {
                warnings.add(
                    "⚠ KI-Agent ist deaktiviert " +
                    "(peb.bmk.ai-agent-enabled=false). " +
                    "In-Process Logik verwendet.");
            }

            AtomicInteger counter = new AtomicInteger(0);
            traverseAndAssign(
                    rootObjectId, rootRevisionId,
                    "", warnings, counter, runAt, username);
            updatedCount = counter.get();
            response.setMessage(
                "✅ In-Process Logik hat BMKs generiert.");
        }

        // updatedCount ist jetzt in ALLEN Pfaden garantiert gesetzt ✅
        response.setSuccess(true);
        response.setUpdatedRelations(updatedCount);

    } catch (Exception e) {
        log.error("BMK-Generierung fehlgeschlagen. Modus=[{}]",
                  useAiAgent ? "KI-AGENT" : "IN-PROCESS", e);
        response.setSuccess(false);
        response.setMessage("❌ Fehler: " + e.getMessage());
        response.setUpdatedRelations(0);
        warnings.add("Kritischer Fehler: " + e.getMessage());
    }

    response.setWarnings(warnings);

    log.info("BMK-Generierung abgeschlossen. " +
             "Modus=[{}], Aktualisiert=[{}], Warnungen=[{}]",
             useAiAgent ? "KI-AGENT" : "IN-PROCESS",
             response.getUpdatedRelations(),
             warnings.size());

    return response;
}

// ═════════════════════════════════════════════════════════
// MODUS B: KI-AGENT INTEGRATION
// ═════════════════════════════════════════════════════════

private int generateViaAiAgent(
        String        rootObjectId,
        Long          rootRevisionId,
        List<String>  warnings,
        LocalDateTime runAt,
        String        username) throws Exception {

    // ── 1. Gesamten Baum flach sammeln ────────────────────
    List<BmkAgentTreeDTO.RelationEntry> entries = new ArrayList<>();
    collectRelationsFlat(
            rootObjectId, rootRevisionId,
            "", 1, entries, warnings);

    if (entries.isEmpty()) {
        warnings.add("Keine Relationen im Baum gefunden.");
        return 0;
    }

    log.info("Sende {} Relationen an Gemini Agent.", entries.size());

    // ── 2. DTO bauen ──────────────────────────────────────
    BmkAgentTreeDTO treeDto = BmkAgentTreeDTO.builder()
            .rootObjectId(rootObjectId)
            .norm("N54_D6_DIN_EN_81346-2")
            .relations(entries)
            .build();

    // ── 3. Agent aufrufen ─────────────────────────────────
    Map<String, String> bmkMap =
            agentClient.generateBmkMap(treeDto, username);

    if (bmkMap.isEmpty()) {
        throw new IllegalStateException(
            "Gemini Agent hat eine leere BMK-Map zurückgegeben. " +
            "Intent möglicherweise nicht erkannt.");
    }

    // ── 4. Ergebnisse zurückschreiben ─────────────────────
    int updated = 0;
    for (BmkAgentTreeDTO.RelationEntry entry : entries) {
        String relationIdStr = String.valueOf(entry.getRelationId());
        String generatedBmk  = bmkMap.get(relationIdStr);

        if (generatedBmk == null || generatedBmk.isBlank()) {
            warnings.add(
                "Agent hat kein BMK für Relation [" +
                entry.getRelationId() + "] " +
                entry.getParentObjectId() + " → " +
                entry.getChildObjectId() + " geliefert.");
            continue;
        }

        structureRelationRepository
                .findById(entry.getRelationId())
                .ifPresentOrElse(
                    rel -> {
                        rel.setBmk(generatedBmk);
                        rel.setBmkGeneratedAt(runAt);
                        rel.setBmkGeneratedBy(username + " (KI-Agent)");
                        structureRelationRepository.save(rel);
                        log.debug("Relation [{}] → BMK: {}",
                                  rel.getRelationId(), generatedBmk);
                    },
                    () -> warnings.add(
                        "Relation [" + entry.getRelationId() +
                        "] nicht in DB gefunden.")
                );
        updated++;
    }

    return updated;
}

 /**
  * Sammelt alle Relationen des Baums rekursiv in einer flachen Liste.
  * Parallel ermittelt er den parentBmkPath (In-Process-Logik als Kontext).
  *
  * Diese Information hilft dem Agenten den Pfad-Kontext zu verstehen,
  * auch wenn er ihn eigenständig neu berechnet.
  */
 private void collectRelationsFlat(
         String        parentObjectId,
         Long          parentRevisionId,
         String        parentBmkPath,
         int           depth,
         List<BmkAgentTreeDTO.RelationEntry> entries,
         List<String>  warnings) {

     List<StructureRelation> children =
             structureRelationRepository
                     .findByParentIdAndParentRevisionId(
                             parentObjectId, parentRevisionId);

     for (StructureRelation rel : children) {

         String refDes = rel.getReferenceDesignator();

         if (refDes == null || refDes.isBlank()) {
             warnings.add(
                 "Relation [" + rel.getRelationId() + "] " +
                 parentObjectId + " → " + rel.getChildId() +
                 " hat kein Reference Designator.");
             refDes = "";
         }

         // Kontext-Pfad für den Agenten (hilft, ist nicht bindend)
         String contextPath = assembleBmk(parentBmkPath, refDes);

         // Entry für den Agenten erstellen
         entries.add(BmkAgentTreeDTO.RelationEntry.builder()
                 .relationId(rel.getRelationId())
                 .parentObjectId(parentObjectId)
                 .childObjectId(rel.getChildId())
                 .childObjectType(
                     rel.getChildId().length() >= 2
                         ? rel.getChildId().substring(0, 2)
                         : "?")
                 .referenceDesignator(refDes)
                 .parentBmkPath(parentBmkPath)
                 .depth(depth)
                 .build());

         // Rekursion
         collectRelationsFlat(
                 rel.getChildId(),
                 rel.getChildRevisionId(),
                 contextPath,
                 depth + 1,
                 entries,
                 warnings);
     }
 }

 // ═════════════════════════════════════════════════════════
 // MODUS A: IN-PROCESS (unverändert, bestehende Logik)
 // ═════════════════════════════════════════════════════════

 private void traverseAndAssign(
         String        parentObjectId,
         Long          parentRevisionId,
         String        parentBmkPath,
         List<String>  warnings,
         AtomicInteger counter,
         LocalDateTime runAt,
         String        username) {

     List<StructureRelation> children =
             structureRelationRepository
                     .findByParentIdAndParentRevisionId(
                             parentObjectId, parentRevisionId);

     for (StructureRelation relation : children) {

         String refDes = relation.getReferenceDesignator();

         if (refDes == null || refDes.isBlank()) {
             warnings.add(
                 "Relation [" + relation.getRelationId() + "] " +
                 parentObjectId + " → " + relation.getChildId() +
                 " hat kein Reference Designator. " +
                 "BMK-Segment wird übersprungen.");
             refDes = "";
         }

         String assembledBmk = assembleBmk(parentBmkPath, refDes);

         relation.setBmk(assembledBmk);
         relation.setBmkGeneratedAt(runAt);
         relation.setBmkGeneratedBy(username + " (in-process)");
         structureRelationRepository.save(relation);
         counter.incrementAndGet();

         log.debug("Relation [{}] {} → {} : BMK={}",
                   relation.getRelationId(),
                   parentObjectId,
                   relation.getChildId(),
                   assembledBmk);

         traverseAndAssign(
                 relation.getChildId(),
                 relation.getChildRevisionId(),
                 assembledBmk,
                 warnings, counter, runAt, username);
     }
 }

 private String assembleBmk(String parentPath, String refDes) {
     if (refDes == null || refDes.isBlank()) return parentPath;
     return parentPath + refDes.trim();
 }

 // ═════════════════════════════════════════════════════════
 // SINGLE-RELATION UPDATES (unverändert)
 // ═════════════════════════════════════════════════════════

 @Transactional
 public StructureRelation updateBmkFields(
         Long   relationId,
         String referenceDesignator,
         String bmk,
         String username) {

     StructureRelation rel = structureRelationRepository
             .findById(relationId)
             .orElseThrow(() -> new IllegalArgumentException(
                     "Relation nicht gefunden: " + relationId));

     rel.setReferenceDesignator(referenceDesignator);
     rel.setBmk(bmk);
     rel.setBmkGeneratedBy(username + " (manuell)");
     rel.setBmkGeneratedAt(LocalDateTime.now());

     return structureRelationRepository.save(rel);
 }

 @Transactional
 public StructureRelation updateReferenceDesignator(
         Long   relationId,
         String referenceDesignator,
         String username) {

     StructureRelation rel = structureRelationRepository
             .findById(relationId)
             .orElseThrow(() -> new IllegalArgumentException(
                     "Relation nicht gefunden: " + relationId));

     String oldValue = rel.getReferenceDesignator();
     String sanitized = (referenceDesignator == null
                         || referenceDesignator.isBlank())
                        ? null
                        : referenceDesignator.trim();

     rel.setReferenceDesignator(sanitized);
     rel.setBmkGeneratedBy(username + " (refdes-edit)");
     rel.setBmkGeneratedAt(LocalDateTime.now());

     StructureRelation saved = structureRelationRepository.save(rel);

     log.info("RefDes aktualisiert [relation={}] '{}' → '{}' by [{}]",
              relationId, oldValue, sanitized, username);

     return saved;
 }
}