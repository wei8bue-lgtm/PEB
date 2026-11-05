package com.peb.service.planning;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PlanningExcelServiceTest {
    @Test
    void importsVersionedSapMappingsAndReusableAssemblies() throws Exception {
        PlanningData identical = importRepeatedAssembly("2", true);
        var assembly = identical.assemblyDefinitions.stream().filter(item -> item.customerErpNumber().equals("200")).findFirst().orElseThrow();
        assertThat(assembly.customerRevision()).isEqualTo("A");
        assertThat(assembly.usageRows()).containsExactly(3, 5);
        assertThat(assembly.components()).singleElement().satisfies(component -> {
            assertThat(component.customerErpNumber()).isEqualTo("300");
            assertThat(component.quantity()).isEqualTo("2");
        });
        assertThat(assembly.conflictingBreakdown()).isFalse();
        var article = identical.articleMasters.stream().filter(item -> item.customerErpNumber().equals("200")).findFirst().orElseThrow();
        assertThat(article.boschSapNumber()).isEqualTo("5000200");
        assertThat(article.boschSapRevision()).isEqualTo("01");
        assertThat(article.sourceRows()).containsExactly(3, 5);
        assertThat(article.missingMasterData()).isFalse();

        PlanningData divergent = importRepeatedAssembly("3", true);
        assertThat(divergent.assemblyDefinitions.stream().filter(item -> item.customerErpNumber().equals("200"))
                .findFirst().orElseThrow().conflictingBreakdown()).isTrue();

        PlanningData missingUse = importRepeatedAssembly("2", false);
        assertThat(missingUse.assemblyDefinitions.stream().filter(item -> item.customerErpNumber().equals("200"))
                .findFirst().orElseThrow().conflictingBreakdown()).isTrue();
    }

    @Test
    void matchesEveryColumnInProvidedZoneAndOutputWorkbooks() throws Exception {
        Path directory = Path.of("docs/fertigungsplanung");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(directory.resolve("Output.xlsx")));
        try (var stream = Files.newInputStream(directory.resolve("TEST_Zonen_Entkoppung.xlsm"));
             var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(stream)) {
            var row = workbook.getSheet("Zonen_Vorgaben").getRow(2);
            for (int column = 0; column < 36; column++) assertThat(row.getCell(column).getStringCellValue().trim())
                    .as("Zonenspalte %s", PlanningData.column(column)).isEqualTo(PlanningRules.ZONE_HEADERS.get(column));
        }
        try (var stream = Files.newInputStream(directory.resolve("Output.xlsx"));
             var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(stream)) {
            var row = workbook.getSheetAt(0).getRow(5);
            for (int column = 0; column < 45; column++) assertThat(row.getCell(column).getStringCellValue().trim())
                    .as("Ausgabespalte %s", PlanningData.column(column)).isEqualTo(PlanningRules.OUTPUT_HEADERS.get(column));
        }
    }

    @Test
    void exportsAllColumnsAsTextWithoutFormulaExecution() throws Exception {
        var data = new PlanningData();
        data.inputHeaders.add("Ebene 0");
        var zone = PlanningData.emptyRow(36); zone.put("D", "00123"); zone.put("AD", "=1+1"); data.zones.add(zone);
        var output = PlanningData.emptyRow(45); output.put("AS", "last"); data.output.add(output);
        var stream = new java.io.ByteArrayOutputStream();
        new PlanningExcelService().export(data, stream);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(stream.toByteArray()))) {
            assertThat(workbook.getSheet("Zonen_Vorgaben").getRow(1).getCell(3).getStringCellValue()).isEqualTo("00123");
            assertThat(workbook.getSheet("Zonen_Vorgaben").getRow(1).getCell(29).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(workbook.getSheet("Fertigungsliste").getRow(1).getCell(44).getStringCellValue()).isEqualTo("last");
        }
    }

    @Test
    void exportsCustomerAndBoschNumberViewsIndependentlyOrTogether() throws Exception {
        var data = new PlanningData();
        data.articleMasters.add(new PlanningData.ArticleMaster("001234", "A", "50001234", "01", "Part",
                java.util.Map.of(), java.util.List.of(), false));
        for (String mode : java.util.List.of("customer", "sap", "both")) {
            var stream = new ByteArrayOutputStream();
            new PlanningExcelService().export(data, stream, mode);
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(stream.toByteArray()))) {
                var sheet = workbook.getSheet("Nummernzuordnung");
                int expectedColumns = mode.equals("both") ? 5 : 3;
                assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) expectedColumns);
                assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(mode.equals("sap") ? "50001234" : "001234");
                if (mode.equals("both")) assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("50001234");
            }
        }
    }

    @Test
    void exportsProcessHierarchyResourcesAndLineBalancing() throws Exception {
        var data = new PlanningData();
        data.processStepGroups.add(new PlanningData.ProcessStepGroup("group-1", "Montage", 10));
        var resource = new PlanningData.ResourceRequirement("resource-1", PlanningData.ResourceType.TOOL,
                "TOOL-1", "A", "Drehmomentschluessel", java.math.BigDecimal.ONE, "Stk");
        var operation = new PlanningData.OperationStep("operation-1", "Verschrauben", "Sollmoment beachten", 10, 2,
                java.math.BigDecimal.valueOf(12), java.math.BigDecimal.valueOf(8), "MTM", "MTM-100", null, "workplace-1", List.of(resource));
        data.processTemplates.add(new PlanningData.ProcessTemplateRevision("template-1", 1, "Gehäuse", List.of(operation)));
        data.processUses.add(new PlanningData.ProcessUse("use-1", "group-1", "template-1", 1, "", ""));
        data.workplaces.add(new PlanningData.Workplace("workplace-1", "1", "WS-1", "Montage 1", "1"));
        var bytes = new ByteArrayOutputStream();
        new PlanningExcelService().export(data, bytes);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(workbook.getSheet("Prozessplan").getRow(1).getCell(16).getStringCellValue()).isEqualTo("8");
            assertThat(workbook.getSheet("Prozessplan").getRow(1).getCell(17).getStringCellValue()).isEqualTo("16");
            assertThat(workbook.getSheet("Prozessressourcen").getRow(1).getCell(3).getStringCellValue()).isEqualTo("TOOL");
            assertThat(workbook.getSheet("LineBalancing").getRow(1).getCell(5).getStringCellValue()).isEqualTo("26.67");
        }
    }

    @Test
    void exportsReleasedWorkSequenceWithDocumentReferences() throws Exception {
        var data = new PlanningData();
        data.processStepGroups.add(new PlanningData.ProcessStepGroup("group", "Montage", 10));
        data.workplaces.add(new PlanningData.Workplace("wp", "1", "WS-1", "Montageplatz", "1"));
        var document = new PlanningData.OperationDocument("doc", "DRAWING", "Montagezeichnung", "https://example.org/drawing/1", "Ansicht B", true);
        var operation = new PlanningData.OperationStep("op", "Verschrauben", "Sollmoment beachten", 10, 2,
                java.math.BigDecimal.TEN, null, "", "", null, "wp", List.of(), List.of(document));
        data.processTemplates.add(new PlanningData.ProcessTemplateRevision("template", 1, "Gehäuse", List.of(operation)));
        data.processUses.add(new PlanningData.ProcessUse("use", "group", "template", 1, "100", "A"));
        var bytes = new ByteArrayOutputStream();
        new PlanningExcelService().exportWorkSequence(data, "Musterplanung", 7, bytes);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(workbook.getSheet("Arbeitsfolgekarte").getRow(1).getCell(8).getStringCellValue()).isEqualTo("Verschrauben");
            assertThat(workbook.getSheet("Dokumentreferenzen").getRow(1).getCell(5).getStringCellValue()).isEqualTo("Montagezeichnung");
            assertThat(workbook.getSheet("Dokumentreferenzen").getRow(1).getCell(6).getStringCellValue()).isEqualTo("https://example.org/drawing/1");
        }
    }

    @Test
    void exportsFilteredValidationReportsWithRevisionAndPackagingColumns() throws Exception {
        var validation = new PlanningData.MaterialValidationReport("Test", 4, "now", "1", null,
                List.of(new PlanningData.MaterialValidationRow("100", "A", "200", "B", "Schraube",
                        java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(8), java.math.BigDecimal.valueOf(-2), "SHORTAGE")));
        var bytes = new ByteArrayOutputStream(); new PlanningExcelService().exportReport(validation, bytes);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(workbook.getSheet("Materialabgleich").getRow(0).getLastCellNum()).isEqualTo((short) 9);
            assertThat(workbook.getSheet("Materialabgleich").getRow(1).getCell(8).getStringCellValue()).isEqualTo("SHORTAGE");
        }
        var data = new PlanningData();
        data.articleMasters.add(new PlanningData.ArticleMaster("100", "A", "SAP100", "01", "Test", Map.of(), List.of(), false));
        data.processStepGroups.add(new PlanningData.ProcessStepGroup("g", "Gruppe", 10));
        data.workplaces.add(new PlanningData.Workplace("wp", "1", "WS", "Station", "1"));
        var resource = new PlanningData.ResourceRequirement("r", PlanningData.ResourceType.TOOL, "T1", "A", "Werkzeug",
                java.math.BigDecimal.ONE, "Stk", "SPEC-1", "KLT", java.math.BigDecimal.TEN, "Stk/KLT", "CONTAINER-7");
        var step = new PlanningData.OperationStep("op", "Bohren", "", 10, 1, java.math.BigDecimal.TEN,
                null, "", "", null, "wp", List.of(resource));
        data.processTemplates.add(new PlanningData.ProcessTemplateRevision("t", 1, "Prozess", List.of(step)));
        data.processUses.add(new PlanningData.ProcessUse("u", "g", "t", 1, "100", "A"));
        var report = new PlanningValidationService().resources(data, "Test", 4, "tools", null, "wp", "100", "A");
        bytes.reset(); new PlanningExcelService().exportReport(report, bytes);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(workbook.getSheet("Ressourcenbedarf").getRow(0).getLastCellNum()).isEqualTo((short) 19);
            assertThat(workbook.getSheet("Ressourcenbedarf").getRow(1).getCell(3).getStringCellValue()).isEqualTo("SPEC-1");
            assertThat(workbook.getSheet("Ressourcenbedarf").getRow(1).getCell(8).getStringCellValue()).isEqualTo("CONTAINER-7");
        }
    }

    @Test
    void importsProvidedStructureWithHierarchyAndAllAttributes() throws Exception {
        Path sample = Path.of("docs/fertigungsplanung/Input.xlsx");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(sample));
        try (var stream = Files.newInputStream(sample)) {
            PlanningData data = new PlanningExcelService().readStructure(stream);
            assertThat(data.structure).hasSize(1236);
            assertThat(data.inputHeaders).hasSize(19);
            assertThat(data.structure.get(0).cells().get("J")).isEqualTo("010031000010");
            assertThat(data.structure.get(0).parentRow()).isNull();
            assertThat(data.structure.get(1).parentRow()).isEqualTo(2);
            assertThat(data.structure).allSatisfy(row -> assertThat(row.cells()).hasSize(19));
        }
    }

    private PlanningData importRepeatedAssembly(String secondQuantity, boolean secondHasChild) throws Exception {
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Input");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ebene 0"); header.createCell(1).setCellValue("Ebene 1");
            header.createCell(2).setCellValue("Ebene 2"); header.createCell(9).setCellValue("ERP_Nr");
            header.createCell(10).setCellValue("Beschreibung"); header.createCell(12).setCellValue("akt. Revision");
            header.createCell(19).setCellValue("Bosch_SAP_Nr"); header.createCell(20).setCellValue("SAP_Revision");
            writeOccurrence(sheet, 1, 0, "100", "Root", "01", "", "", "");
            writeOccurrence(sheet, 2, 1, "200", "Baugruppe X", "A", "5000200", "01", "");
            writeOccurrence(sheet, 3, 2, "300", "Teil P", "02", "5000300", "02", "2");
            writeOccurrence(sheet, 4, 1, "200", "Baugruppe X", "A", "5000200", "01", "");
            if (secondHasChild) writeOccurrence(sheet, 5, 2, "300", "Teil P", "02", "5000300", "02", secondQuantity);
            workbook.write(bytes);
            return new PlanningExcelService().readStructure(new ByteArrayInputStream(bytes.toByteArray()));
        }
    }

    private void writeOccurrence(org.apache.poi.ss.usermodel.Sheet sheet, int rowNumber, int depth,
                                 String number, String description, String revision, String sapNumber,
                                 String sapRevision, String quantity) {
        var row = sheet.createRow(rowNumber);
        row.createCell(depth).setCellValue(depth);
        row.createCell(9).setCellValue(number);
        row.createCell(10).setCellValue(description);
        row.createCell(12).setCellValue(revision);
        row.createCell(19).setCellValue(sapNumber);
        row.createCell(20).setCellValue(sapRevision);
        if (!quantity.isBlank()) row.createCell(13).setCellValue(quantity);
    }
}