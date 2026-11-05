package com.peb.service.planning;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PlanningRulesTest {
    private PlanningData sample() {
        PlanningData data = new PlanningData();
        for (int index = 0; index < 3; index++) {
            var cells = PlanningData.emptyRow(19);
            cells.put("J", "00" + index); cells.put("K", "Teil " + index); cells.put("N", "2");
            data.structure.add(new PlanningData.StructureRow(index + 2, index, index == 0 ? null : index + 1, cells));
        }
        return data;
    }

    private Map<String, String> zone(String number, String part, String target) {
        var row = PlanningData.emptyRow(36);
        row.put("A", number); row.put("B", "Zone " + number); row.put("D", part); row.put("P", target);
        return row;
    }

    @Test
    void derivesLinkedFieldsAndRejectsDuplicateAssignments() {
        var data = sample();
        var row = PlanningRules.normalizeZones(data, List.of(zone("1", "000", ""))).get(0);
        assertThat(row.get("H")).isEqualTo("000");
        assertThat(row.get("I")).isEqualTo("Teil 0");
        assertThat(row.get("J")).isEqualTo("1");
        assertThatThrownBy(() -> PlanningRules.normalizeZones(data, List.of(zone("1", "000", ""), zone("2", "000", ""))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bereits");
    }

    @Test
    void rejectsSelfAndIndirectCyclesAndUnknownRecipients() {
        var data = sample();
        assertThatThrownBy(() -> PlanningRules.normalizeZones(data, List.of(zone("1", "000", "1")))).hasMessageContaining("selbst");
        assertThatThrownBy(() -> PlanningRules.normalizeZones(data, List.of(zone("1", "000", "2"), zone("2", "001", "3"), zone("3", "002", "1"))))
                .hasMessageContaining("Zyklus");
        assertThatThrownBy(() -> PlanningRules.normalizeZones(data, List.of(zone("1", "000", "9")))).hasMessageContaining("Unbekannte");
    }

    @Test
    void generatesMaterialQuantitiesAndStopsAtZoneBoundaries() {
        var data = sample();
        data.zones = PlanningRules.normalizeZones(data, List.of(zone("1", "000", "")));
        var output = PlanningRules.generate(data);
        assertThat(output).hasSize(1);
        assertThat(output.get(0).get("T")).isEqualTo("002");
        assertThat(output.get(0).get("S")).isEqualTo("4");
        data.zones = PlanningRules.normalizeZones(data, List.of(zone("1", "000", ""), zone("2", "001", "1")));
        output = PlanningRules.generate(data);
        assertThat(output.get(0).get("T")).isEqualTo("001");
        assertThat(output.get(0).get("S")).isEqualTo("2");
        assertThat(output.get(1).get("T")).isEqualTo("002");
    }

    @Test
    void verifiesOutputDependenciesAndBooleanDomain() {
        var data = sample();
        data.zones = PlanningRules.normalizeZones(data, List.of(zone("1", "000", "")));
        var row = PlanningRules.generate(data).get(0);
        row.put("C", "999");
        assertThat(PlanningRules.normalizeOutput(data, List.of(row)).get(0).get("C")).isEqualTo("1");
        row.put("J", "perhaps");
        assertThatThrownBy(() -> PlanningRules.normalizeOutput(data, List.of(row))).hasMessageContaining("WAHR");
        row.put("J", "WAHR"); row.put("A", "other");
        assertThatThrownBy(() -> PlanningRules.normalizeOutput(data, List.of(row))).hasMessageContaining("Baugruppe");
    }

    @Test
    void allowsSeparateRevisionTargetsAndManualMasterData() {
        var data = new PlanningData();
        var articleRows = List.of(article("0815", "A", "5000815", "01", "Version A"),
                article("0815", "B", "5000815", "02", "Version B"));
        var normalizedArticles = PlanningRules.normalizeArticles(data, articleRows);
        data.articleMasters = normalizedArticles.stream().map(row -> new PlanningData.ArticleMaster(row.get("A"), row.get("B"),
                row.get("C"), row.get("D"), row.get("E"), Map.of(), List.of(), false)).toList();
        var zones = PlanningRules.normalizeZones(data, List.of(target("1", "Montage", "0815", "A", "3"),
                target("1", "Montage", "0815", "B", "7")));
        assertThat(zones).extracting(row -> row.get("H")).containsExactly("0815_A", "0815_B");
        assertThat(zones).extracting(row -> row.get("J")).containsExactly("3", "7");
        assertThat(PlanningRules.parts(data)).singleElement().satisfies(part -> assertThat(part.revisions()).containsExactly("A", "B"));
    }

    @Test
    void validatesWorkplaceZoneUniquenessAndZoneAssignment() {
        var data = sample();
        data.zones = PlanningRules.normalizeZones(data, List.of(zone("1", "000", ""), zone("2", "001", "1")));
        var first = workplace("1", "WS-1", "Montageplatz", "12");
        var second = workplace("2", "WS-1", "Doppelbelegung", "8");
        assertThatThrownBy(() -> PlanningRules.normalizeWorkplaces(List.of(first, second), data.zones))
                .hasMessageContaining("nur einer Zone");
        second.put("B", "WS-2");
        assertThat(PlanningRules.normalizeWorkplaces(List.of(first, second), data.zones)).extracting(row -> row.get("E"))
                .containsExactly("Zone 1", "Zone 2");
        second.put("A", "99");
        assertThatThrownBy(() -> PlanningRules.normalizeWorkplaces(List.of(first, second), data.zones)).hasMessageContaining("Unbekannte");
    }

    @Test
    void resolvesAtBoundaryOnlyWhenExplicitlyDeliveredToCurrentZone() {
        var data = sample();
        var source = zone("1", "000", "");
        var target = zone("2", "001", "3");
        var destination = zone("3", "002", "");
        data.zones = PlanningRules.normalizeZones(data, List.of(source, target, destination));
        var output = PlanningRules.generate(data);
        assertThat(output.get(0).get("T")).isEqualTo("002");
        assertThat(output.get(0).get("S")).isEqualTo("4");
        assertThat(data.deliveries).singleElement().satisfies(delivery -> {
            assertThat(delivery.sourceZone()).isEqualTo("2");
            assertThat(delivery.destinationZone()).isEqualTo("3");
        });
    }

    private Map<String, String> article(String number, String revision, String sap, String sapRevision, String description) {
        var row = PlanningData.emptyRow(7); row.put("A", number); row.put("B", revision); row.put("C", sap);
        row.put("D", sapRevision); row.put("E", description); return row;
    }

    private Map<String, String> target(String number, String zoneName, String part, String revision, String quantity) {
        var row = zone(number, part, ""); row.put("B", zoneName); row.put("G", revision); row.put("J", quantity); return row;
    }

    private Map<String, String> workplace(String zone, String code, String name, String takt) {
        var row = PlanningData.emptyRow(5); row.put("A", zone); row.put("B", code); row.put("C", name); row.put("D", takt); return row;
    }
}