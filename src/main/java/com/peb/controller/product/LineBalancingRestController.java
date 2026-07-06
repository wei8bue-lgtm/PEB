// src/main/java/com/peb/controller/product/LineBalancingRestController.java
package com.peb.controller.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.peb.model.product.ManufacturingOperation;
import com.peb.model.product.PlantBOP;
import com.peb.repository.product.PlantBOPRepository;
import com.peb.service.product.BottleneckAnalysisService;
import com.peb.service.product.LineBalancingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/plant-bop")
@RequiredArgsConstructor
@Slf4j
public class LineBalancingRestController {

    private final PlantBOPRepository        plantBOPRepo;
    private final LineBalancingService      balancingService;
    private final BottleneckAnalysisService bottleneckService;

    // ── GET /api/plant-bop/{id}/balancing ────────────────────────
    @GetMapping("/{id}/balancing")
    public ResponseEntity<LineBalancingJson> getBalancing(
            @PathVariable Long id) {

        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plant BOP nicht gefunden: " + id));

        LineBalancingService.LineBalancingResult result =
                balancingService.calculate(id, plantBOP.getName());

        BottleneckAnalysisService.BottleneckReport report =
                bottleneckService.analyse(result);

        LineBalancingJson json =
                LineBalancingJson.from(result, report);

        log.info("JSON-Export Line Balancing für PlantBOP {}",
                id);

        return ResponseEntity.ok(json);
    }

    // ── Nur KPIs (kompakt) ────────────────────────────────────────
    @GetMapping("/{id}/balancing/kpis")
    public ResponseEntity<KpiJson> getKpis(
            @PathVariable Long id) {

        PlantBOP plantBOP = plantBOPRepo.findById(id)
                .orElseThrow();

        LineBalancingService.LineBalancingResult result =
                balancingService.calculate(id, plantBOP.getName());

        return ResponseEntity.ok(KpiJson.from(result));
    }

    // ── Alle Plant BOPs als Übersicht ─────────────────────────────
    @GetMapping("/balancing/overview")
    public ResponseEntity<List<KpiJson>> getOverview() {

        List<KpiJson> overview = plantBOPRepo.findAll()
                .stream()
                .map(p -> {
                    LineBalancingService.LineBalancingResult r =
                            balancingService.calculate(
                                    p.getId(), p.getName());
                    return KpiJson.from(r);
                })
                .toList();

        return ResponseEntity.ok(overview);
    }

