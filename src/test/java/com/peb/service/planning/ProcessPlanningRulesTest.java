package com.peb.service.planning;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ProcessPlanningRulesTest {
    @Test
    void sumsSequentialExecutionsUsesApprovedStudyAndKeepsResourcesOnOneOperation() {
        PlanningData data = new PlanningData();
        data.articleMasters.add(new PlanningData.ArticleMaster("0010", "A", "500010", "01", "Assembly",
                Map.of(), List.of(), false));
        var zone = PlanningData.emptyRow(36);
        zone.put("A", "10"); zone.put("B", "Assembly zone"); zone.put("D", "0010"); zone.put("G", "A"); zone.put("J", "5"); zone.put("K", "2");
        data.zones.add(zone);
        data.workplaces.add(new PlanningData.Workplace("wp-1", "10", "WS-1", "Montage 1", "1"));
        data.processStepGroups.add(new PlanningData.ProcessStepGroup("group-1", "Vormontage", 10));
        var resources = List.of(
                new PlanningData.ResourceRequirement("res-1", PlanningData.ResourceType.MATERIAL, "0001", "A", "Schraube", BigDecimal.valueOf(4), "Stk"),
                new PlanningData.ResourceRequirement("res-2", PlanningData.ResourceType.TOOL, "TOOL-1", "", "Drehmomentschluessel", BigDecimal.ONE, "Stk"),
                new PlanningData.ResourceRequirement("res-3", PlanningData.ResourceType.AID, "AID-1", "", "Montagehilfe", BigDecimal.ONE, "Stk"));
        var operation = new PlanningData.OperationStep("op-1", "Verschrauben", "Mit Sollmoment anziehen", 10, 2,
                BigDecimal.valueOf(12), BigDecimal.valueOf(8), "MTM", "MTM-4711", BigDecimal.valueOf(9), "wp-1", resources);
        data.processTemplates.add(new PlanningData.ProcessTemplateRevision("template-1", 1, "Gehäuse montieren", List.of(operation)));
        data.processUses.add(new PlanningData.ProcessUse("use-1", "group-1", "template-1", 1, "0010", "A"));

        ProcessPlanningRules.validate(data);
        var load = ProcessPlanningRules.balance(data).get(0);
        assertThat(load.workContentSeconds()).isEqualByComparingTo("16");
        assertThat(load.targetSeconds()).isEqualByComparingTo("60");
        assertThat(load.loadPercent()).isEqualByComparingTo("26.67");
        assertThat(load.operations()).singleElement().satisfies(step -> {
            assertThat(step.effectiveSeconds()).isEqualByComparingTo("16");
            assertThat(step.effectiveTimeSource()).isEqualTo("ZEITSTUDIE_MTM");
            assertThat(step.executionsPerUnit()).isEqualTo(2);
        });
        assertThat(operation.resources()).hasSize(3);
        assertThat(operation.sequence()).isEqualTo(10);
    }

    @Test
    void fallsBackToEstimateAndRejectsDuplicateOrderAndInvalidResource() {
        var step = new PlanningData.OperationStep("op-1", "Montieren", "", 10, 1,
                BigDecimal.valueOf(14), null, "", "", null, "", List.of());
        assertThat(ProcessPlanningRules.effectiveSeconds(step)).isEqualByComparingTo("14");
        assertThat(ProcessPlanningRules.effectiveSource(step)).isEqualTo("SCHAETZUNG");
        var duplicate = new PlanningData.OperationStep("op-2", "Pruefen", "", 10, 1,
                BigDecimal.ONE, null, "", "", null, "", List.of());
        assertThatThrownBy(() -> ProcessPlanningRules.validateOperations(List.of(step, duplicate)))
                .hasMessageContaining("Reihenfolge");
        var badResource = new PlanningData.ResourceRequirement("res-1", PlanningData.ResourceType.MATERIAL,
                "", "", "", BigDecimal.ONE, "Stk");
        var invalid = new PlanningData.OperationStep("op-3", "Verpacken", "", 20, 1,
                BigDecimal.ONE, null, "", "", null, "", List.of(badResource));
        assertThatThrownBy(() -> ProcessPlanningRules.validateOperations(List.of(invalid)))
                .hasMessageContaining("Artikelnummer oder Beschreibung");
    }

    @Test
    void requiresAssignedWorkplaceAndTimeBeforeRelease() {
        var data = new PlanningData();
        data.processStepGroups.add(new PlanningData.ProcessStepGroup("group", "Montage", 10));
        var operation = new PlanningData.OperationStep("op", "Montieren", "", 10, 1,
                null, null, "", "", null, "", List.of());
        data.processTemplates.add(new PlanningData.ProcessTemplateRevision("template", 1, "Station", List.of(operation)));
        data.processUses.add(new PlanningData.ProcessUse("use", "group", "template", 1, "", ""));
        ProcessPlanningRules.validate(data);
        assertThatThrownBy(() -> ProcessPlanningRules.validateForRelease(data)).hasMessageContaining("keinem Arbeitsplatz");
        data.processTemplates.set(0, new PlanningData.ProcessTemplateRevision("template", 1, "Station", List.of(
                new PlanningData.OperationStep("op", "Montieren", "", 10, 1, BigDecimal.ONE, null, "", "", null, "", List.of()))));
        assertThatThrownBy(() -> ProcessPlanningRules.validateForRelease(data)).hasMessageContaining("keinem Arbeitsplatz");
        data.workplaces.add(new PlanningData.Workplace("wp", "1", "AP-1", "Montageplatz", "1"));
        data.processTemplates.set(0, new PlanningData.ProcessTemplateRevision("template", 1, "Station", List.of(
                new PlanningData.OperationStep("op", "Montieren", "", 10, 1, null, null, "", "", null, "wp", List.of()))));
        assertThatThrownBy(() -> ProcessPlanningRules.validateForRelease(data)).hasMessageContaining("Schaetzung oder Zeitstudie");
    }

    @Test
    void validatesOperationDocumentReferencesAndExternalUrls() {
        var documents = List.of(
                new PlanningData.OperationDocument("doc-1", "DRAWING", "Montagezeichnung", "/documents/drawing-1", "Ansicht B", false),
                new PlanningData.OperationDocument("doc-2", "PROCESS_DESCRIPTION", "Prozessvorgabe", "https://example.org/process/42", "", true));
        var valid = new PlanningData.OperationStep("op-1", "Montieren", "", 10, 1,
                BigDecimal.TEN, null, "", "", null, "", List.of(), documents);
        ProcessPlanningRules.validateOperations(List.of(valid));

        var unsafeExternal = new PlanningData.OperationDocument("doc-3", "DECISION", "Vorgabe", "javascript:alert(1)", "", true);
        var invalid = new PlanningData.OperationStep("op-2", "Prüfen", "", 20, 1,
                BigDecimal.ONE, null, "", "", null, "", List.of(), List.of(unsafeExternal));
        assertThatThrownBy(() -> ProcessPlanningRules.validateOperations(List.of(invalid)))
                .hasMessageContaining("HTTP(S)-URL");
    }
}