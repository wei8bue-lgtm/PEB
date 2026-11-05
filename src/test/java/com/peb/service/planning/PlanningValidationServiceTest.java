package com.peb.service.planning;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PlanningValidationServiceTest {
    @Test
    void flagsMissingSapMappingsAndAllowsSharedSapNumbersAcrossDifferentErpArticles() {
        PlanningData data = new PlanningData();
        data.articleMasters.add(new PlanningData.ArticleMaster("100", "A", "SAP-1", "01", "A", Map.of(), List.of(), false));
        data.articleMasters.add(new PlanningData.ArticleMaster("200", "B", "SAP-1", "01", "B", Map.of(), List.of(), false));
        data.articleMasters.add(new PlanningData.ArticleMaster("300", "A", "", "", "C", Map.of(), List.of(), true));
        var report = new PlanningValidationService().numberMappings(data, "Test", 3);
        assertThat(report.rows()).filteredOn(row -> row.boschSapNumber().isBlank()).singleElement()
                .satisfies(row -> assertThat(row.status()).isEqualTo("INCOMPLETE_MAPPING"));
        assertThat(report.rows()).filteredOn(row -> row.boschSapNumber().equals("SAP-1")).hasSize(2)
                .allSatisfy(row -> assertThat(row.status()).isEqualTo("MAPPED"));
    }

    private PlanningData fixture(BigDecimal screwUsage, String destinationTakt) {
        PlanningData data = new PlanningData();
        data.articleMasters.add(new PlanningData.ArticleMaster("100", "A", "SAP100", "01", "Endbaugruppe", Map.of(), List.of(), false));
        data.articleMasters.add(new PlanningData.ArticleMaster("200", "A", "SAP200", "01", "Zulieferbaugruppe", Map.of(), List.of(), false));
        data.articleMasters.add(new PlanningData.ArticleMaster("300", "A", "SAP300", "01", "Schraube", Map.of(), List.of(), false));
        data.structure.add(node(2, 0, null, "100", "A", "Endbaugruppe", "1"));
        data.structure.add(node(3, 1, 2, "300", "A", "Schraube", "3"));
        data.structure.add(node(4, 1, 2, "200", "A", "Zulieferbaugruppe", "2"));
        data.structure.add(node(5, 2, 4, "300", "A", "Schraube", "50"));
        var source = zone("2", "Bereitstellung", "200", "A", "2", "1", "1");
        var destination = zone("1", "Montage", "100", "A", "10", destinationTakt, "");
        data.zones = List.of(destination, source);
        data.deliveries = List.of(new PlanningData.ZoneDelivery("2", "1", "200", "A", "2"));
        data.workplaces.add(new PlanningData.Workplace("wp-1", "1", "WS-1", "Montageplatz", ""));
        data.processStepGroups.add(new PlanningData.ProcessStepGroup("group-1", "Montage", 10));
        var resources = List.of(
                new PlanningData.ResourceRequirement("screw", PlanningData.ResourceType.MATERIAL, "300", "A", "Schraube", screwUsage, "Stk", "Z-300", "KLT", BigDecimal.TEN, "Stk/KLT", "KLT-01"),
                new PlanningData.ResourceRequirement("assembly", PlanningData.ResourceType.MATERIAL, "200", "A", "Zulieferbaugruppe", BigDecimal.valueOf(2), "Stk"),
                new PlanningData.ResourceRequirement("tool", PlanningData.ResourceType.TOOL, "TOOL-1", "A", "Drehmomentwerkzeug", BigDecimal.ONE, "Stk", "WZ-4711", "", null, "", ""),
                new PlanningData.ResourceRequirement("aid", PlanningData.ResourceType.AID, "AID-1", "A", "Montagehilfe", BigDecimal.ONE, "Stk"));
        var step = new PlanningData.OperationStep("step-1", "Verschrauben", "", 10, 1, BigDecimal.TEN,
                null, "", "", null, "wp-1", resources);
        data.processTemplates.add(new PlanningData.ProcessTemplateRevision("template-1", 1, "Montageprozess", List.of(step)));
        data.processUses.add(new PlanningData.ProcessUse("use-1", "group-1", "template-1", 1, "100", "A"));
        return data;
    }

    @Test
    void comparesMaterialsPerAssemblyAndStopsAtDeliveredAssemblyBoundary() {
        var service = new PlanningValidationService();
        var match = service.materialUsage(fixture(BigDecimal.valueOf(3), "2"), "Test", 1, "1", null);
        assertThat(match.rows()).filteredOn(row -> row.articleNumber().equals("300")).singleElement().satisfies(row -> {
            assertThat(row.expectedQuantity()).isEqualByComparingTo("3");
            assertThat(row.actualQuantity()).isEqualByComparingTo("3");
            assertThat(row.status()).isEqualTo("MATCH");
        });
        assertThat(match.rows()).filteredOn(row -> row.articleNumber().equals("200")).singleElement().satisfies(row -> {
            assertThat(row.expectedQuantity()).isEqualByComparingTo("2");
            assertThat(row.actualQuantity()).isEqualByComparingTo("2");
        });
        assertThat(match.rows()).noneMatch(row -> row.articleNumber().equals("300") && row.expectedQuantity().compareTo(BigDecimal.valueOf(100)) > 0);
        var shortage = service.materialUsage(fixture(BigDecimal.valueOf(2), "2"), "Test", 1, "1", null);
        assertThat(shortage.rows()).filteredOn(row -> row.articleNumber().equals("300")).singleElement().satisfies(row -> {
            assertThat(row.variance()).isEqualByComparingTo("-1"); assertThat(row.status()).isEqualTo("SHORTAGE");
        });
        var excess = service.materialUsage(fixture(BigDecimal.valueOf(4), "2"), "Test", 1, "1", null);
        assertThat(excess.rows()).filteredOn(row -> row.articleNumber().equals("300")).singleElement().satisfies(row -> assertThat(row.status()).isEqualTo("EXCESS"));
    }

    @Test
    void checksSupplyPerHourAndBuildsFilteredResourceReports() {
        PlanningData data = fixture(BigDecimal.valueOf(3), "2");
        var supply = new PlanningValidationService().zoneSupply(data, "Test", 4);
        assertThat(supply.rows()).singleElement().satisfies(row -> {
            assertThat(row.suppliedPerHour()).isEqualByComparingTo("120");
            assertThat(row.requiredPerHour()).isEqualByComparingTo("600");
            assertThat(row.status()).isEqualTo("SHORTAGE");
        });
        var tools = new PlanningValidationService().resources(data, "Test", 4, "tools", "1", "wp-1", "100", "A");
        assertThat(tools.rows()).singleElement().satisfies(row -> {
            assertThat(row.resourceType()).isEqualTo("TOOL"); assertThat(row.specialNumber()).isEqualTo("WZ-4711");
            assertThat(row.operationName()).isEqualTo("Verschrauben");
        });
        var materials = new PlanningValidationService().resources(data, "Test", 4, "materials", "1", "wp-1", "100", "A");
        assertThat(materials.rows()).filteredOn(row -> row.articleNumber().equals("300")).singleElement().satisfies(row -> {
            assertThat(row.containerAssignment()).isEqualTo("KLT-01");
            assertThat(row.containersPerUnit()).isEqualByComparingTo("1");
            assertThat(row.unitsPerContainer()).isEqualByComparingTo("10");
        });
    }

    private PlanningData.StructureRow node(int sourceRow, int depth, Integer parent, String number, String revision,
                                           String description, String quantity) {
        var cells = PlanningData.emptyRow(19); cells.put("J", number); cells.put("M", revision); cells.put("K", description); cells.put("N", quantity);
        return new PlanningData.StructureRow(sourceRow, depth, parent, cells);
    }

    private Map<String, String> zone(String number, String name, String article, String revision,
                                     String quantity, String takt, String receiver) {
        var row = PlanningData.emptyRow(36); row.put("A", number); row.put("B", name); row.put("D", article);
        row.put("G", revision); row.put("J", quantity); row.put("K", takt); row.put("P", receiver); return row;
    }
}