package com.peb.service.planning;

import java.math.BigDecimal;
import java.util.*;

public class PlanningRules {
        public record Part(String number, String description, long count, List<String> revisions) {}
        public record ArticleOption(String number, String revision, String description, long count,
                                    String boschSapNumber, String boschSapRevision, boolean missingMasterData) {}

        public static List<Part> parts(PlanningData data) {
                if (!data.articleMasters.isEmpty()) {
                        Map<String, List<ArticleOption>> grouped = new LinkedHashMap<>();
                        articleOptions(data).forEach(article -> grouped.computeIfAbsent(article.number(), key -> new ArrayList<>()).add(article));
                        return grouped.values().stream().map(articles -> new Part(articles.get(0).number(), articles.get(0).description(),
                                articles.stream().mapToLong(ArticleOption::count).sum(), articles.stream().map(ArticleOption::revision).distinct().sorted().toList())).toList();
                }
                Map<String, List<PlanningData.StructureRow>> groups = new LinkedHashMap<>();
                for (var row : data.structure) groups.computeIfAbsent(row.cells().get("J"), key -> new ArrayList<>()).add(row);
                return groups.entrySet().stream().map(entry -> new Part(entry.getKey(), entry.getValue().get(0).cells().get("K"),
                                entry.getValue().size(), entry.getValue().stream().map(row -> row.cells().get("M"))
                                .filter(value -> !value.isBlank()).distinct().sorted().toList())).toList();
        }

        public static List<ArticleOption> articleOptions(PlanningData data) {
                if (!data.articleMasters.isEmpty()) return data.articleMasters.stream().map(article -> new ArticleOption(
                        article.customerErpNumber(), article.customerRevision(), article.description(), article.sourceRows().size(),
                        article.boschSapNumber(), article.boschSapRevision(), article.missingMasterData())).toList();
                Map<String, List<PlanningData.StructureRow>> groups = new LinkedHashMap<>();
                for (var row : data.structure) groups.computeIfAbsent(identity(row.cells().get("J"), row.cells().get("M")), key -> new ArrayList<>()).add(row);
                return groups.values().stream().map(rows -> new ArticleOption(rows.get(0).cells().get("J"), rows.get(0).cells().get("M"),
                        rows.get(0).cells().get("K"), rows.size(), "", "", rows.get(0).cells().get("K").isBlank())).toList();
        }

