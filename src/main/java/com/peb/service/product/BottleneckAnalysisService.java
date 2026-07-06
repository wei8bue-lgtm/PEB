// src/main/java/com/peb/service/product/BottleneckAnalysisService.java
package com.peb.service.product;

import com.peb.model.product.ManufacturingOperation;
import com.peb.service.product.LineBalancingService.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
public class BottleneckAnalysisService {

    // ── Schwellenwerte ────────────────────────────────────────────
    /** Ab diesem Wert gilt eine Station als überlastet */
    private static final BigDecimal OVERLOAD_THRESHOLD =
            BigDecimal.valueOf(100);

    /** Unter diesem Wert hat eine Station Aufnahmekapazität */
    private static final BigDecimal UNDERLOAD_THRESHOLD =
            BigDecimal.valueOf(80);

    // ── Haupt-Einstiegspunkt ──────────────────────────────────────
    public BottleneckReport analyse(LineBalancingResult result) {

        List<WorkstationMetrics> stations = result.stations();
        BigDecimal bottleneck = result.bottleneck();

        // 1. Überlastete Stationen (Istzeit > Sollzeit = > 100%)
        List<WorkstationMetrics> overloaded = stations.stream()
                .filter(ws -> ws.loadPct(bottleneck)
                        .compareTo(OVERLOAD_THRESHOLD) > 0)
                .sorted(Comparator.comparing(
                        (WorkstationMetrics ws) ->
                                ws.loadPct(bottleneck)).reversed())
                .toList();

        // 2. Stationen mit freier Kapazität (< 80%)
        List<WorkstationMetrics> underloaded = stations.stream()
                .filter(ws -> ws.loadPct(bottleneck)
                        .compareTo(UNDERLOAD_THRESHOLD) < 0)
                .sorted(Comparator.comparing(
                        (WorkstationMetrics ws) ->
                                ws.loadPct(bottleneck)))
                .toList();

        // 3. Vorschläge generieren
        List<BalancingSuggestion> suggestions = new ArrayList<>();
        for (WorkstationMetrics bottleneckWs : overloaded) {
            suggestions.addAll(generateSuggestions(
                    bottleneckWs, underloaded,
                    bottleneck, stations));
        }

        // Nach Verbesserungspotenzial sortieren
        suggestions.sort(Comparator.comparing(
                BalancingSuggestion::improvement).reversed());

        // 4. Simulation des besten Vorschlags
        SimulationResult simulation =
                simulate(stations, suggestions, bottleneck);

        log.info("Bottleneck-Analyse: {} überlastete, "
               + "{} unterlastete Stationen, "
               + "{} Vorschläge",
                overloaded.size(), underloaded.size(),
                suggestions.size());

        return new BottleneckReport(
                overloaded, underloaded,
                suggestions, simulation);
    }

