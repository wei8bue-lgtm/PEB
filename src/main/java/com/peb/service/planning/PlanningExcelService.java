package com.peb.service.planning;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

@Service
public class PlanningExcelService {
    public PlanningData readStructure(InputStream stream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(stream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > 50000) throw new IllegalArgumentException("Maximal 50.000 Strukturzeilen erlaubt.");
            DataFormatter formatter = new DataFormatter(Locale.GERMANY);
            formatter.setUseCachedValuesForFormulaCells(true);
            PlanningData data = new PlanningData();
            Row header = sheet.getRow(0);
            if (header == null || !text(header, 9, formatter).equals("ERP_Nr")) {
                throw new IllegalArgumentException("Spalte J der ersten Zeile muss ERP_Nr enthalten.");
            }
            int width = Math.max(19, header.getLastCellNum());
            for (int column = 0; column < width; column++) data.inputHeaders.add(text(header, column, formatter));
            int sapNumberColumn = findHeader(header, formatter, "Bosch_SAP_Nr", "SAP_Nr", "Bosch-SAP-Nummer", "Bosch SAP Nummer", "Bosch_Materialnummer", "SAP_Materialnummer");
            int sapRevisionColumn = findHeader(header, formatter, "Bosch_SAP_Revision", "SAP_Revision", "SAP Revision", "Bosch SAP Revision");
            Integer[] ancestors = new Integer[7];
            Map<String, List<PlanningData.StructureRow>> occurrences = new LinkedHashMap<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                Map<String, String> cells = PlanningData.emptyRow(width);
                for (int column = 0; column < width; column++) cells.put(PlanningData.column(column), text(row, column, formatter));
                if (cells.values().stream().allMatch(String::isBlank)) continue;
                int depth = -1;
                for (int column = 0; column < 7; column++) {
                    if (!cells.get(PlanningData.column(column)).isBlank()) {
                        if (depth != -1) throw new IllegalArgumentException("Mehrere Ebenen in Excel-Zeile " + (index + 1));
                        depth = column;
                    }
                }
                if (depth < 0 || cells.get("J").isBlank()) throw new IllegalArgumentException("Ebene oder ERP_Nr fehlt in Excel-Zeile " + (index + 1));
                Integer parent = depth == 0 ? null : ancestors[depth - 1];
                if (depth > 0 && parent == null) throw new IllegalArgumentException("Fehlender Elternknoten in Excel-Zeile " + (index + 1));
                PlanningData.StructureRow occurrence = new PlanningData.StructureRow(index + 1, depth, parent, cells);
                data.structure.add(occurrence);
                occurrences.computeIfAbsent(identity(cells.get("J"), cells.get("M")), key -> new ArrayList<>()).add(occurrence);
                ancestors[depth] = index + 1;
                Arrays.fill(ancestors, depth + 1, ancestors.length, null);
            }
            buildArticleMasters(data, occurrences, sapNumberColumn, sapRevisionColumn);
            buildAssemblyDefinitions(data);
            return data;
        }
    }

    private int findHeader(Row header, DataFormatter formatter, String... names) {
        Set<String> expected = Arrays.stream(names).map(this::normalizedHeader).collect(java.util.stream.Collectors.toSet());
        for (int index = 0; index < header.getLastCellNum(); index++) {
            if (expected.contains(normalizedHeader(text(header, index, formatter)))) return index;
        }
        return -1;
    }

    private String normalizedHeader(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void buildArticleMasters(PlanningData data, Map<String, List<PlanningData.StructureRow>> occurrences,
                                     int sapNumberColumn, int sapRevisionColumn) {
        for (var entry : occurrences.entrySet()) {
            var first = entry.getValue().get(0);
            Map<String, String> attributes = new LinkedHashMap<>();
            for (int column = 0; column < data.inputHeaders.size(); column++) {
                if (column == 9 || column == 10 || column == sapNumberColumn || column == sapRevisionColumn) continue;
                attributes.put(data.inputHeaders.get(column), first.cells().get(PlanningData.column(column)));
            }
            String description = first.cells().get("K");
            String sapNumber = sapNumberColumn < 0 ? "" : first.cells().getOrDefault(PlanningData.column(sapNumberColumn), "");
            String sapRevision = sapRevisionColumn < 0 ? "" : first.cells().getOrDefault(PlanningData.column(sapRevisionColumn), "");
            List<Integer> sourceRows = entry.getValue().stream().map(PlanningData.StructureRow::sourceRow).toList();
            boolean inconsistent = entry.getValue().stream().anyMatch(row -> !row.cells().get("K").equals(description)
                    || (sapNumberColumn >= 0 && !row.cells().get(PlanningData.column(sapNumberColumn)).equals(sapNumber))
                    || (sapRevisionColumn >= 0 && !row.cells().get(PlanningData.column(sapRevisionColumn)).equals(sapRevision)));
            data.articleMasters.add(new PlanningData.ArticleMaster(first.cells().get("J"), first.cells().get("M"), sapNumber,
                    sapRevision, description, attributes, sourceRows, description.isBlank() || first.cells().get("M").isBlank() || sapNumber.isBlank() || inconsistent));
        }
    }

    private void buildAssemblyDefinitions(PlanningData data) {
        Map<Integer, List<PlanningData.StructureRow>> children = new HashMap<>();
        Map<String, List<PlanningData.StructureRow>> occurrences = new LinkedHashMap<>();
        Map<String, List<List<PlanningData.Component>>> breakdowns = new HashMap<>();
        for (var row : data.structure) {
            occurrences.computeIfAbsent(identity(row.cells().get("J"), row.cells().get("M")), key -> new ArrayList<>()).add(row);
            if (row.parentRow() != null) children.computeIfAbsent(row.parentRow(), key -> new ArrayList<>()).add(row);
        }
        Map<String, List<Integer>> usages = new HashMap<>();
        for (var entry : occurrences.entrySet()) {
            List<List<PlanningData.Component>> variants = new ArrayList<>();
            boolean hasChildren = false;
            for (var occurrence : entry.getValue()) {
                usages.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(occurrence.sourceRow());
                List<PlanningData.Component> components = children.getOrDefault(occurrence.sourceRow(), List.of()).stream()
                        .map(child -> new PlanningData.Component(child.cells().get("J"), child.cells().get("M"), child.cells().get("N"), child.sourceRow())).toList();
                hasChildren |= !components.isEmpty();
                if (variants.stream().noneMatch(existing -> sameBreakdown(existing, components))) variants.add(components);
            }
            if (hasChildren) breakdowns.put(entry.getKey(), variants);
        }
        for (var entry : breakdowns.entrySet()) {
            String[] identity = entry.getKey().split("\\u0000", -1);
            data.assemblyDefinitions.add(new PlanningData.AssemblyDefinition(identity[0], identity[1], entry.getValue().get(0),
                    usages.getOrDefault(entry.getKey(), List.of()), entry.getValue().size() > 1));
        }
    }

    private boolean sameBreakdown(List<PlanningData.Component> left, List<PlanningData.Component> right) {
        return left.size() == right.size() && java.util.stream.IntStream.range(0, left.size()).allMatch(index -> {
            var first = left.get(index); var second = right.get(index);
            return first.customerErpNumber().equals(second.customerErpNumber())
                    && first.customerRevision().equals(second.customerRevision())
                    && first.quantity().equals(second.quantity());
        });
    }

    private String identity(String number, String revision) { return number + "\u0000" + revision; }

    private String text(Row row, int column, DataFormatter formatter) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell != null && cell.getCellType() == CellType.ERROR) throw new IllegalArgumentException("Excel-Fehler in " + cell.getAddress());
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    public void export(PlanningData data, OutputStream stream) throws IOException {
        export(data, stream, "both");
    }

    public void export(PlanningData data, OutputStream stream, String numberMode) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            writeSheet(workbook, "Struktur", data.inputHeaders, data.structure.stream().map(PlanningData.StructureRow::cells).toList());
            writeSheet(workbook, "Zonen_Vorgaben", PlanningRules.ZONE_HEADERS, data.zones);
            writeSheet(workbook, "Fertigungsliste", PlanningRules.OUTPUT_HEADERS, data.output);
            if (!Set.of("customer", "sap", "both").contains(numberMode)) throw new IllegalArgumentException("Ungueltige Nummernanzeige.");
            List<String> numberHeaders = switch (numberMode) {
                case "customer" -> List.of("Kunden-ERP-Nr.", "Kundenrevision", "Beschreibung");
                case "sap" -> List.of("Bosch-SAP-Nr.", "SAP-Revision", "Beschreibung");
                default -> List.of("Kunden-ERP-Nr.", "Kundenrevision", "Bosch-SAP-Nr.", "SAP-Revision", "Beschreibung");
            };
            List<Map<String, String>> numberRows = data.articleMasters.stream().map(article -> {
                Map<String, String> row = PlanningData.emptyRow(numberHeaders.size());
                List<String> values = switch (numberMode) {
                    case "customer" -> List.of(article.customerErpNumber(), article.customerRevision(), article.description());
                    case "sap" -> List.of(article.boschSapNumber(), article.boschSapRevision(), article.description());
                    default -> List.of(article.customerErpNumber(), article.customerRevision(), article.boschSapNumber(), article.boschSapRevision(), article.description());
                };
                for (int index = 0; index < values.size(); index++) row.put(PlanningData.column(index), values.get(index));
                return row;
            }).toList();
            writeSheet(workbook, "Nummernzuordnung", numberHeaders, numberRows);
            List<Map<String, String>> workplaceRows = data.workplaces.stream().map(workplace -> {
                Map<String, String> row = PlanningData.emptyRow(5);
                row.put("A", workplace.zoneNumber()); row.put("B", workplace.code()); row.put("C", workplace.name());
                row.put("D", workplace.taktMinutes());
                row.put("E", data.zones.stream().filter(zone -> zone.get("A").equals(workplace.zoneNumber())).map(zone -> zone.get("B")).findFirst().orElse(""));
                return row;
            }).toList();
            writeSheet(workbook, "Arbeitsplaetze", PlanningRules.WORKPLACE_HEADERS, workplaceRows);
            exportProcessPlan(workbook, data);
            workbook.write(stream);
        }
    }

    public void exportReport(Object report, OutputStream stream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            if (report instanceof PlanningData.MaterialValidationReport validation) {
                List<String> headers = List.of("Ziel_ERP_Nr", "Zielrevision", "Material_ERP_Nr", "Materialrevision", "Beschreibung", "Erwartet_je_Stueck", "Verbraucht_je_Stueck", "Abweichung", "Status");
                List<Map<String, String>> rows = validation.rows().stream().map(item -> {
                    Map<String, String> row = PlanningData.emptyRow(headers.size());
                    row.put("A", item.targetErpNumber()); row.put("B", item.targetRevision()); row.put("C", item.articleNumber()); row.put("D", item.articleRevision());
                    row.put("E", item.description()); row.put("F", string(item.expectedQuantity())); row.put("G", string(item.actualQuantity()));
                    row.put("H", string(item.variance())); row.put("I", item.status()); return row;
                }).toList();
                writeSheet(workbook, "Materialabgleich", headers, rows);
            } else if (report instanceof PlanningData.ZoneSupplyValidationReport validation) {
                List<String> headers = List.of("Quellzone", "Quellzonenname", "Zielzone", "Zielzonenname", "ERP_Nr", "Revision", "Angebot_pro_Stunde", "Bedarf_pro_Stunde", "Abweichung_pro_Stunde", "Quelltakt_s", "Zieltakt_s", "Status");
                List<Map<String, String>> rows = validation.rows().stream().map(item -> {
                    Map<String, String> row = PlanningData.emptyRow(headers.size()); row.put("A", item.sourceZone()); row.put("B", item.sourceZoneName());
                    row.put("C", item.destinationZone()); row.put("D", item.destinationZoneName()); row.put("E", item.articleNumber()); row.put("F", item.articleRevision());
                    row.put("G", string(item.suppliedPerHour())); row.put("H", string(item.requiredPerHour())); row.put("I", string(item.variancePerHour()));
                    row.put("J", string(item.sourceTaktSeconds())); row.put("K", string(item.destinationTaktSeconds())); row.put("L", item.status()); return row;
                }).toList();
                writeSheet(workbook, "Zonenversorgung", headers, rows);
            } else if (report instanceof PlanningData.NumberValidationReport validation) {
                List<String> headers = List.of("Kunden_ERP_Nr", "Kundenrevision", "Bosch_SAP_Nr", "SAP_Revision", "Beschreibung", "Status", "Pruefhinweis");
                List<Map<String, String>> rows = validation.rows().stream().map(item -> {
                    Map<String, String> row = PlanningData.emptyRow(headers.size()); row.put("A", item.customerErpNumber()); row.put("B", item.customerRevision());
                    row.put("C", item.boschSapNumber()); row.put("D", item.boschSapRevision()); row.put("E", item.description());
                    row.put("F", item.status()); row.put("G", item.details()); return row;
                }).toList();
                writeSheet(workbook, "Nummernpruefung", headers, rows);
            } else if (report instanceof PlanningData.ResourceReport resources) {
                List<String> headers = List.of("Ressourcentyp", "Artikelnummer", "Artikelrevision", "Spezialnummer", "Beschreibung", "Prozess", "Arbeitsschritt_ID", "Arbeitsschritt", "Containerzuordnung", "Arbeitsplatz_ID", "Arbeitsplatz", "Ziel_ERP_Nr", "Zielrevision", "Einheit", "Bedarf_je_Stueck", "Containertyp", "Einheiten_je_Container", "Container_je_Stueck", "Verpackungseinheit");
                List<Map<String, String>> rows = resources.rows().stream().map(item -> {
                    Map<String, String> row = PlanningData.emptyRow(headers.size()); row.put("A", item.resourceType()); row.put("B", item.articleNumber());
                    row.put("C", item.articleRevision()); row.put("D", item.specialNumber()); row.put("E", item.description());
                    row.put("F", item.processName()); row.put("G", item.operationId()); row.put("H", item.operationName()); row.put("I", item.containerAssignment());
                    row.put("J", item.workplaceId()); row.put("K", item.workplaceName()); row.put("L", item.targetErpNumber()); row.put("M", item.targetRevision());
                    row.put("N", item.unit()); row.put("O", string(item.requiredQuantityPerUnit())); row.put("P", item.containerType());
                    row.put("Q", string(item.unitsPerContainer())); row.put("R", string(item.containersPerUnit())); row.put("S", item.packagingUnit()); return row;
                }).toList();
                writeSheet(workbook, "Ressourcenbedarf", headers, rows);
            } else throw new IllegalArgumentException("Unbekannter Reporttyp.");
            workbook.write(stream);
        }
    }

    private String string(java.math.BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }

    public void exportWorkSequence(PlanningData data, String projectName, int revision, OutputStream stream) throws IOException {
        List<String> headers = List.of("Planung", "Revision", "Schrittgruppe", "Prozess", "Ziel_ERP_Nr", "Zielrevision",
                "Reihenfolge", "Arbeitsschritt_ID", "Arbeitsschritt", "Beschreibung", "Arbeitsplatz", "Ausfuehrungen_je_Stueck",
                "Zeit_s_je_Ausfuehrung", "Arbeitsinhalt_s_je_Stueck", "Ressourcen");
        Map<String, PlanningData.ProcessStepGroup> groups = new HashMap<>();
        data.processStepGroups.forEach(group -> groups.put(group.id(), group));
        Map<String, PlanningData.ProcessTemplateRevision> templates = new HashMap<>();
        data.processTemplates.forEach(template -> templates.put(template.templateId() + "\u0000" + template.revision(), template));
        Map<String, PlanningData.Workplace> workplaces = new HashMap<>();
        data.workplaces.forEach(workplace -> workplaces.put(workplace.id(), workplace));
        List<Map<String, String>> operationRows = new ArrayList<>();
        List<Map<String, String>> documentRows = new ArrayList<>();
        List<String> documentHeaders = List.of("Schrittgruppe", "Prozess", "Arbeitsschritt_ID", "Arbeitsschritt", "Dokumenttyp", "Titel", "Referenz", "Externe_Vorgabe", "Notiz");
        for (var use : data.processUses) {
            var group = groups.get(use.stepGroupId());
            var template = templates.get(use.templateId() + "\u0000" + use.templateRevision());
            for (var operation : template.operations().stream().sorted(Comparator.comparingInt(PlanningData.OperationStep::sequence)).toList()) {
                Map<String, String> row = PlanningData.emptyRow(headers.size());
                List<String> values = List.of(safe(projectName), Integer.toString(revision), safe(group.name()), safe(template.name()), safe(use.targetErpNumber()),
                    safe(use.targetRevision()), Integer.toString(operation.sequence()), safe(operation.id()), safe(operation.name()), safe(operation.description()),
                        Optional.ofNullable(workplaces.get(operation.workplaceId())).map(PlanningData.Workplace::name).orElse(""),
                        Integer.toString(operation.executionsPerUnit()), string(ProcessPlanningRules.effectiveSeconds(operation)),
                        string(ProcessPlanningRules.effectiveSeconds(operation).multiply(java.math.BigDecimal.valueOf(operation.executionsPerUnit()))),
                        (operation.resources() == null ? List.<PlanningData.ResourceRequirement>of() : operation.resources()).stream()
                            .map(resource -> resource.type() + ": " + (resource.articleNumber() == null || resource.articleNumber().isBlank() ? resource.description() : resource.articleNumber())
                                        + " x " + string(resource.quantity()) + " " + resource.unit()).collect(java.util.stream.Collectors.joining("; ")));
                for (int index = 0; index < values.size(); index++) row.put(PlanningData.column(index), values.get(index));
                operationRows.add(row);
                for (var document : operation.documents() == null ? List.<PlanningData.OperationDocument>of() : operation.documents()) {
                    Map<String, String> documentRow = PlanningData.emptyRow(documentHeaders.size());
                        List<String> documentValues = List.of(safe(group.name()), safe(template.name()), safe(operation.id()), safe(operation.name()), safe(document.type()),
                            safe(document.title()), safe(document.uri()), document.external() ? "JA" : "NEIN", safe(document.note()));
                    for (int index = 0; index < documentValues.size(); index++) documentRow.put(PlanningData.column(index), documentValues.get(index));
                    documentRows.add(documentRow);
                }
            }
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            writeSheet(workbook, "Arbeitsfolgekarte", headers, operationRows);
            writeSheet(workbook, "Dokumentreferenzen", documentHeaders, documentRows);
            workbook.write(stream);
        }
    }

    private String safe(String value) { return value == null ? "" : value; }

    private void exportProcessPlan(Workbook workbook, PlanningData data) {
        List<String> headers = List.of("Schrittgruppe", "Prozess", "Prozessvorlagen_ID", "Vorlagenrevision", "Verwendung_ID",
                "Ziel_ERP_Nr", "Zielrevision", "Schrittnummer", "Arbeitsschritt_ID", "Bezeichnung", "Beschreibung",
                "Ausfuehrungen_je_Stueck", "Schaetzung_s_je_Ausfuehrung", "Zeitstudie_s_je_Ausfuehrung", "Zeitstudienquelle",
                "Zeitstudienreferenz", "Effektiv_s_je_Ausfuehrung", "Arbeitsinhalt_s_je_Stueck", "Istzeit_s", "Arbeitsplatz_ID", "Arbeitsplatz");
        Map<String, PlanningData.ProcessStepGroup> groups = new HashMap<>();
        data.processStepGroups.forEach(group -> groups.put(group.id(), group));
        Map<String, PlanningData.ProcessTemplateRevision> templates = new HashMap<>();
        data.processTemplates.forEach(template -> templates.put(template.templateId() + "\u0000" + template.revision(), template));
        Map<String, PlanningData.Workplace> workplaces = new HashMap<>();
        data.workplaces.forEach(workplace -> workplaces.put(workplace.id(), workplace));
        List<Map<String, String>> operationRows = new ArrayList<>();
    List<Map<String, String>> resourceRows = new ArrayList<>();
        for (var use : data.processUses) {
            var group = groups.get(use.stepGroupId());
            var template = templates.get(use.templateId() + "\u0000" + use.templateRevision());
            if (group == null || template == null) continue;
            for (var operation : template.operations()) {
                Map<String, String> row = PlanningData.emptyRow(headers.size());
                String effective = ProcessPlanningRules.effectiveSeconds(operation).toPlainString();
                row.put("A", group.name()); row.put("B", template.name()); row.put("C", template.templateId());
                row.put("D", Integer.toString(template.revision())); row.put("E", use.id());
                row.put("F", use.targetErpNumber()); row.put("G", use.targetRevision()); row.put("H", Integer.toString(operation.sequence()));
                row.put("I", operation.id()); row.put("J", operation.name()); row.put("K", operation.description());
                row.put("L", Integer.toString(operation.executionsPerUnit()));
                row.put("M", string(operation.estimatedSeconds())); row.put("N", string(operation.timeStudySeconds()));
                row.put("O", operation.timeStudySource()); row.put("P", operation.timeStudyReference()); row.put("Q", effective);
                row.put("R", ProcessPlanningRules.effectiveSeconds(operation).multiply(java.math.BigDecimal.valueOf(operation.executionsPerUnit())).toPlainString());
                row.put("S", string(operation.actualSeconds())); row.put("T", operation.workplaceId());
                row.put("U", workplaces.containsKey(operation.workplaceId()) ? workplaces.get(operation.workplaceId()).name() : "");
                operationRows.add(row);
                for (var resource : operation.resources() == null ? List.<PlanningData.ResourceRequirement>of() : operation.resources()) {
                    Map<String, String> resourceRow = PlanningData.emptyRow(16);
                    resourceRow.put("A", use.id()); resourceRow.put("B", operation.id()); resourceRow.put("C", operation.name());
                    resourceRow.put("D", resource.type().name()); resourceRow.put("E", resource.articleNumber());
                    resourceRow.put("F", resource.articleRevision()); resourceRow.put("G", resource.description());
                    resourceRow.put("H", string(resource.quantity())); resourceRow.put("I", resource.unit()); resourceRow.put("J", template.name());
                    resourceRow.put("K", group.name()); resourceRow.put("L", resource.specialNumber()); resourceRow.put("M", resource.containerAssignment());
                    resourceRow.put("N", resource.containerType()); resourceRow.put("O", string(resource.unitsPerContainer()));
                    resourceRow.put("P", resource.packagingUnit()); resourceRows.add(resourceRow);
                }
            }
        }
        writeSheet(workbook, "Prozessplan", headers, operationRows);
        writeSheet(workbook, "Prozessressourcen", List.of("Verwendung_ID", "Arbeitsschritt_ID", "Arbeitsschritt", "Typ", "Artikelnummer",
            "Artikelrevision", "Beschreibung", "Menge", "Einheit", "Prozess", "Schrittgruppe", "Spezialnummer", "Containerzuordnung",
            "Containertyp", "Einheiten_je_Container", "Verpackungseinheit"), resourceRows);
        List<Map<String, String>> balanceRows = ProcessPlanningRules.balance(data).stream().map(load -> {
            Map<String, String> row = PlanningData.emptyRow(7);
            row.put("A", load.workplaceName()); row.put("B", load.targetErpNumber()); row.put("C", load.targetRevision());
            row.put("D", load.workContentSeconds().toPlainString()); row.put("E", load.targetSeconds().toPlainString());
            row.put("F", load.loadPercent().toPlainString()); row.put("G", String.join(", ", load.operations().stream().map(PlanningData.OperationLoad::operationName).toList()));
            return row;
        }).toList();
        writeSheet(workbook, "LineBalancing", List.of("Arbeitsplatz", "Ziel_ERP_Nr", "Zielrevision", "Arbeitsinhalt_s_je_Stueck", "Taktziel_s", "Auslastung_Prozent", "Arbeitsschritte"), balanceRows);
    }

    private void writeSheet(Workbook workbook, String name, List<String> headers, List<Map<String, String>> rows) {
        if (headers.isEmpty()) headers = List.of("Keine Daten importiert");
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int column = 0; column < headers.size(); column++) {
            header.createCell(column).setCellValue(headers.get(column));
            sheet.setColumnWidth(column, 6000);
        }
        for (int index = 0; index < rows.size(); index++) {
            Row row = sheet.createRow(index + 1);
            for (int column = 0; column < headers.size(); column++) row.createCell(column).setCellValue(rows.get(index).getOrDefault(PlanningData.column(column), ""));
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rows.size(), 0, headers.size() - 1));
    }
}