        public static List<Map<String, String>> normalizeZones(PlanningData data, List<Map<String, String>> rows) {
                List<Map<String, String>> result = copyRows(rows, ZONE_HEADERS.size());
                Map<String, Part> parts = new HashMap<>();
                parts(data).forEach(part -> parts.put(part.number(), part));
                Map<String, String> names = new LinkedHashMap<>();
                Set<String> usedParts = new HashSet<>();
                Map<String, String> numbersByName = new HashMap<>();
                for (var row : result) {
                        require(!row.get("A").isBlank() && !row.get("B").isBlank(), "Zone_Nr und Zone_Name sind erforderlich.");
                        String previous = names.putIfAbsent(row.get("A"), row.get("B"));
                        require(previous == null || previous.equals(row.get("B")), "Eine Zonennummer muss immer denselben Namen haben.");
                        String number = numbersByName.putIfAbsent(row.get("B"), row.get("A"));
                        require(number == null || number.equals(row.get("A")), "Ein Zonenname muss eindeutig einer Zonennummer zugeordnet sein.");
                        List<ArticleOption> revisions = articleOptions(data).stream().filter(article -> article.number().equals(row.get("D"))).toList();
                        ArticleOption article = revisions.stream().filter(candidate -> candidate.revision().equals(row.get("G"))).findFirst().orElse(null);
                        if (article == null && row.get("G").isBlank() && revisions.size() == 1) article = revisions.get(0);
                        require(article != null, "Unbekannte oder nicht ausgewaehlte Revision fuer ERP_Nr: " + row.get("D"));
                        String revision = article.revision();
                        require(usedParts.add(identity(article.number(), revision)), "ERP-Nummer und Revision bereits einer Zone zugeordnet: " + article.number() + " / " + revision);
                        row.put("E", revisions.size() > 1 ? "WAHR" : "FALSCH");
                        row.put("F", String.join(", ", revisions.stream().map(ArticleOption::revision).distinct().sorted().toList()));
                        row.put("G", revision);
                        row.put("H", article.number() + (revision.isBlank() ? "" : "_" + revision));
                        row.put("I", article.description());
                        if (row.get("J").isBlank()) row.put("J", Long.toString(article.count()));
                        try { require(new BigDecimal(row.get("J")).signum() > 0, "Zielmenge muss groesser als null sein."); }
                        catch (NumberFormatException exception) { throw new IllegalArgumentException("Ungueltige Zielmenge fuer " + article.number()); }
                }
                Map<String, Set<String>> edges = new LinkedHashMap<>();
                names.keySet().forEach(number -> edges.put(number, new LinkedHashSet<>()));
                for (var row : result) {
                        String target = row.get("P");
                        if (target.isBlank()) row.put("Q", "");
                        else {
                                require(names.containsKey(target), "Unbekannte Empfaengerzone: " + target);
                                require(!target.equals(row.get("A")), "Eine Zone darf nicht an sich selbst liefern.");
                                row.put("Q", names.get(target));
                                edges.get(row.get("A")).add(target);
                        }
                }
                Map<String, Integer> degrees = new LinkedHashMap<>();
                names.keySet().forEach(number -> degrees.put(number, 0));
                edges.values().forEach(targets -> targets.forEach(target -> degrees.merge(target, 1, Integer::sum)));
                Deque<String> ready = new ArrayDeque<>();
                degrees.forEach((number, degree) -> { if (degree == 0) ready.add(number); });
                int visited = 0;
                while (!ready.isEmpty()) {
                        String number = ready.remove();
                        visited++;
                        for (String target : edges.get(number)) if (degrees.merge(target, -1, Integer::sum) == 0) ready.add(target);
                }
                require(visited == names.size(), "Die Lieferbeziehungen enthalten einen Zyklus.");
                data.deliveries = result.stream().filter(row -> !row.get("P").isBlank()).map(row -> new PlanningData.ZoneDelivery(
                        row.get("A"), row.get("P"), row.get("D"), row.get("G"), row.get("J"))).toList();
                return result;
        }

        public static List<Map<String, String>> normalizeOutput(PlanningData data, List<Map<String, String>> rows) {
                List<Map<String, String>> result = copyRows(rows, OUTPUT_HEADERS.size());
                Map<String, Map<String, String>> zonesByPart = new HashMap<>();
                data.zones.forEach(zone -> zonesByPart.put(identity(zone.get("D"), zone.get("G")), zone));
                Set<String> materialIdentities = new HashSet<>();
                articleOptions(data).forEach(article -> materialIdentities.add(identity(article.number(), article.revision())));
                for (var row : result) {
                    var zone = zonesByPart.get(identity(row.get("B"), row.get("E")));
                    if (zone == null) zone = data.zones.stream().filter(candidate -> candidate.get("D").equals(row.get("B")) && candidate.get("B").equals(row.get("A"))).findFirst().orElse(null);
                    require(zone != null && zone.get("B").equals(row.get("A")), "Baugruppe gehoert nicht zur ausgewaehlten Zone.");
                    row.put("C", zone.get("J"));
                    row.put("F", zone.get("I"));
                        require(Set.of("WAHR", "FALSCH").contains(row.get("J")), "SAP_AG_plausibel muss WAHR oder FALSCH sein.");
                        if (!row.get("T").isBlank()) {
                                require(materialIdentities.contains(identity(row.get("T"), row.get("W"))), "Unbekannte Material-ERP-Nummer/Revision.");
                        }
                }
                return result;
        }

