// src/main/java/com/peb/service/product/OperationPdfService.java
package com.peb.service.product;

import com.peb.model.product.ManufacturingBOM;
import com.peb.model.product.ManufacturingOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationPdfService {

    // ── GEÄNDERT: nutzt ManufacturingBOMService statt Repo ───────
    private final ManufacturingBOMService mfgBOMService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Transactional(readOnly = true)
    public String generateWorkInstructionHtml(Long mfgBomId) {

        // VORHER (fehlerhaft):
        // mfgBOMRepo.findByIdWithOperations(mfgBomId)
        //
        // JETZT: über Service – lädt Operationen korrekt
        ManufacturingBOM bom =
                mfgBOMService.findByIdWithOperationsOnly(mfgBomId);

        return buildHtml(bom);
    }

    // ── Rest der Klasse bleibt unverändert ───────────────────────

    private String buildHtml(ManufacturingBOM bom) {
        StringBuilder sb = new StringBuilder();
        sb.append(htmlHead(bom.getName()));
        sb.append(htmlHeader(bom));
        sb.append(htmlMetaTable(bom));
        sb.append(htmlSummary(bom));

        if (bom.getOperations().isEmpty()) {
            sb.append("""
                <div style="text-align:center;color:#6c757d;
                            padding:3rem;border:2px dashed #dee2e6;
                            border-radius:6px;margin:1rem 0;">
                    Keine Operationen vorhanden.
                </div>
                """);
        }

        for (ManufacturingOperation op : bom.getOperations()) {
            sb.append(htmlOperationCard(op));
        }

        sb.append(htmlFooter(bom));
        sb.append("</body></html>");
        return sb.toString();
    }

    private String htmlHead(String title) {
        return """
            <!DOCTYPE html>
            <html lang="de">
            <head>
            <meta charset="UTF-8">
            <title>Arbeitsanweisung – %s</title>
            <style>
              *{box-sizing:border-box}
              body{font-family:'Segoe UI',Arial,sans-serif;
                   font-size:10pt;margin:0;
                   background:white;color:#212529}
              .page{max-width:210mm;margin:0 auto;padding:15mm}
              .doc-header{background:#003d7a;color:white;
                          padding:12px 16px;
                          border-radius:6px 6px 0 0;
                          display:flex;
                          justify-content:space-between;
                          align-items:center}
              .doc-header h1{margin:0;font-size:16pt}
              .meta{width:100%%;border-collapse:collapse;
                    margin-bottom:16px;
                    border:1px solid #dee2e6;border-top:none}
              .meta td{padding:5px 10px;
                       border-bottom:1px solid #dee2e6}
              .meta td:first-child{font-weight:600;
                                   background:#f0f4ff;
                                   width:30%%;color:#003d7a}
              .op-card{border:1px solid #dee2e6;
                       border-radius:6px;margin-bottom:14px;
                       page-break-inside:avoid}
              .op-head{background:#0056b3;color:white;
                       padding:8px 14px;
                       border-radius:5px 5px 0 0;
                       display:flex;
                       justify-content:space-between;
                       align-items:center}
              .op-head .title{font-weight:bold;font-size:11pt}
              .op-body{padding:10px 14px}
              .time-grid{display:grid;
                         grid-template-columns:repeat(4,1fr);
                         gap:8px;margin:8px 0;
                         background:#f8f9fa;padding:10px;
                         border-radius:4px;
                         border:1px solid #dee2e6}
              .tc{text-align:center}
              .tc .lbl{font-size:8pt;color:#6c757d;
                       text-transform:uppercase}
              .tc .val{font-size:14pt;font-weight:700;
                       color:#003d7a;line-height:1.2}
              .tc .unit{font-size:8pt;color:#6c757d}
              .box{border-radius:4px;padding:9px 12px;
                   margin-top:8px;font-size:9.5pt}
              .box-info{background:#e7f3ff;
                        border-left:4px solid #0056b3}
              .box-warn{background:#fff3cd;
                        border-left:4px solid #ffc107}
              .box-danger{background:#f8d7da;
                          border-left:4px solid #dc3545}
              .box-title{font-weight:700;margin-bottom:4px}
              .footer{margin-top:20px;padding-top:8px;
                      border-top:1px solid #dee2e6;
                      font-size:8pt;color:#6c757d;
                      text-align:center}
              @media print{
                .page{padding:0;max-width:100%%;}
                .op-card{page-break-inside:avoid}}
            </style>
            </head>
            <body><div class="page">
            """.formatted(title);
    }

    private String htmlHeader(ManufacturingBOM bom) {
        return """
            <div class="doc-header">
              <div>
                <h1>🔧 Arbeitsanweisung</h1>
                <div style="font-size:9pt;opacity:.8;">
                  %s | Version %s
                </div>
              </div>
              <div style="text-align:right;font-size:9pt;
                          opacity:.85;">
                Erstellt von: %s<br>
                Druckdatum: %s
              </div>
            </div>
            """.formatted(
                bom.getName(),
                bom.getBomVersion(),
                bom.getCreatedBy() != null
                        ? bom.getCreatedBy() : "—",
                LocalDateTime.now().format(FMT));
    }

    private String htmlMetaTable(ManufacturingBOM bom) {
        return """
            <table class="meta">
              <tr><td>Bezeichnung</td><td>%s</td></tr>
              <tr><td>Version</td><td>%s</td></tr>
              <tr><td>Beschreibung</td><td>%s</td></tr>
              <tr><td>Anzahl Operationen</td><td>%d</td></tr>
            </table>
            """.formatted(
                bom.getName(),
                bom.getBomVersion(),
                bom.getDescription() != null
                        ? bom.getDescription() : "—",
                bom.getOperations().size());
    }

    private String htmlSummary(ManufacturingBOM bom) {
        BigDecimal totalTarget = bom.getOperations().stream()
                .map(o -> o.getCycleTimeTarget() != null
                        ? o.getCycleTimeTarget()
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActual = bom.getOperations().stream()
                .map(o -> o.getCycleTimeActual() != null
                        ? o.getCycleTimeActual()
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return """
            <table style="width:100%%;border-collapse:collapse;
                          margin-bottom:16px;">
              <tr style="background:#003d7a;color:white;">
                <th style="padding:6px 10px;">
                    Gesamt-Sollzeit (s)</th>
                <th style="padding:6px 10px;">
                    Gesamt-Istzeit (s)</th>
                <th style="padding:6px 10px;">
                    Operationen</th>
              </tr>
              <tr style="border:1px solid #dee2e6;">
                <td style="padding:6px 10px;text-align:center;
                           font-weight:700;">%s</td>
                <td style="padding:6px 10px;text-align:center;
                           font-weight:700;">%s</td>
                <td style="padding:6px 10px;text-align:center;
                           font-weight:700;">%d</td>
              </tr>
            </table>
            """.formatted(
                fmt(totalTarget),
                fmt(totalActual),
                bom.getOperations().size());
    }

    private String htmlOperationCard(ManufacturingOperation op) {
        String num = op.getOperationNumber() != null
                ? op.getOperationNumber() + " – " : "";
        StringBuilder card = new StringBuilder();

        card.append("""
            <div class="op-card">
              <div class="op-head">
                <div class="title">%s %s%s</div>
                <div style="font-size:9pt;opacity:.85;">
                  %s | WS: %s | %d MA
                </div>
              </div>
              <div class="op-body">
            """.formatted(
                op.getOperationType().getIcon(),
                num,
                op.getName(),
                op.getOperationType().getDisplayName(),
                op.getWorkstation() != null
                        ? op.getWorkstation() : "—",
                op.getWorkerCount() != null
                        ? op.getWorkerCount() : 1));

        card.append("""
            <div class="time-grid">
              <div class="tc">
                <div class="lbl">Sollzeit</div>
                <div class="val">%s</div>
                <div class="unit">Sekunden</div>
              </div>
              <div class="tc">
                <div class="lbl">Istzeit</div>
                <div class="val">%s</div>
                <div class="unit">Sekunden</div>
              </div>
              <div class="tc">
                <div class="lbl">Linientakt</div>
                <div class="val">%s</div>
                <div class="unit">Sekunden</div>
              </div>
              <div class="tc">
                <div class="lbl">Wartezeit</div>
                <div class="val">%s</div>
                <div class="unit">Sekunden</div>
              </div>
            </div>
            """.formatted(
                fmt(op.getCycleTimeTarget()),
                fmt(op.getCycleTimeActual()),
                fmt(op.getLineTaktTime()),
                fmt(op.getWaitTime())));

        if (op.getToolsRequired() != null
                && !op.getToolsRequired().isBlank()) {
            card.append("""
                <div class="box box-info">
                  <div class="box-title">🔧 Werkzeuge</div>
                  %s
                </div>
                """.formatted(op.getToolsRequired()));
        }
        if (op.getWorkInstructions() != null
                && !op.getWorkInstructions().isBlank()) {
            card.append("""
                <div class="box box-warn">
                  <div class="box-title">📋 Arbeitsschritte</div>
                  %s
                </div>
                """.formatted(
                    op.getWorkInstructions()
                      .replace("\n", "<br>")));
        }
        if (op.getSafetyNotes() != null
                && !op.getSafetyNotes().isBlank()) {
            card.append("""
                <div class="box box-danger">
                  <div class="box-title">⚠️ Sicherheit</div>
                  %s
                </div>
                """.formatted(
                    op.getSafetyNotes()
                      .replace("\n", "<br>")));
        }

        card.append("</div></div>");
        return card.toString();
    }

    private String htmlFooter(ManufacturingBOM bom) {
        return """
            <div class="footer">
              PEB – Production Engineering Backbone |
              MfgBOM-ID: %d |
              Gedruckt: %s
            </div></div>
            """.formatted(
                bom.getId(),
                LocalDateTime.now().format(FMT));
    }

    private String fmt(BigDecimal v) {
        return v == null ? "—"
                : v.stripTrailingZeros().toPlainString();
    }
}