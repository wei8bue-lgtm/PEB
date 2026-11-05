package com.peb.service.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanningValidationService {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000001");

    public PlanningData.NumberValidationReport numberMappings(PlanningData data, String projectName, int revision) {
        Map<String, Long> revisionCounts = PlanningRules.articleOptions(data).stream().collect(Collectors.groupingBy(
                PlanningRules.ArticleOption::number, Collectors.mapping(PlanningRules.ArticleOption::revision,
                        Collectors.collectingAndThen(Collectors.toSet(), values -> (long) values.size()))));
        List<PlanningData.NumberValidationRow> rows = data.articleMasters.stream().map(article -> {
            boolean incomplete = article.missingMasterData() || blank(article.boschSapNumber());
            String details = blank(article.boschSapNumber()) ? "Bosch-SAP-Nummer fehlt" : "ERP-Revision ist einer Bosch-SAP-Nummer zugeordnet";
            if (revisionCounts.getOrDefault(article.customerErpNumber(), 0L) > 1) details += "; ERP-Nummer besitzt mehrere Revisionen";
            if (blank(article.customerRevision())) details += "; Kundenrevision fehlt";
            return new PlanningData.NumberValidationRow(article.customerErpNumber(), article.customerRevision(), article.boschSapNumber(),
                    article.boschSapRevision(), article.description(), incomplete ? "INCOMPLETE_MAPPING" : "MAPPED", details);
        }).toList();
        return new PlanningData.NumberValidationReport(projectName, revision, LocalDateTime.now().toString(), rows);
    }

    public PlanningData.MaterialValidationReport materialUsage(PlanningData data, String projectName, int revision,
                                                                 String zoneFilter, String workplaceFilter) {
        ProcessPlanningRules.validate(data);
        Map<Integer, List<PlanningData.StructureRow>> children = children(data);
        Map<String, BigDecimal> expected = new TreeMap<>();
        Map<String, BigDecimal> actual = new TreeMap<>();
        Set<String> workplaceMaterialFilter = new HashSet<>();
        Map<String, String> descriptions = new HashMap<>();
        Map<String, PlanningData.ArticleMaster> masters = data.articleMasters.stream().collect(Collectors.toMap(
                article -> key(article.customerErpNumber(), article.customerRevision()), Function.identity(), (first, second) -> first));

        for (var zone : data.zones.stream().filter(zone -> blank(zoneFilter) || zone.get("A").equals(zoneFilter)).toList()) {
            String target = key(zone.get("D"), zone.get("G"));
            for (var root : data.structure.stream().filter(row -> same(row.cells().get("J"), row.cells().get("M"), zone.get("D"), zone.get("G"))).toList()) {
                collectExpected(data, zone, root, BigDecimal.ONE, children, target, expected, descriptions);
            }
        }

        Map<String, PlanningData.ProcessTemplateRevision> templates = data.processTemplates.stream().collect(Collectors.toMap(
                item -> key(item.templateId(), Integer.toString(item.revision())), Function.identity(), (first, second) -> second));
        Map<String, PlanningData.Workplace> workplaces = data.workplaces.stream().collect(Collectors.toMap(
                PlanningData.Workplace::id, Function.identity(), (first, second) -> first));
        for (var use : data.processUses) {
            if (blank(use.targetErpNumber()) || (!blank(zoneFilter) && !hasTargetInZone(data, zoneFilter, use.targetErpNumber(), use.targetRevision()))) continue;
            String target = key(use.targetErpNumber(), use.targetRevision());
            var template = templates.get(key(use.templateId(), Integer.toString(use.templateRevision())));
            if (template == null) continue;
            for (var operation : template.operations()) {
                var workplace = workplaces.get(operation.workplaceId());
                if (!blank(zoneFilter) && (workplace == null || !zoneFilter.equals(workplace.zoneNumber()))) continue;
                for (var resource : safeResources(operation)) {
                    if (resource.type() != PlanningData.ResourceType.MATERIAL || blank(resource.articleNumber())) continue;
                    String component = key(resource.articleNumber(), resource.articleRevision());
                    String composite = target + "\u0001" + component;
                    if (blank(workplaceFilter) || workplaceFilter.equals(operation.workplaceId())) workplaceMaterialFilter.add(composite);
                    actual.merge(composite, nz(resource.quantity()).multiply(BigDecimal.valueOf(operation.executionsPerUnit())), BigDecimal::add);
                    if (!blank(resource.description())) descriptions.putIfAbsent(component, resource.description());
                }
            }
        }

        Set<String> identities = new TreeSet<>(); identities.addAll(expected.keySet()); identities.addAll(actual.keySet());
        List<PlanningData.MaterialValidationRow> rows = identities.stream().map(identity -> {
            String[] parts = identity.split("\u0001", -1);
            String[] target = parts[0].split("\u0000", -1); String[] component = parts[1].split("\u0000", -1);
            BigDecimal expectedQuantity = expected.getOrDefault(identity, BigDecimal.ZERO);
            BigDecimal actualQuantity = actual.getOrDefault(identity, BigDecimal.ZERO);
            BigDecimal variance = actualQuantity.subtract(expectedQuantity);
            String status = variance.abs().compareTo(TOLERANCE) <= 0 ? "MATCH" : variance.signum() < 0 ? "SHORTAGE" : "EXCESS";
            var master = masters.get(parts[1]);
            return new PlanningData.MaterialValidationRow(target[0], target[1], component[0], component[1],
                    master == null ? descriptions.getOrDefault(parts[1], "") : master.description(),
                    expectedQuantity, actualQuantity, variance, status);
        }).toList();
        List<PlanningData.MaterialValidationRow> filtered = blank(workplaceFilter) ? rows : rows.stream()
            .filter(row -> workplaceMaterialFilter.contains(key(row.targetErpNumber(), row.targetRevision()) + "\u0001"
                + key(row.articleNumber(), row.articleRevision()))).toList();
        return new PlanningData.MaterialValidationReport(projectName, revision, LocalDateTime.now().toString(), zoneFilter, workplaceFilter, filtered);
    }

    public PlanningData.ZoneSupplyValidationReport zoneSupply(PlanningData data, String projectName, int revision) {
        ProcessPlanningRules.validate(data);
        Map<String, List<Map<String, String>>> zones = data.zones.stream().collect(Collectors.groupingBy(row -> row.get("A")));
        Map<Integer, List<PlanningData.StructureRow>> children = children(data);
        List<PlanningData.ZoneSupplyValidationRow> rows = new ArrayList<>();
        for (var delivery : data.deliveries) {
            List<Map<String, String>> sources = zones.getOrDefault(delivery.sourceZone(), List.of());
            List<Map<String, String>> destinations = zones.getOrDefault(delivery.destinationZone(), List.of());
            Map<String, String> source = sources.stream().filter(row -> same(row.get("D"), row.get("G"), delivery.customerErpNumber(), delivery.customerRevision())).findFirst().orElse(null);
            if (source == null || destinations.isEmpty()) continue;
            String destinationName = destinations.get(0).get("B");
            BigDecimal supplied = hourlyRate(source.get("J"), source.get("K"));
            BigDecimal required = BigDecimal.ZERO;
            BigDecimal destinationTakt = BigDecimal.ZERO;
            for (Map<String, String> destination : destinations) {
                BigDecimal assembliesPerHour = hourlyRate(destination.get("J"), destination.get("K"));
                BigDecimal requiredPerAssembly = deliveredNeed(data, destination, delivery.customerErpNumber(), delivery.customerRevision(), children);
                required = required.add(requiredPerAssembly.multiply(assembliesPerHour));
                if (destinationTakt.signum() == 0) destinationTakt = minutesToSeconds(destination.get("K"));
            }
            BigDecimal variance = supplied.subtract(required);
            String status = variance.abs().compareTo(TOLERANCE) <= 0 ? "MATCH" : variance.signum() < 0 ? "SHORTAGE" : "EXCESS";
            rows.add(new PlanningData.ZoneSupplyValidationRow(delivery.sourceZone(), source.get("B"), delivery.destinationZone(), destinationName,
                    delivery.customerErpNumber(), delivery.customerRevision(), supplied, required, variance,
                    minutesToSeconds(source.get("K")), destinationTakt, status));
        }
        return new PlanningData.ZoneSupplyValidationReport(projectName, revision, LocalDateTime.now().toString(), rows);
    }

    public PlanningData.ResourceReport resources(PlanningData data, String projectName, int revision, String kind,
            String zoneFilter, String workplaceFilter, String targetNumber, String targetRevision) {
        ProcessPlanningRules.validate(data);
        Set<PlanningData.ResourceType> included = switch (value(kind).toLowerCase(Locale.ROOT)) {
            case "material", "materials" -> Set.of(PlanningData.ResourceType.MATERIAL);
            case "tool", "tools" -> Set.of(PlanningData.ResourceType.TOOL);
            case "aid", "aids" -> Set.of(PlanningData.ResourceType.AID);
            case "all", "resources" -> EnumSet.allOf(PlanningData.ResourceType.class);
            default -> throw new IllegalArgumentException("Reporttyp muss materials, tools, aids oder all sein.");
        };
        Map<String, PlanningData.ProcessTemplateRevision> templates = data.processTemplates.stream().collect(Collectors.toMap(
                item -> key(item.templateId(), Integer.toString(item.revision())), Function.identity(), (first, second) -> second));
        Map<String, PlanningData.Workplace> workplaces = data.workplaces.stream().collect(Collectors.toMap(
                PlanningData.Workplace::id, Function.identity(), (first, second) -> first));
        Map<String, Accumulator> accumulated = new TreeMap<>();
        for (var use : data.processUses) {
            if (!blank(targetNumber) && !same(use.targetErpNumber(), use.targetRevision(), targetNumber, targetRevision)) continue;
            if (!blank(zoneFilter) && !useBelongsToZone(data, use, zoneFilter, templates, workplaces)) continue;
            var template = templates.get(key(use.templateId(), Integer.toString(use.templateRevision())));
            if (template == null) continue;
            for (var operation : template.operations()) {
                if (!blank(workplaceFilter) && !workplaceFilter.equals(operation.workplaceId())) continue;
                var workplace = workplaces.get(operation.workplaceId());
                if (!blank(zoneFilter) && (workplace == null || !zoneFilter.equals(workplace.zoneNumber()))) continue;
                for (var resource : safeResources(operation)) {
                    if (!included.contains(resource.type())) continue;
                    String identity = String.join("\u0000", resource.type().name(), value(resource.articleNumber()), value(resource.articleRevision()),
                            value(resource.specialNumber()), value(use.templateId()), value(operation.id()), value(operation.workplaceId()),
                            value(use.targetErpNumber()), value(use.targetRevision()), value(resource.containerType()), value(resource.packagingUnit()),
                            value(resource.containerAssignment()), value(resource.unit()));
                        Accumulator item = accumulated.computeIfAbsent(identity, ignored -> new Accumulator(resource, operation, use, template.name(), workplace));
                    item.required = item.required.add(nz(resource.quantity()).multiply(BigDecimal.valueOf(operation.executionsPerUnit())));
                }
            }
        }
        List<PlanningData.ResourceReportRow> rows = accumulated.values().stream().map(item -> {
            BigDecimal capacity = item.resource.unitsPerContainer();
            BigDecimal containers = capacity == null || capacity.signum() <= 0 ? null : item.required.divide(capacity, 0, RoundingMode.CEILING);
            return new PlanningData.ResourceReportRow(item.resource.type().name(), value(item.resource.articleNumber()), value(item.resource.articleRevision()),
                    value(item.resource.specialNumber()), value(item.resource.description()), item.templateName, item.operation.id(), item.operation.name(),
                    value(item.resource.containerAssignment()), value(item.operation.workplaceId()),
                    item.workplace == null ? "Nicht zugeordnet" : item.workplace.name(), value(item.use.targetErpNumber()),
                    value(item.use.targetRevision()), value(item.resource.unit()), item.required, value(item.resource.containerType()),
                    capacity, containers, value(item.resource.packagingUnit()));
        }).toList();
        return new PlanningData.ResourceReport(projectName, revision, LocalDateTime.now().toString(), kind, zoneFilter, workplaceFilter, rows);
    }

    private void collectExpected(PlanningData data, Map<String, String> zone, PlanningData.StructureRow parent, BigDecimal multiplier,
            Map<Integer, List<PlanningData.StructureRow>> children, String target, Map<String, BigDecimal> expected, Map<String, String> descriptions) {
        Map<String, Map<String, String>> boundaries = new HashMap<>();
        data.zones.forEach(candidate -> boundaries.put(key(candidate.get("D"), candidate.get("G")), candidate));
        for (var child : children.getOrDefault(parent.sourceRow(), List.of())) {
            BigDecimal total = multiplier.multiply(parseQuantity(child.cells().get("N"), child.sourceRow()));
            Map<String, String> boundary = boundaries.get(key(child.cells().get("J"), child.cells().get("M")));
            boolean suppliedHere = boundary != null && boundary.get("P").equals(zone.get("A"));
            if (suppliedHere || !children.containsKey(child.sourceRow())) {
                String component = key(child.cells().get("J"), child.cells().get("M"));
                expected.merge(target + "\u0001" + component, total, BigDecimal::add);
                descriptions.putIfAbsent(component, child.cells().get("K"));
            } else collectExpected(data, zone, child, total, children, target, expected, descriptions);
        }
    }

    private BigDecimal deliveredNeed(PlanningData data, Map<String, String> zone, String number, String articleRevision,
                                     Map<Integer, List<PlanningData.StructureRow>> children) {
        BigDecimal[] sum = { BigDecimal.ZERO };
        for (var root : data.structure.stream().filter(row -> same(row.cells().get("J"), row.cells().get("M"), zone.get("D"), zone.get("G"))).toList()) {
            collectDelivered(data, zone, root, BigDecimal.ONE, number, articleRevision, children, sum);
        }
        return sum[0];
    }

    private void collectDelivered(PlanningData data, Map<String, String> zone, PlanningData.StructureRow parent,
            BigDecimal multiplier, String number, String articleRevision, Map<Integer, List<PlanningData.StructureRow>> children, BigDecimal[] sum) {
        Map<String, Map<String, String>> boundaries = new HashMap<>();
        data.zones.forEach(candidate -> boundaries.put(key(candidate.get("D"), candidate.get("G")), candidate));
        for (var child : children.getOrDefault(parent.sourceRow(), List.of())) {
            BigDecimal total = multiplier.multiply(parseQuantity(child.cells().get("N"), child.sourceRow()));
            Map<String, String> boundary = boundaries.get(key(child.cells().get("J"), child.cells().get("M")));
            if (boundary != null && boundary.get("P").equals(zone.get("A"))) {
                if (same(child.cells().get("J"), child.cells().get("M"), number, articleRevision)) sum[0] = sum[0].add(total);
            } else if (children.containsKey(child.sourceRow())) collectDelivered(data, zone, child, total, number, articleRevision, children, sum);
        }
    }

    private boolean useBelongsToZone(PlanningData data, PlanningData.ProcessUse use, String zoneNumber,
            Map<String, PlanningData.ProcessTemplateRevision> templates, Map<String, PlanningData.Workplace> workplaces) {
        if (data.zones.stream().anyMatch(zone -> zone.get("A").equals(zoneNumber)
                && same(zone.get("D"), zone.get("G"), use.targetErpNumber(), use.targetRevision()))) return true;
        var template = templates.get(key(use.templateId(), Integer.toString(use.templateRevision())));
        return template != null && template.operations().stream().map(operation -> workplaces.get(operation.workplaceId()))
                .filter(Objects::nonNull).anyMatch(workplace -> zoneNumber.equals(workplace.zoneNumber()));
    }

    private static Map<Integer, List<PlanningData.StructureRow>> children(PlanningData data) {
        Map<Integer, List<PlanningData.StructureRow>> result = new HashMap<>();
        for (var row : data.structure) if (row.parentRow() != null) result.computeIfAbsent(row.parentRow(), ignored -> new ArrayList<>()).add(row);
        return result;
    }
    private static List<PlanningData.ResourceRequirement> safeResources(PlanningData.OperationStep operation) { return operation.resources() == null ? List.of() : operation.resources(); }
    private static boolean hasTargetInZone(PlanningData data, String zoneNumber, String number, String revision) { return data.zones.stream().anyMatch(zone -> zone.get("A").equals(zoneNumber) && same(zone.get("D"), zone.get("G"), number, revision)); }
    private static BigDecimal hourlyRate(String quantity, String taktMinutes) { BigDecimal takt = decimal(taktMinutes); return takt.signum() <= 0 ? BigDecimal.ZERO : decimal(quantity).multiply(BigDecimal.valueOf(60)).divide(takt, 6, RoundingMode.HALF_UP); }
    private static BigDecimal minutesToSeconds(String minutes) { return decimal(minutes).multiply(BigDecimal.valueOf(60)); }
    private static BigDecimal parseQuantity(String raw, int sourceRow) {
        if (blank(raw)) return BigDecimal.ONE;
        try { return new BigDecimal(raw.contains(",") ? raw.replace(".", "").replace(',', '.') : raw); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Ungueltige Menge in Excel-Zeile " + sourceRow); }
    }
    private static BigDecimal decimal(String value) { try { return blank(value) ? BigDecimal.ZERO : new BigDecimal(value); } catch (NumberFormatException exception) { return BigDecimal.ZERO; } }
    private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String key(String number, String revision) { return value(number) + "\u0000" + value(revision); }
    private static boolean same(String firstNumber, String firstRevision, String secondNumber, String secondRevision) { return key(firstNumber, firstRevision).equals(key(secondNumber, secondRevision)); }
    private static String value(String value) { return value == null ? "" : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static final class Accumulator {
        private final PlanningData.ResourceRequirement resource; private final PlanningData.OperationStep operation;
        private final PlanningData.ProcessUse use; private final String templateName; private final PlanningData.Workplace workplace;
        private BigDecimal required = BigDecimal.ZERO;
        private Accumulator(PlanningData.ResourceRequirement resource, PlanningData.OperationStep operation, PlanningData.ProcessUse use,
                            String templateName, PlanningData.Workplace workplace) {
            this.resource = resource; this.operation = operation; this.use = use; this.templateName = templateName; this.workplace = workplace;
        }
    }
}