        public static List<Map<String, String>> generate(PlanningData data) {
                require(!data.zones.isEmpty(), "Zuerst Zonen konfigurieren.");
                Map<String, List<Map<String, String>>> boundaries = new HashMap<>();
                data.zones.forEach(zone -> boundaries.computeIfAbsent(identity(zone.get("D"), zone.get("G")), key -> new ArrayList<>()).add(zone));
                Map<Integer, List<PlanningData.StructureRow>> children = new HashMap<>();
                Map<String, List<PlanningData.StructureRow>> occurrences = new HashMap<>();
                for (var row : data.structure) {
                        occurrences.computeIfAbsent(identity(row.cells().get("J"), row.cells().get("M")), key -> new ArrayList<>()).add(row);
                        if (row.parentRow() != null) children.computeIfAbsent(row.parentRow(), key -> new ArrayList<>()).add(row);
                }
                List<Map<String, String>> result = new ArrayList<>();
                for (var zone : data.zones) {
                        int before = result.size();
                        for (var root : occurrences.getOrDefault(identity(zone.get("D"), zone.get("G")), List.of())) appendMaterials(result, zone, root, BigDecimal.ONE, children, boundaries);
                        if (before == result.size()) result.add(outputRow(zone));
                }
                return normalizeOutput(data, result);
        }

        private static void appendMaterials(List<Map<String, String>> result, Map<String, String> zone,
                                                                                PlanningData.StructureRow parent, BigDecimal multiplier,
                                                                                Map<Integer, List<PlanningData.StructureRow>> children,
                                                                                Map<String, List<Map<String, String>>> boundaries) {
                for (var child : children.getOrDefault(parent.sourceRow(), List.of())) {
                        String rawQuantity = child.cells().get("N");
                        BigDecimal quantity;
                        try { quantity = rawQuantity.isBlank() ? BigDecimal.ONE : new BigDecimal(rawQuantity.replace(".", "").replace(',', '.')); }
                        catch (NumberFormatException exception) { throw new IllegalArgumentException("Ungueltige Menge in Excel-Zeile " + child.sourceRow()); }
                        require(quantity.signum() >= 0, "Negative Menge in Excel-Zeile " + child.sourceRow());
                        BigDecimal total = multiplier.multiply(quantity);
                        List<Map<String, String>> targetZones = boundaries.getOrDefault(identity(child.cells().get("J"), child.cells().get("M")), List.of());
                        boolean deliveredHere = targetZones.stream().anyMatch(target -> target.get("P").equals(zone.get("A")));
                        if (deliveredHere || !children.containsKey(child.sourceRow())) {
                                Map<String, String> row = outputRow(zone);
                                row.put("S", total.stripTrailingZeros().toPlainString());
                                row.put("T", child.cells().get("J"));
                                row.put("W", child.cells().get("M"));
                                row.put("Y", child.cells().get("J") + (child.cells().get("M").isBlank() ? "" : "_" + child.cells().get("M")));
                                row.put("Z", child.cells().get("K"));
                                row.put("Q", child.cells().get("Q"));
                                result.add(row);
                                require(result.size() <= 50000, "Maximal 50.000 Ausgabezeilen erlaubt.");
                        } else appendMaterials(result, zone, child, total, children, boundaries);
                }
        }

        private static Map<String, String> outputRow(Map<String, String> zone) {
                Map<String, String> row = PlanningData.emptyRow(OUTPUT_HEADERS.size());
                row.put("A", zone.get("B")); row.put("B", zone.get("D")); row.put("C", zone.get("J"));
                row.put("D", zone.get("F")); row.put("E", zone.get("G")); row.put("F", zone.get("I")); row.put("J", "FALSCH");
                return row;
        }

