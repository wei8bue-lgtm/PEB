package com.peb.service.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProcessPlanningRules {
    private ProcessPlanningRules() {}

    public static void validate(PlanningData data) {
        Map<String, PlanningData.ProcessStepGroup> groups = unique(data.processStepGroups, PlanningData.ProcessStepGroup::id, "Schrittgruppe");
        Map<String, PlanningData.ProcessTemplateRevision> templates = new HashMap<>();
        for (var template : data.processTemplates) {
            require(!empty(template.templateId()), "Prozessvorlage benoetigt eine ID.");
            require(template.revision() > 0, "Prozessvorlagenrevision muss positiv sein.");
            require(!empty(template.name()), "Prozessvorlage benoetigt einen Namen.");
            require(templates.put(key(template.templateId(), Integer.toString(template.revision())), template) == null,
                    "Doppelte Prozessvorlagenrevision.");
            validateOperations(template.operations());
        }
        unique(data.processUses, PlanningData.ProcessUse::id, "Prozessverwendung");
        for (var use : data.processUses) {
            require(groups.containsKey(use.stepGroupId()), "Unbekannte Schrittgruppe in Prozessverwendung.");
            require(templates.containsKey(key(use.templateId(), Integer.toString(use.templateRevision()))),
                    "Prozessvorlagenrevision fehlt in Prozessverwendung.");
            if (!empty(use.targetErpNumber())) require(PlanningRules.articleOptions(data).stream()
                    .anyMatch(article -> article.number().equals(use.targetErpNumber()) && article.revision().equals(value(use.targetRevision()))),
                    "Zielbaugruppe mit ERP-Nummer/Revision ist nicht im Artikelstamm vorhanden.");
        }
        Set<String> workplaceIds = data.workplaces.stream().map(PlanningData.Workplace::id).collect(Collectors.toSet());
        for (var template : data.processTemplates) for (var operation : template.operations()) {
            if (!empty(operation.workplaceId())) require(workplaceIds.contains(operation.workplaceId()),
                    "Arbeitsschritt verweist auf einen unbekannten Arbeitsplatz: " + operation.workplaceId());
        }
    }

    public static void validateForRelease(PlanningData data) {
        validate(data);
        List<String> issues = consistencyIssues(data);
        require(issues.isEmpty(), issues.isEmpty() ? "" : issues.get(0));
        Map<String, PlanningData.ProcessTemplateRevision> templates = data.processTemplates.stream()
                .collect(Collectors.toMap(template -> key(template.templateId(), Integer.toString(template.revision())), Function.identity()));
        for (var use : data.processUses) {
            var template = templates.get(key(use.templateId(), Integer.toString(use.templateRevision())));
            if (!empty(use.targetErpNumber())) require(PlanningRules.articleOptions(data).stream()
                    .anyMatch(article -> article.number().equals(use.targetErpNumber()) && article.revision().equals(value(use.targetRevision()))),
                    "Zielbaugruppe mit ERP-Nummer/Revision ist nicht im Artikelstamm vorhanden.");
            for (var operation : template.operations()) {
                require(!empty(operation.workplaceId()), "Arbeitsschritt '" + operation.name() + "' ist keinem Arbeitsplatz zugeordnet.");
                require(operation.estimatedSeconds() != null || operation.timeStudySeconds() != null,
                        "Arbeitsschritt '" + operation.name() + "' benoetigt eine Schaetzung oder Zeitstudie.");
            }
        }
    }

    public static List<String> consistencyIssues(PlanningData data) {
        validate(data);
        Set<String> articleIds = PlanningRules.articleOptions(data).stream()
                .map(article -> key(article.number(), article.revision())).collect(Collectors.toSet());
        Map<String, PlanningData.ProcessTemplateRevision> templates = data.processTemplates.stream()
                .collect(Collectors.toMap(template -> key(template.templateId(), Integer.toString(template.revision())), Function.identity()));
        Set<String> issues = new LinkedHashSet<>();
        for (var use : data.processUses) {
            if (!empty(use.targetErpNumber()) && !articleIds.contains(key(use.targetErpNumber(), use.targetRevision()))) {
                issues.add("Prozessverwendung " + use.id() + " verweist auf eine nicht vorhandene Zielbaugruppe.");
            }
            var template = templates.get(key(use.templateId(), Integer.toString(use.templateRevision())));
            for (var operation : template.operations()) {
                if (empty(operation.workplaceId())) issues.add("Arbeitsschritt '" + operation.name() + "' ist keinem Arbeitsplatz zugeordnet.");
                if (operation.estimatedSeconds() == null && operation.timeStudySeconds() == null) {
                    issues.add("Arbeitsschritt '" + operation.name() + "' benoetigt eine Schaetzung oder Zeitstudie.");
                }
                for (var resource : operation.resources() == null ? List.<PlanningData.ResourceRequirement>of() : operation.resources()) {
                    if (resource.type() == PlanningData.ResourceType.MATERIAL && !empty(resource.articleNumber())
                            && !articleIds.contains(key(resource.articleNumber(), resource.articleRevision()))) {
                        issues.add("Material " + resource.articleNumber() + " / " + value(resource.articleRevision())
                                + " in Arbeitsschritt '" + operation.name() + "' fehlt im Artikelstamm.");
                    }
                }
            }
        }
        return List.copyOf(issues);
    }

    public static List<PlanningData.WorkplaceLoad> balance(PlanningData data) {
        validate(data);
        Map<String, PlanningData.ProcessTemplateRevision> templates = data.processTemplates.stream()
                .collect(Collectors.toMap(template -> key(template.templateId(), Integer.toString(template.revision())), Function.identity()));
        Map<String, PlanningData.Workplace> workplaces = data.workplaces.stream()
                .collect(Collectors.toMap(PlanningData.Workplace::id, Function.identity()));
        Map<String, Map<String, String>> zones = new HashMap<>();
        data.zones.forEach(zone -> zones.put(key(zone.get("D"), zone.get("G")), zone));
        Map<String, List<PlanningData.OperationLoad>> operations = new LinkedHashMap<>();
        Map<String, BigDecimal> takt = new HashMap<>();
        for (var use : data.processUses) {
            var template = templates.get(key(use.templateId(), Integer.toString(use.templateRevision())));
            var group = data.processStepGroups.stream().filter(item -> item.id().equals(use.stepGroupId())).findFirst().orElseThrow();
            Map<String, String> targetZone = zones.get(key(use.targetErpNumber(), use.targetRevision()));
            for (var operation : template.operations().stream().sorted(Comparator.comparingInt(PlanningData.OperationStep::sequence)).toList()) {
                if (empty(operation.workplaceId())) continue;
                var workplace = workplaces.get(operation.workplaceId());
                String targetNumber = value(use.targetErpNumber());
                String targetRevision = value(use.targetRevision());
                String bucket = operation.workplaceId() + "\u0000" + targetNumber + "\u0001" + targetRevision;
                BigDecimal totalSeconds = effectiveSeconds(operation).multiply(BigDecimal.valueOf(operation.executionsPerUnit()));
                operations.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(new PlanningData.OperationLoad(
                        operation.id(), operation.name(), group.name() + " / " + template.name(), operation.sequence(),
                        totalSeconds, effectiveSource(operation), operation.executionsPerUnit()));
                BigDecimal targetSeconds = minutesToSeconds(decimal(workplace.taktMinutes()));
                if (targetSeconds.signum() == 0 && targetZone != null) targetSeconds = minutesToSeconds(decimal(targetZone.get("K")));
                takt.put(bucket, targetSeconds);
            }
        }
        List<PlanningData.WorkplaceLoad> result = new ArrayList<>();
        operations.forEach((bucket, steps) -> {
            String[] parts = bucket.split("\u0000", -1);
            String workplaceId = parts[0];
            String[] target = parts[1].split("\u0001", -1);
            BigDecimal work = steps.stream().map(PlanningData.OperationLoad::effectiveSeconds).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal targetSeconds = takt.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal percent = targetSeconds.signum() == 0 ? BigDecimal.ZERO
                    : work.multiply(BigDecimal.valueOf(100)).divide(targetSeconds, 2, RoundingMode.HALF_UP);
            var workplace = workplaces.get(workplaceId);
            result.add(new PlanningData.WorkplaceLoad(workplaceId, workplace.name(), target[0], target[1], work,
                    targetSeconds, percent, List.copyOf(steps)));
        });
        result.sort(Comparator.comparing(PlanningData.WorkplaceLoad::workplaceName)
                .thenComparing(PlanningData.WorkplaceLoad::targetErpNumber).thenComparing(PlanningData.WorkplaceLoad::targetRevision));
        return result;
    }

    public static BigDecimal effectiveSeconds(PlanningData.OperationStep operation) {
        BigDecimal seconds = operation.timeStudySeconds() != null ? operation.timeStudySeconds() : operation.estimatedSeconds();
        return seconds == null ? BigDecimal.ZERO : seconds;
    }

    public static String effectiveSource(PlanningData.OperationStep operation) {
        if (operation.timeStudySeconds() != null) return "ZEITSTUDIE_" + value(operation.timeStudySource());
        return operation.estimatedSeconds() == null ? "FEHLT" : "SCHAETZUNG";
    }

    public static void validateOperations(List<PlanningData.OperationStep> operations) {
        require(operations != null, "Prozessvorlage benoetigt eine Operationsliste.");
        Set<String> ids = new HashSet<>();
        Set<Integer> sequences = new HashSet<>();
        for (var operation : operations) {
            require(!empty(operation.id()) && ids.add(operation.id()), "Arbeitsschritt-ID fehlt oder ist doppelt.");
            require(!empty(operation.name()), "Arbeitsschritt benoetigt eine Bezeichnung.");
            require(operation.sequence() > 0 && sequences.add(operation.sequence()), "Reihenfolge je Prozess muss eindeutig und positiv sein.");
            require(operation.executionsPerUnit() > 0, "Ausfuehrungsanzahl je Stueck muss positiv sein.");
            requireNonNegative(operation.estimatedSeconds(), "Geschaetzte Dauer");
            requireNonNegative(operation.timeStudySeconds(), "Zeitstudiendauer");
            requireNonNegative(operation.actualSeconds(), "Istzeit");
            if (operation.timeStudySeconds() != null) require(!empty(operation.timeStudySource()), "Zeitstudie benoetigt eine Quelle (z. B. MTM oder URS).");
            Set<String> resourceIds = new HashSet<>();
            for (var resource : operation.resources() == null ? List.<PlanningData.ResourceRequirement>of() : operation.resources()) {
                require(!empty(resource.id()) && resourceIds.add(resource.id()), "Ressourcen-ID fehlt oder ist doppelt.");
                require(resource.type() != null, "Ressourcentyp muss Material, Werkzeug oder Hilfsmittel sein.");
                require(!empty(resource.articleNumber()) || !empty(resource.description()), "Ressource benoetigt Artikelnummer oder Beschreibung.");
                requireNonNegative(resource.quantity(), "Ressourcenmenge");
                requireNonNegative(resource.unitsPerContainer(), "Einheiten je Container");
                require(resource.unitsPerContainer() == null || resource.unitsPerContainer().signum() > 0, "Einheiten je Container muessen groesser als null sein.");
            }
            Set<String> documentIds = new HashSet<>();
            for (var document : operation.documents() == null ? List.<PlanningData.OperationDocument>of() : operation.documents()) {
                require(document != null && !empty(document.id()) && documentIds.add(document.id()),
                        "Dokumenten-ID fehlt oder ist doppelt.");
                require(Set.of("PROCESS_DESCRIPTION", "DECISION", "DRAWING", "NOTE", "OTHER").contains(document.type()),
                        "Dokumenttyp muss Prozessbeschreibung, Festlegung, Zeichnung, Notiz oder Sonstiges sein.");
                require(!empty(document.title()), "Dokumentreferenz benoetigt einen Titel.");
                if (document.external()) require(isWebUrl(document.uri()), "Externe Prozessvorgabe benoetigt eine gueltige HTTP(S)-URL.");
                else if (!empty(document.uri())) require(isSafeReference(document.uri()), "Dokumentreferenz muss eine HTTP(S)-URL oder ein relativer Dokumentpfad sein.");
                else require("NOTE".equals(document.type()) && !empty(document.note()),
                        "Dokumentreferenz benoetigt eine URL/einen Pfad oder einen Notiztext.");
            }
        }
    }

    private static boolean isWebUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            var uri = java.net.URI.create(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static boolean isSafeReference(String value) {
        String reference = value.trim();
        if (isWebUrl(reference)) return true;
        return reference.startsWith("/") && !reference.startsWith("//") && !reference.contains("\\")
                && !reference.toLowerCase(Locale.ROOT).startsWith("/javascript:");
    }

    private static <T> Map<String, T> unique(List<T> values, Function<T, String> key, String label) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) require(value != null && !empty(key.apply(value)) && result.put(key.apply(value), value) == null,
                "Fehlende oder doppelte ID fuer " + label + ".");
        return result;
    }

    private static BigDecimal minutesToSeconds(BigDecimal minutes) { return minutes.multiply(BigDecimal.valueOf(60)); }
    private static BigDecimal decimal(String value) { try { return empty(value) ? BigDecimal.ZERO : new BigDecimal(value); } catch (NumberFormatException exception) { return BigDecimal.ZERO; } }
    private static String key(String first, String second) { return value(first) + "\u0000" + value(second); }
    private static boolean empty(String value) { return value == null || value.isBlank(); }
    private static String value(String value) { return value == null ? "" : value; }
    private static void requireNonNegative(BigDecimal value, String name) { require(value == null || value.signum() >= 0, name + " darf nicht negativ sein."); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}