    // ── Vorschläge generieren ─────────────────────────────────────
    private List<BalancingSuggestion> generateSuggestions(
            WorkstationMetrics bottleneckWs,
            List<WorkstationMetrics> underloaded,
            BigDecimal bottleneck,
            List<WorkstationMetrics> allStations) {

        List<BalancingSuggestion> result = new ArrayList<>();

        for (ManufacturingOperation op
                : bottleneckWs.operations()) {

            BigDecimal opTime =
                    op.getCycleTimeTarget() != null
                    ? op.getCycleTimeTarget()
                    : BigDecimal.ZERO;

            if (opTime.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // ── Vorschlag A: Operation verschieben ────────────────
            for (WorkstationMetrics target : underloaded) {

                // Neue Istzeit der Zielstation nach Verschiebung
                BigDecimal newTargetActual =
                        target.totalActual().add(opTime);

                // Neue Sollzeit der Zielstation
                BigDecimal newTargetCapacity =
                        target.totalTarget().add(opTime);

                // Zielstation darf nach Verschiebung
                // nicht selbst überlastet werden
                if (newTargetCapacity.compareTo(
                        BigDecimal.ZERO) > 0) {
                    BigDecimal newTargetLoad = newTargetActual
                            .divide(newTargetCapacity,
                                    4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    if (newTargetLoad.compareTo(
                            OVERLOAD_THRESHOLD) <= 0) {

                        BigDecimal improvement =
                                calculateImprovement(
                                        bottleneckWs, opTime);

                        result.add(new BalancingSuggestion(
                                SuggestionType.MOVE_OPERATION,
                                bottleneckWs.name(),
                                target.name(),
                                op,
                                "Operation '" + op.getName()
                                + "' (" + opTime + "s Sollzeit)"
                                + " von " + bottleneckWs.name()
                                + " → " + target.name()
                                + " verschieben.",
                                improvement,
                                BigDecimal.ZERO
                        ));
                    }
                }
            }

            // ── Vorschlag B: Zweite Person einsetzen ──────────────
            if (op.getWorkerCount() == null
                    || op.getWorkerCount() < 2) {

                // Mit 2. Person halbiert sich effektive Zeit
                BigDecimal reducedTime = opTime.divide(
                        BigDecimal.valueOf(2),
                        3, RoundingMode.HALF_UP);
                BigDecimal improvement =
                        opTime.subtract(reducedTime);

                result.add(new BalancingSuggestion(
                        SuggestionType.ADD_WORKER,
                        bottleneckWs.name(),
                        bottleneckWs.name(),
                        op,
                        "2. Person für '" + op.getName()
                        + "' einsetzen. "
                        + "Zeitreduktion ca. "
                        + improvement + "s.",
                        improvement,
                        BigDecimal.ZERO
                ));
            }

            // ── Vorschlag C: Prozessverbesserung ──────────────────
            // Istzeit > Sollzeit = Verschwendung vorhanden
            if (op.getCycleTimeActual() != null
                    && op.getCycleTimeTarget() != null
                    && op.getCycleTimeActual()
                         .compareTo(op.getCycleTimeTarget()) > 0) {

                BigDecimal gap =
                        op.getCycleTimeActual()
                          .subtract(op.getCycleTimeTarget());

                result.add(new BalancingSuggestion(
                        SuggestionType.PROCESS_IMPROVEMENT,
                        bottleneckWs.name(),
                        bottleneckWs.name(),
                        op,
                        "Prozessverbesserung bei '"
                        + op.getName()
                        + "': Istzeit ("
                        + op.getCycleTimeActual()
                        + "s) liegt " + gap
                        + "s über Sollzeit ("
                        + op.getCycleTimeTarget()
                        + "s). Verschwendung analysieren (MUDA).",
                        gap,
                        BigDecimal.ZERO
                ));
            }
        }

        return result;
    }

    private BigDecimal calculateImprovement(
            WorkstationMetrics ws,
            BigDecimal opTime) {
        // Wie viel Sekunden wird die Überlast reduziert?
        BigDecimal currentOverload =
                ws.totalActual().subtract(ws.totalTarget());
        return currentOverload.min(opTime).max(BigDecimal.ZERO);
    }

    // ── Simulation ────────────────────────────────────────────────
    private SimulationResult simulate(
            List<WorkstationMetrics> stations,
            List<BalancingSuggestion> suggestions,
            BigDecimal currentBottleneck) {

        if (suggestions.isEmpty()) {
            return new SimulationResult(
                    currentBottleneck,
                    currentBottleneck,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }

        BalancingSuggestion best = suggestions.get(0);

        // Simulierter neuer Bottleneck
        BigDecimal simulatedBottleneck =
                currentBottleneck
                        .subtract(best.improvement())
                        .max(BigDecimal.ZERO);

        // Neue Efficiency nach bestem Vorschlag
        BigDecimal totalWork = stations.stream()
                .map(WorkstationMetrics::totalTarget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int n = stations.size();
        BigDecimal newEfficiency = BigDecimal.ZERO;
        if (n > 0 && simulatedBottleneck
                .compareTo(BigDecimal.ZERO) > 0) {
            newEfficiency = totalWork
                    .divide(
                            simulatedBottleneck.multiply(
                                    BigDecimal.valueOf(n)),
                            4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return new SimulationResult(
                currentBottleneck,
                simulatedBottleneck,
                best.improvement(),
                newEfficiency);
    }

    // ── Enums & Records ───────────────────────────────────────────

    public enum SuggestionType {
        MOVE_OPERATION(     "⇄ Operation verschieben", "primary"),
        ADD_WORKER(         "👷 Zweite Person",         "warning"),
        PROCESS_IMPROVEMENT("📉 Prozess verbessern",   "danger");

        public final String label;
        public final String cssClass;

        SuggestionType(String label, String cssClass) {
            this.label    = label;
            this.cssClass = cssClass;
        }
    }

    public record BalancingSuggestion(
            SuggestionType         type,
            String                 fromStation,
            String                 toStation,
            ManufacturingOperation operation,
            String                 description,
            BigDecimal             improvement,
            BigDecimal             newEfficiency
    ) {}

    public record SimulationResult(
            BigDecimal currentBottleneck,
            BigDecimal simulatedBottleneck,
            BigDecimal bottleneckReduction,
            BigDecimal newEfficiency
    ) {}

    public record BottleneckReport(
            List<WorkstationMetrics>  overloadedStations,
            List<WorkstationMetrics>  underloadedStations,
            List<BalancingSuggestion> suggestions,
            SimulationResult          simulation
    ) {}
}