        public static List<Map<String, String>> normalizeArticles(PlanningData data, List<Map<String, String>> rows) {
                List<Map<String, String>> normalized = copyRows(rows, ARTICLE_HEADERS.size());
                Map<String, PlanningData.ArticleMaster> existing = new HashMap<>();
                data.articleMasters.forEach(article -> existing.put(identity(article.customerErpNumber(), article.customerRevision()), article));
                Set<String> identities = new HashSet<>();
                for (var row : normalized) {
                        String number = row.get("A"); String revision = row.get("B");
                        require(!number.isBlank(), "Kunden-ERP-Nummer ist erforderlich.");
                        require(identities.add(identity(number, revision)), "Kunden-ERP-Nummer und Revision muessen eindeutig sein.");
                        require(row.get("D").isBlank() || !row.get("C").isBlank(), "SAP-Revision kann nicht ohne Bosch-SAP-Nummer gepflegt werden.");
                        PlanningData.ArticleMaster previous = existing.get(identity(number, revision));
                        row.put("F", row.get("E").isBlank() || revision.isBlank() || row.get("C").isBlank()
                                ? "FEHLENDE_STAMMDATEN" : "VOLLSTAENDIG");
                        row.put("G", previous == null ? "0" : Integer.toString(previous.sourceRows().size()));
                }
                return normalized;
        }

        public static List<Map<String, String>> normalizeWorkplaces(List<Map<String, String>> rows,
                                                                     List<Map<String, String>> zones) {
                List<Map<String, String>> normalized = copyRows(rows, WORKPLACE_HEADERS.size());
                Set<String> workplaceIds = new HashSet<>();
                Set<String> zoneNumbers = new HashSet<>();
                zones.forEach(zone -> zoneNumbers.add(zone.get("A")));
                Map<String, String> zoneNames = new HashMap<>();
                zones.forEach(zone -> zoneNames.put(zone.get("A"), zone.get("B")));
                for (var row : normalized) {
                        require(!row.get("B").isBlank() && !row.get("C").isBlank(), "Arbeitsplatz-ID und Name sind erforderlich.");
                        require(workplaceIds.add(row.get("B")), "Ein Arbeitsplatz darf nur einer Zone zugeordnet sein: " + row.get("B"));
                        require(zoneNumbers.contains(row.get("A")), "Unbekannte Arbeitsplatz-Zone: " + row.get("A"));
                        if (!row.get("D").isBlank()) {
                                try { require(new BigDecimal(row.get("D")).signum() >= 0, "Taktzeit darf nicht negativ sein."); }
                                catch (NumberFormatException exception) { throw new IllegalArgumentException("Ungueltige Arbeitsplatz-Taktzeit."); }
                        }
                        row.put("E", zoneNames.get(row.get("A")));
                }
                return normalized;
        }

        private static String identity(String number, String revision) { return (number == null ? "" : number) + "\u0000" + (revision == null ? "" : revision); }

