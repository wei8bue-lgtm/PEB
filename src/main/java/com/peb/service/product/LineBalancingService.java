// src/main/java/com/peb/service/product/LineBalancingService.java
package com.peb.service.product;

import com.peb.model.product.ManufacturingBOM;
import com.peb.model.product.ManufacturingOperation;
import com.peb.model.product.PlantBOPNode;
import com.peb.model.product.ProductBOP;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.PlantBOPNodeRepository;
import com.peb.repository.product.ProductBOPRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineBalancingService {

    private final PlantBOPNodeRepository     nodeRepo;
    private final ManufacturingBOMRepository mfgBOMRepo;
    private final ProductBOPRepository       productBOPRepo;

    @Transactional(readOnly = true)
    public LineBalancingResult calculate(Long plantBopId,
                                         String plantBopName) {

        // ── Schritt 1: Knoten laden (ManyToOne per JOIN FETCH) ────
        List<PlantBOPNode> allNodes =
                nodeRepo.findAllByPlantBOPIdWithDetails(plantBopId);

        // ── Schritt 2: MfgBOMs sammeln ────────────────────────────
        // Collections (productBOP.manufacturingBOMs) separat laden
        List<ManufacturingBOM> allMfgBOMs =
                collectMfgBOMs(allNodes);

        log.debug("Line Balancing für PlantBOP {}: {} Knoten, "
                + "{} ManufacturingBOMs gefunden",
                plantBopId, allNodes.size(), allMfgBOMs.size());

        // ── Schritt 3: Operationen nach Workstation gruppieren ─────
        Map<String, List<ManufacturingOperation>> byStation =
                new LinkedHashMap<>();

        for (ManufacturingBOM mfg : allMfgBOMs) {
            // Operationen explizit laden
            ManufacturingBOM loaded = mfgBOMRepo
                    .findByIdWithDetails(mfg.getId())
                    .orElse(mfg);

            for (ManufacturingOperation op
                    : loaded.getOperations()) {
                String station =
                        (op.getWorkstation() != null
                         && !op.getWorkstation().isBlank())
                        ? op.getWorkstation()
                        : "Unassigned";
                byStation.computeIfAbsent(
                        station,
                        k -> new ArrayList<>()).add(op);
            }
        }

        // ── Schritt 4: Metriken berechnen ─────────────────────────
        List<WorkstationMetrics> metrics = new ArrayList<>();
        BigDecimal bottleneck = BigDecimal.ZERO;

        for (var entry : byStation.entrySet()) {
            BigDecimal target = sumField(entry.getValue(), true);
            BigDecimal actual = sumField(entry.getValue(), false);
            BigDecimal takt   = maxTakt(entry.getValue());

            WorkstationMetrics wm = new WorkstationMetrics(
                    entry.getKey(),
                    entry.getValue(),
                    target, actual, takt);
            metrics.add(wm);

            if (target.compareTo(bottleneck) > 0) {
                bottleneck = target;
            }
        }

        // ── Schritt 5: KPIs ───────────────────────────────────────
        BigDecimal totalWork = metrics.stream()
                .map(WorkstationMetrics::totalTarget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int n = metrics.size();
        BigDecimal efficiency = BigDecimal.ZERO;
        if (n > 0
                && bottleneck.compareTo(BigDecimal.ZERO) > 0) {
            efficiency = totalWork
                    .divide(
                            bottleneck.multiply(
                                    BigDecimal.valueOf(n)),
                            4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        BigDecimal balanceLoss =
                BigDecimal.valueOf(100).subtract(efficiency);

        log.info("Line Balancing '{}': Efficiency={}, "
               + "Bottleneck={}s, Stationen={}",
                plantBopName,
                efficiency.setScale(1, RoundingMode.HALF_UP),
                bottleneck, n);

        return new LineBalancingResult(
                plantBopName, metrics,
                bottleneck, totalWork,
                efficiency, balanceLoss);
    }

    // ── Private Hilfsmethoden ─────────────────────────────────────

    /**
     * Sammelt alle ManufacturingBOMs aus den Knoten.
     * ProductBOP.manufacturingBOMs wird per separatem Repository-
     * Aufruf geladen (kein JOIN FETCH auf Collection!).
     */
    private List<ManufacturingBOM> collectMfgBOMs(
            List<PlantBOPNode> nodes) {

        // LinkedHashMap → Reihenfolge + Duplikat-Vermeidung
        Map<Long, ManufacturingBOM> result = new LinkedHashMap<>();

        for (PlantBOPNode node : nodes) {

            // Direkte ManufacturingBOM am Knoten
            if (node.getManufacturingBOM() != null) {
                ManufacturingBOM mfg = node.getManufacturingBOM();
                result.putIfAbsent(mfg.getId(), mfg);
            }

            // ManufacturingBOMs via ProductBOP
            // → ProductBOP separat laden um LazyInit zu vermeiden
            if (node.getProductBOP() != null) {
                Long pbopId = node.getProductBOP().getId();
                ProductBOP pbop = productBOPRepo
                        .findByIdWithMfgBOMs(pbopId)
                        .orElse(node.getProductBOP());

                for (ManufacturingBOM mfg
                        : pbop.getManufacturingBOMs()) {
                    result.putIfAbsent(mfg.getId(), mfg);
                }
            }
        }

        return new ArrayList<>(result.values());
    }

    private BigDecimal sumField(
            List<ManufacturingOperation> ops,
            boolean useTarget) {
        return ops.stream()
                .map(op -> {
                    BigDecimal val = useTarget
                            ? op.getCycleTimeTarget()
                            : op.getCycleTimeActual();
                    return val != null ? val : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal maxTakt(
            List<ManufacturingOperation> ops) {
        return ops.stream()
                .map(op -> op.getLineTaktTime() != null
                        ? op.getLineTaktTime()
                        : BigDecimal.ZERO)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    // ── Result Records ────────────────────────────────────────────

    public record LineBalancingResult(
            String                   plantBOPName,
            List<WorkstationMetrics> stations,
            BigDecimal               bottleneck,
            BigDecimal               totalWork,
            BigDecimal               efficiency,
            BigDecimal               balanceLoss
    ) {}

    public record WorkstationMetrics(
            String                       name,
            List<ManufacturingOperation> operations,
            BigDecimal                   totalTarget,
            BigDecimal                   totalActual,
            BigDecimal                   taktTime
    ) {
        public BigDecimal idleTime(BigDecimal bottleneck) {
            return bottleneck.subtract(totalTarget)
                             .max(BigDecimal.ZERO);
        }

        public BigDecimal loadPct(BigDecimal bottleneck) {
            if (bottleneck.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return totalTarget
                    .divide(bottleneck, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .min(BigDecimal.valueOf(100));
        }

        public String loadCssClass(BigDecimal bottleneck) {
            double pct = loadPct(bottleneck).doubleValue();
            if (pct >= 90) return "load-red";
            if (pct >= 70) return "load-yellow";
            return "load-green";
        }
    }
}