    // ════════════════════════════════════════════════════════════
    // JSON-Strukturen (Records → Jackson serialisiert automatisch)
    // ════════════════════════════════════════════════════════════

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LineBalancingJson(
            // ── Meta ─────────────────────────────────────────────
            String            exportedAt,
            String            plantBOPName,

            // ── KPIs ─────────────────────────────────────────────
            KpiJson           kpis,

            // ── Stationen ────────────────────────────────────────
            List<StationJson> stations,

            // ── Bottleneck-Analyse ────────────────────────────────
            AnalysisJson      analysis
    ) {
        static LineBalancingJson from(
                LineBalancingService.LineBalancingResult result,
                BottleneckAnalysisService.BottleneckReport report) {

            return new LineBalancingJson(
                    LocalDateTime.now().format(
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    result.plantBOPName(),
                    KpiJson.from(result),
                    result.stations().stream()
                            .map(ws -> StationJson.from(
                                    ws, result.bottleneck()))
                            .toList(),
                    AnalysisJson.from(report, result.bottleneck())
            );
        }
    }

    // ── KPIs ──────────────────────────────────────────────────────
    public record KpiJson(
            String     plantBOPName,
            double     efficiencyPct,
            double     balanceLossPct,
            double     bottleneckSeconds,
            double     totalWorkSeconds,
            int        stationCount,
            int        overloadedStations
    ) {
        static KpiJson from(
                LineBalancingService.LineBalancingResult r) {

            long overloaded = r.stations().stream()
                    .filter(ws -> ws.isOverloaded(r.bottleneck()))
                    .count();

            return new KpiJson(
                    r.plantBOPName(),
                    r.efficiency().setScale(
                            2, RoundingMode.HALF_UP).doubleValue(),
                    r.balanceLoss().setScale(
                            2, RoundingMode.HALF_UP).doubleValue(),
                    r.bottleneck().doubleValue(),
                    r.totalWork().doubleValue(),
                    r.stations().size(),
                    (int) overloaded
            );
        }
    }

    // ── Station ───────────────────────────────────────────────────
    public record StationJson(
            String            name,
            boolean           overloaded,
            double            targetSeconds,
            double            actualSeconds,
            double            idleTimeSeconds,
            double            loadPct,
            String            loadStatus,    // OK / WARNING / OVERLOADED
            int               operationCount,
            List<OperationJson> operations
    ) {
        static StationJson from(
                LineBalancingService.WorkstationMetrics ws,
                BigDecimal bottleneck) {

            double load = ws.loadPct(bottleneck)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();

            String status = ws.isOverloaded(bottleneck)
                    ? "OVERLOADED"
                    : load >= 80 ? "WARNING" : "OK";

            return new StationJson(
                    ws.name(),
                    ws.isOverloaded(bottleneck),
                    ws.totalTarget().doubleValue(),
                    ws.totalActual().doubleValue(),
                    ws.idleTime(bottleneck)
                      .setScale(3, RoundingMode.HALF_UP)
                      .doubleValue(),
                    load,
                    status,
                    ws.operations().size(),
                    ws.operations().stream()
                            .map(OperationJson::from)
                            .toList()
            );
        }
    }

    // ── Operation ─────────────────────────────────────────────────
    public record OperationJson(
            Long   id,
            String operationNumber,
            String name,
            String type,
            String workstation,
            double cycleTimeTarget,
            double cycleTimeActual,
            double lineTaktTime,
            int    workerCount
    ) {
        static OperationJson from(ManufacturingOperation op) {
            return new OperationJson(
                    op.getId(),
                    op.getOperationNumber(),
                    op.getName(),
                    op.getOperationType() != null
                            ? op.getOperationType().name()
                            : null,
                    op.getWorkstation(),
                    op.getCycleTimeTarget() != null
                            ? op.getCycleTimeTarget().doubleValue()
                            : 0.0,
                    op.getCycleTimeActual() != null
                            ? op.getCycleTimeActual().doubleValue()
                            : 0.0,
                    op.getLineTaktTime() != null
                            ? op.getLineTaktTime().doubleValue()
                            : 0.0,
                    op.getWorkerCount() != null
                            ? op.getWorkerCount() : 1
            );
        }
    }

    // ── Bottleneck-Analyse ────────────────────────────────────────
    public record AnalysisJson(
            List<String>           overloadedStations,
            List<String>           underloadedStations,
            List<SuggestionJson>   suggestions,
            SimulationJson         simulation
    ) {
        static AnalysisJson from(
                BottleneckAnalysisService.BottleneckReport report,
                BigDecimal bottleneck) {

            return new AnalysisJson(
                    report.overloadedStations().stream()
                            .map(LineBalancingService
                                    .WorkstationMetrics::name)
                            .toList(),
                    report.underloadedStations().stream()
                            .map(LineBalancingService
                                    .WorkstationMetrics::name)
                            .toList(),
                    report.suggestions().stream()
                            .map(SuggestionJson::from)
                            .toList(),
                    SimulationJson.from(report.simulation())
            );
        }
    }

    // ── Vorschlag ─────────────────────────────────────────────────
    public record SuggestionJson(
            String type,
            String fromStation,
            String toStation,
            String operationName,
            String description,
            double improvementSeconds
    ) {
        static SuggestionJson from(
                BottleneckAnalysisService.BalancingSuggestion s) {
            return new SuggestionJson(
                    s.type().name(),
                    s.fromStation(),
                    s.toStation(),
                    s.operation().getName(),
                    s.description(),
                    s.improvement().doubleValue()
            );
        }
    }

    // ── Simulation ────────────────────────────────────────────────
    public record SimulationJson(
            double currentBottleneckSeconds,
            double simulatedBottleneckSeconds,
            double bottleneckReductionSeconds,
            double newEfficiencyPct
    ) {
        static SimulationJson from(
                BottleneckAnalysisService.SimulationResult s) {
            return new SimulationJson(
                    s.currentBottleneck().doubleValue(),
                    s.simulatedBottleneck().doubleValue(),
                    s.bottleneckReduction().doubleValue(),
                    s.newEfficiency().setScale(
                            2, RoundingMode.HALF_UP).doubleValue()
            );
        }
    }
}