        private static List<Map<String, String>> copyRows(List<Map<String, String>> rows, int width) {
                require(rows != null && rows.size() <= 50000, "Maximal 50.000 Zeilen erlaubt.");
                List<Map<String, String>> result = new ArrayList<>();
                Set<String> identities = new HashSet<>();
                for (var source : rows) {
                        require(source != null, "Leere Tabellenzeile.");
                        Map<String, String> target = PlanningData.emptyRow(width);
                            String identity = source.get("_rowId");
                            if (identity == null) identity = UUID.randomUUID().toString();
                            require(identity.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") && identities.add(identity), "Ungueltige oder doppelte Zeilenidentitaet.");
                        for (String column : target.keySet()) {
                                String value = source.getOrDefault(column, "");
                                require(value != null && value.length() <= 4000, "Ungueltiger Zellenwert in Spalte " + column);
                                target.put(column, value.trim());
                        }
                        target.put("_rowId", identity);
                        result.add(target);
                }
                return result;
        }

        private static void require(boolean condition, String message) {
                if (!condition) throw new IllegalArgumentException(message);
        }
    public static final List<String> ZONE_HEADERS = List.of(
            "Zone_Nr", "Zone_Name", "Fertigstellung_Lagerplatz", "Ziel_Baugruppe_SNR", "SNR_hat_Versionen",
            "bekante_Revisionen", "Rev_Auswahl", "Ziel_BG_SNR_Rev", "Ziel_BG_Name", "Anzahl_Ziel_BG_je_Zonentakt",
            "Zone_Zieltakt", "avg_Zieltakt_diese_Baugruppe_min", "avg_Zieltakt_alle_BG_per_Zone", "ausgehend_BG_pro_Stunde",
            "ausgehend_alle_BG_je_Zone", "Liefern_an_Zone_Nr", "Empf\u00e4nger_Zone_Name", "Liefern_an_Lagerplatz",
            "KLT-Typ", "AnZ_je_KLT_Beladung", "Gewicht_Teile", "Gewicht_Beladung", "Verp_Breite", "Verp_Tiefe",
            "Verp_H\u00f6he", "Tranport_Mittel", "Verp_Auf_Tranposrtmittel", "somit_Einh_auf_Transportmittel",
            "EPK_(Einheiten_per_Kanban)", "Bemerkung", "Verpackungen_pro_Stunde",
            "insges_ausgehend_Verpackungen_je_Stunde_aus_Zone", "insges_Verp_zur_Zielzone_pro_Stunde",
            "Zuordnung_Transporteur", "reserve", "letzte Spalte Pivot");
        public static final List<String> ARTICLE_HEADERS = List.of("Kunden-ERP-Nr.", "Kundenrevision", "Bosch-SAP-Nr.", "SAP-Revision", "Beschreibung", "Stammdatenstatus", "Importverwendungen");
        public static final List<String> WORKPLACE_HEADERS = List.of("Zone_Nr", "Arbeitsplatz_ID", "Arbeitsplatz_Name", "Taktzeit_Minuten", "Zonenname");
    public static final List<String> OUTPUT_HEADERS = List.of(
            "Zone", "Ziel_Baugruppe_SNR", "Anzahl_Versionen", "bekannte_Revisionen", "Rev_Auswahl", "Zeil_GB_Name",
            "Prozesschritt _ID", "Baufortschritt_Modellfilter", "SAP_AG", "SAP_AG_plausibel", "Prozess_Gruppe",
            "Prozess_Beschreibung", "Reihenfolge", "Anzahl Prz - Wiederholungen", "Dauer gesch\u00e4tzt je 1 Durchf\u00fchrung [sek]",
            "Bemerkung", "Zu_APL", "Dauer [min] alle Wdh", "Mat_Anzahl je 1 Prz - Ausf\u00fchrung", "Mat_Bauteil_SNR",
            "SNR_hat_Versionen", "bekannte_Revisionen", "Revision_Auswahl", "Ist_Verbrauchsmaterial_ausserhalb_BOM",
            "Mat_SNR_Rev", "s_Bautei_Name_plus", "KLT-Typ", "AnZ/KLTBeladung", "Mat_Verbau_Proc_wdh", "Wzg_ID",
            "Wzg Name", "ben\u00f6tigt PM\u00dc", "Bemerkung", "Hilfsmittel_ID", "Hilfsmittel Bezeichnung",
            "Anzahl_Hilfmittel_gleichzeitig", "Hilfsmittel Z-Nummer", "Bemerkung Hilfsmittel", "Verbr_Sachnummer",
            "Verbrauchsmaterial", "Verbr_Mat_Verpackung_Typ", "Verbr_Mat_Anzahl_pro_Beladung",
            "Verbr_Mat_Ladungen_pro_h_Sch\u00e4tzung", "Bemerkung_Verbrauchsmaterial", "pivot letzte Spalte");
}