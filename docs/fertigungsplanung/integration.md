# Fertigungsplanung in SEP4Eng

## Einordnung

Der neue Bereich `/manufacturing-planning` verwaltet ein eigenstaendiges
Fertigungsplanungsprojekt neben mBOM und BOP. Der Einstieg auf der Startseite
steht direkt oberhalb der Dokumentenvorlagen; die Seitennavigation enthaelt
ebenfalls einen Eintrag. Bestehende eBOM-, CAD- und mBOM-Daten werden beim
Excel-Import nicht veraendert. Eine spaetere Uebernahme freigegebener Planungen
in mBOM/BOP sollte eine explizite Zuordnung zu deren Stammdaten verwenden.

## Planungsbereiche

1. Struktur und Artikelstamm: Import der ersten Tabelle einer `.xlsx`-Datei mit
  Kopfzeile in Zeile 1, ERP_Nr in J und einer belegten Ebenenspalte A-G je
  Strukturzeile. Alle gefundenen Spalten bleiben erhalten. Eltern-Kind-Beziehungen
  referenzieren die Excel-Quellzeile, sodass wiederholte Artikel verschiedene
  Verwendungsstellen behalten. Artikelstammidentitaet ist Kunden-ERP-Nummer plus
  Kundenrevision. Optionale Bosch-SAP-Nummer/Revision werden aus benannten
  Spalten eingelesen und sind im Artikelstamm manuell pflegbar. Wiederverwendete
  Baugruppen erhalten eine gemeinsame Breakdowndefinition mit Verwendungszeilen;
  abweichende Kinder oder Mengen werden markiert. Fehlende Nummer, Revision,
  Beschreibung oder SAP-Zuordnung sind als unvollstaendige Stammdaten sichtbar.
  Ein Neuimport ersetzt Struktur, Stammdaten, Zonen, Arbeitsplaetze und Ausgabe
  nur in der aktuellen Arbeitsrevision.
2. Zonen: Alle 36 Spalten A-AJ aus `Zonen_Vorgaben` bleiben verfuegbar. Eine
  Zeile bezeichnet eine Zielbaugruppe anhand ERP-Nummer D plus Revision G.
  Mehrere Revisionen derselben Nummer bleiben getrennt planbar. H/I zeigen
  zusammengesetzte Identitaet und Beschreibung. J ist die editierbare geplante
  Zielmenge; sie wird mit der Anzahl passender Strukturvorkommen vorbelegt.
  P/Q legen die zielbaugruppenspezifische Lieferbeziehung fest. Die gleiche
  Zone darf mehrere Ziele besitzen, Zonennummer und Name muessen konsistent
  bleiben. Selbstlieferungen und Lieferzyklen sind serverseitig gesperrt.
3. Arbeitsplaetze: Separate Tabelle mit Arbeitsplatz-ID, Name und optionaler
  Taktzeit. Eine Arbeitsplatz-ID kann genau einer Zone zugeordnet sein.
4. Fertigungsliste: Alle 45 Spalten A-AS aus `Output.xlsx` sind vorhanden.
   Die Tabelle startet leer. A bietet unterschiedliche Zonennamen an,
   B nur deren zugeordnete Baugruppen; C uebernimmt die Anzahl aus Zonen J.
  J erlaubt ausschliesslich WAHR/FALSCH. Zeilen koennen manuell angelegt,
  bearbeitet und geloescht oder per Knopfdruck erzeugt werden. Die Struktur
  kann nach Zone und Zielbaugruppe inklusive ihrer Unterstruktur gefiltert
  werden. Projektkopie dupliziert Stammdaten, Struktur, Zonen, Lieferungen,
  Arbeitsplaetze, Taetigkeiten und Materialverbrauch als neuen Arbeitsstand.
    Der Excel-Export enthaelt zusaetzliche `Nummernzuordnung`-, `Arbeitsplaetze`-,
    `Prozessplan`-, `Prozessressourcen`- und `LineBalancing`-Blaetter. Nummernberichte
    koennen Kunden-, SAP- oder beide Nummerngruppen ausgeben.

  ## Prozess- und Arbeitsplatzplanung

  Die Prozesshierarchie lautet `Schrittgruppe -> Prozess/logische Station ->
  Arbeitsschritt`. Prozessvorlagen sind innerhalb des Fertigungsplanungsprojekts
  wiederverwendbar; eine Prozessverwendung bindet die Vorlage an Schrittgruppe und
  optional eine Zielbaugruppe (`ERP-Nummer + Revision`). Der physische Arbeitsplatz
  wird dagegen je Arbeitsschritt gesetzt. Eine Änderung dieses Arbeitsplatzes in
  einer gemeinsam verwendeten Vorlage wird daher als Vorlagenänderung behandelt;
  eine lokale Änderung entkoppelt nur die ausgewählte Verwendung.

  Jeder Arbeitsschritt besitzt Reihenfolge, Beschreibung, positive Ausführungsanzahl
  je Stück der Zielbaugruppe, Schätzzeit, optionale MTM-/URS-/sonstige Zeitstudie,
  optionale Istzeit, Arbeitsplatz und eine Liste von Ressourcen. Ressourcen sind
  unter einem Arbeitsschritt verschachtelt und werden nicht in zusätzliche
  Prozesszeilen aufgespalten. Typen: `MATERIAL`, `TOOL`, `AID`. Jede Ressource kann
  Artikelnummer/Revision oder Beschreibung, Menge und Einheit tragen.

  Zeiten werden in Sekunden je Ausführung erfasst. Die effektive Planzeit ist die
  Zeitstudie, wenn vorhanden, andernfalls die Schätzung. Istzeit bleibt ein
  separater Messwert und steuert die Planungslast nicht. Arbeitsinhalt je Stück
  ist `effektive Sekunden je Ausführung * Ausführungsanzahl`. Die Auslastung wird
  pro Arbeitsplatz und Zielbaugruppe separat berechnet, nicht über verschiedene
  Zielbaugruppen zu einem Produktionsmix aggregiert. Zielzeit ist die explizite
  Arbeitsplatz-Taktzeit in Minuten, sonst der Zonenzieltakt in Minuten; beide
  werden für den Vergleich nach Sekunden umgerechnet. WorkerCount reduziert die
  Zeit nicht automatisch. Arbeitsschritte werden je Prozesssequenz sortiert;
  Transport- und Wartezeiten werden nur berücksichtigt, wenn sie als explizite
  Arbeitsschritte mit Dauer erfasst sind.

  Vor der Vorlagenänderung zeigt der Editor alle Verwendungsstellen der gewählten
  Revision. `LOCAL` erstellt eine unabhängige lokale Prozessvorlage für genau eine
  Verwendung. `SHARED` erzeugt eine neue gemeinsame Vorlagenrevision; der Anwender
  wählt explizit die Verwendungsstellen, die auf diese Revision wechseln sollen.
  Nicht ausgewählte Verwendungen und freigegebene Projekt-Snapshots bleiben auf
  ihrer bisherigen Revision. Noch unbenutzte Vorlagen können vor Zuordnung
  bearbeitet oder gelöscht werden. Die Vorlagenbibliothek ist im ersten Ansatz
  auf ein Planungsprojekt begrenzt, nicht global projektübergreifend.

  Bei der Freigabe müssen alle verwendeten Arbeitsschritte einem Arbeitsplatz
  zugeordnet sein und eine Schätzung oder Zeitstudie besitzen. Unbenutzte
  Vorlagen dürfen unvollständig bleiben. Das Prozess-Line-Balancing zeigt
  Arbeitsinhalt, Taktziel und Auslastung je Arbeitsplatz/Zielbaugruppe.

## Musterstücklisten-Versionen und Arbeitsfolgekarten

Ein Strukturimport kann ausdrücklich als neue Musterstücklisten-Version angelegt
werden. Der Planer erzeugt dafür einen neuen `WORKING`-Snapshot aus dem gewählten
aktuellen Stand, ersetzt darin Struktur, Artikelstamm und Baugruppendefinitionen
durch den Import und leert die daraus abgeleitete Fertigungsliste. Zonen,
Lieferbeziehungen, Arbeitsplätze und Prozessplanung werden übernommen und können
als Vorplanung angepasst werden. Der bisherige Snapshot bleibt unverändert. Neue
Planungsversionen lassen sich auch aus einem noch nicht freigegebenen Stand
verzweigen; dies ist keine Freigabe und ändert den formalen Release-Workflow nicht.

Der Gesamtlistenvergleich identifiziert Positionen über ihren hierarchischen Pfad
aus Kunden-ERP-Nummer/Revision und Vorkommen. Er zeigt `ADDED`, `REMOVED` und bei
geänderter Menge oder Beschreibung `CHANGED`, jeweils mit Quellzeile und alter/neuer
Menge. Die Einordnung erfolgt innerhalb des gewählten Versionspaares.

Jeder Arbeitsschritt kann Dokumentreferenzen der Typen Prozessbeschreibung,
Festlegung, Zeichnung, Notiz oder Sonstiges tragen. Eine Referenz enthält Titel,
optionalen Hinweis sowie URL oder anwendungsrelativen Dokumentpfad. Externe
Prozessvorgaben müssen absolute HTTP(S)-URLs sein. Die Anwendung speichert die
Referenz, lädt oder kopiert jedoch keine Binärdatei; bestehende Dokumente werden
über ihren stabilen Pfad/URL verknüpft. Dokumentreferenzen werden mit dem
Planungssnapshot versioniert und sowohl in der Prozessansicht als auch auf dem
Dokumentblatt der Arbeitsfolgekarte ausgegeben.

Die Arbeitsfolgekarte kann ausschließlich für eine freigegebene, plausible
Projektrevision als XLSX ausgegeben werden. Sie enthält sequenzierte Vorgänge,
Arbeitsplatz, Ausführungszahl, effektive Zeit und Ressourcen sowie die separaten
Dokumentreferenzen. Arbeitsplatz- und Reihenfolgeänderungen erfolgen auf einem
neuen `WORKING`-Snapshot; der Vorgänger und seine bereits ausgegebene Karte bleiben
unverändert. Der genaue fachliche Freigabeprozess für Arbeitsfolgekarten bleibt
offen; die Implementierung nutzt zunächst die vorhandene Projektfreigabe.

## Bedienbarkeit und POC-Scope

Die Planung wird als datenbankgestützte Webanwendung mit revisionsgebundenen
Snapshots, Projektversionsprüfung und den vorhandenen READ/WRITE/DELETE/EXECUTE-
Berechtigungen geführt. Damit können mehrere Planer denselben Planungsstand nutzen;
gleichzeitige Schreibzugriffe mit veralteter Projektversion werden abgewiesen.

Zonen weisen ERP-Artikelrevisionen zu. Beim Anlegen einer Fertigungslistenposition
wird die Zielbaugruppe vorausgewählt, wenn die gewählte Zone genau eine Baugruppe
zugeordnet hat; bei mehreren Zuordnungen bleibt die explizite Auswahl erforderlich.
Bauteile sind über den Artikel-/Zonenlistenwähler auswählbar. Bild-/Thumbnaildaten
sind in den gelieferten Importmodellen nicht enthalten und bleiben daher optional
außerhalb dieses POC.

Tätigkeiten einer Zielbaugruppe lassen sich auf eine zweite Zielbaugruppe kopieren.
Die Quelle bleibt bestehen; vorhandene Tätigkeiten am Ziel werden nach Bestätigung
ersetzt. Prozessvorlagen werden wiederverwendet, und die Zielverwendung kann danach
lokal abgekoppelt und angepasst werden. Reihenfolge lässt sich im Editor mit
Auf-/Ab-Aktionen verändern. Das Line-Balancing zeigt anschließend Arbeitsinhalt,
Takt und Auslastung je Arbeitsplatz/Zielbaugruppe als Engpassindikator.

Der POC befüllt die Fertigungsliste automatisch aus importierter Struktur und
Zonenzuordnung. Arbeitsplaner entscheiden weiterhin manuell über Prozessschritte,
Arbeitsplatz, Zeitansatz und Ressourcen. Es erfolgt keine automatische
Arbeitsverteilung oder Zeitermittlung; das Verschieben und anschließende
Line-Balancing dient der manuellen Engpassanalyse. Eine automatische Positionierung
neuer Bauteile in der Gesamtbaugruppe (UX-04) bleibt optional/später.

## Validierung, Materialbereitstellung und Reports

Der Report `numbers` plausibilisiert Kunden-ERP-Nummer plus Revision gegen die
Bosch-SAP-Zuordnung. Status: `MAPPED` oder `INCOMPLETE_MAPPING`. Mehrere
Kundenartikel dürfen dieselbe SAP-Nummer referenzieren. Jede Reportanfrage ist
an eine konkrete Projekt-Snapshotrevision gebunden.

Der Report `validation` vergleicht je Zielbaugruppe und Revision **pro Stück**
die erwartete, aufgelöste Stückliste mit den Prozessressourcen des Typs
`MATERIAL`. Ressourcenmenge wird mit der Ausführungsanzahl multipliziert. Die
Auflösung stoppt an einer Baugruppe, die über eine Lieferbeziehung an die
aktuelle Zone geliefert wird. Ergebnisse: `MATCH`, `SHORTAGE`, `EXCESS`. Ein
Arbeitsplatzfilter zeigt die Materialpositionen, die an diesem Arbeitsplatz
verwendet werden; der angezeigte Sollwert bleibt der vollständige BOM-Bedarf
dieses Zielartikels, nicht ein automatisch verteilter Arbeitsplatz-Sollanteil.

Der Report `zone-supply` rechnet Quellangebot und Empfängerbedarf in Stück pro
Stunde um: Zielmenge je Zonenschlag / Zieltakt in Minuten * 60. Der Bedarf
berücksichtigt alle Zielbaugruppen der Empfängerzone und deren Stücklistenbedarf
an der vereinbarten Liefergrenze. Status: `MATCH`, `SHORTAGE`, `EXCESS`.

Die Ressourcenreports `materials`, `tools`, `aids` und `all` lassen sich nach
Zone, Arbeitsplatz und Zielbaugruppe filtern. Werkzeug- und Hilfsmittelpositionen
verweisen auf Prozess und Arbeitsschritt und enthalten gepflegte Spezialnummern.
Materialressourcen können optional Containerzuordnung, Containertyp, Einheiten je
Container und Verpackungseinheit tragen. Container je Zielstück wird als
aufgerundeter Materialbedarf / Einheiten je Container berechnet; ohne gepflegte
Kapazität bleibt der Containerwert leer. Diese Verpackungsfelder sind ein
Arbeitsmodell; Mehrwegbehälter, Teilgebinde und Umverpackungen müssen fachlich
noch bestätigt werden.

Die Reportansicht filtert Validierungszustände und lädt Ergebnisse für die
gewählte Konzeptrevision neu. Dieselben Filter gelten für JSON-Vorschau und
XLSX-Download; ältere Projektstände bleiben separat abrufbar.


## Fachliche Entscheidungen und Vorlagenabweichungen

- Die Datei heisst `TEST_Zonen_Entkoppung.xlsm`, das Blatt `Zonen_Vorgaben`.
  Makros werden weder importiert noch ausgefuehrt. Die Beispieldaten der
  Zonendatei werden nicht als Konfiguration uebernommen: sie enthalten
  Mehrfachzuordnungen und Selbstlieferungen, die den Anforderungen widersprechen.
- Ausgabespalte C heisst in der Vorlage `Anzahl_Versionen`. Entsprechend der
  expliziten Anforderung wird dort die Teileanzahl aus Zonen J gespeichert,
  nicht die Anzahl unterschiedlicher Revisionen. Die Originalueberschrift
  bleibt fuer die Spaltenkompatibilitaet erhalten. Diese fachliche Abweichung
  sollte vor einem downstream-Systemanschluss abgestimmt werden.
- Zonen J ist die geplante Zielmenge. Der Startwert zaehlt Strukturvorkommen
  derselben ERP-Nummer und Revision, nicht die Summe von Input N. Materialmengen
  werden separat aus Input N berechnet. Leere N-Werte
  werden als 1 interpretiert; negative und nichtnumerische Mengen sind ungueltig.
- Die automatische Liste loest eine Unterbaugruppe nur dann als eingehendes
  Material auf, wenn die entsprechende zielbaugruppenspezifische Lieferbeziehung
  an diese Zone gerichtet ist. Eine Zielbaugruppe einer anderen Zone allein
  ist keine Aufloesungsgrenze. Mengen werden entlang des Pfads multipliziert.
  Je Vorkommen entsteht eine
  Materialzeile, ohne stillschweigende Aggregation. S/T/W/Y/Z enthalten
  Menge, Sachnummer, Revision, Sachnummer+Revision und Beschreibung.
- Prozessschritte, Arbeitszeiten, SAP-Arbeitsgaenge, Werkzeuge, Hilfsmittel,
  Verpackungskennzahlen und Verbrauchsmaterial liegen in Input A-S nicht
  ausreichend vor. Ihre Spalten bleiben manuell pflegbar; Excel-Formeln,
  Makros und externe Lookup-Tabellen werden nicht als Geschaeftslogik kopiert.
  SAP_AG_plausibel beginnt bei FALSCH, da eine SAP-Pruefung nicht implementiert ist.
- Eine Aenderung der gespeicherten Zonen setzt die Ausgabe zurueck. Erneutes
  Generieren ersetzt auch manuelle Prozessinformationen nach Bestaetigung.
  Ohne ausdrueckliches Bestaetigungsfeld lehnt auch der Server diese Eingriffe
  bei vorhandenen Daten mit HTTP 409 ab.
- Prozessvorlagen und ihre Revisionen sind innerhalb eines Fertigungsplanungs-
  Projekts wiederverwendbar. Es gibt derzeit keine projektuebergreifende globale
  Vorlagenbibliothek. Sequenzen muessen je Prozess eindeutig sein; fehlende
  Arbeitsplatzzuordnung oder fehlende Zeit verhindert die Freigabe, blockiert
  aber nicht die laufende Vorplanung. Auslastungen werden pro Zielbaugruppe
  ausgewiesen; ein Produktionsmix mehrerer Zielbaugruppen wird nicht automatisch
  aggregiert.

## Lebenszyklus und Konsistenz

Ein Projekt beginnt mit Revision 01 in WORKING. Freigabe erfordert eine
importierte Struktur, gueltige Zonen und eine nichtleere, gueltige Ausgabe.
RELEASED ist schreibgeschuetzt. Neue Revision kopiert den gesamten freigegebenen
Stand nach WORKING; die bisherige Revision bleibt RELEASED, bis die neue
Revision freigegeben wird. Dann wird die alte Revision OBSOLETE. Historische
Staende bleiben lesbar und exportierbar.

Jede Revision speichert einen strukturierten JSON-Snapshot in einem Oracle
CLOB. Das ist bewusst ein geschlossenes Planungsaggregat: die 36/45
Vorlagenspalten bleiben vollstaendig erhalten, ohne die produktuebergreifenden
Stammdatenmodelle um unbekannte Excel-Felder zu erweitern. Strukturzeilen und
Tabellen sind innerhalb der Revision ueber ihre Spalten und Quellzeilen
adressierbar; es gibt noch keine unternehmensweite Attribute-Suche oder
separate Datenbankabfragen ueber einzelne Zellen. Fuer grosse, projektuebergreifende
Auswertungen ist eine normalisierte, indexierte Projektion ein naechster Schritt.

Projektversion und Datenbanksperre verhindern das Ueberschreiben paralleler
Aenderungen (HTTP 409). Erstellungs-, Aenderungs- und Freigabeinformationen
werden gespeichert; der Import besitzt Dateiname und SHA-256-Nachweis.
Tabellenzeilen besitzen zusaetzlich eine interne `_rowId`, die bei Aenderungen
erhalten bleibt und nicht als Excel-Spalte exportiert wird. Damit erfordert
auch ein gleichzeitiges Entfernen und Hinzufuegen von Zeilen DELETE.
READ erlaubt Anzeige/Export, WRITE Bearbeitung, DELETE das Entfernen von
Tabellenzeilen und EXECUTE zusaetzlich zu WRITE Freigabe/Revisionierung.
Die Web-Endpunkte liegen bewusst ausserhalb `/api/**` und behalten CSRF-Schutz.
Excel-Export schreibt Texte, keine aus Eingaben ausgefuehrten Excel-Formeln.
Importlimit: 20 MB, maximal 50.000 Struktur- beziehungsweise Ausgabezeilen.

## Betrieb und Validierung

Im Entwicklungsprofil mit `ddl-auto=update` legt Hibernate die neuen Tabellen
an. In Produktion mit `ddl-auto=validate` muss ein DBA vor dem Deployment
`oracle-schema.sql` aus diesem Verzeichnis pruefen und anwenden. Es werden
keine bestehenden Tabellen oder Datensaetze veraendert.

Standardpruefung:

```powershell
mvn '-Dmaven.test.skip=false' '-Dtest=PlanningExcelServiceTest,PlanningRulesTest,ProcessPlanningRulesTest,PlanningProjectServiceTest' test
node --check src/main/resources/static/js/manufacturing-planning.js
```

Das POM deaktiviert Tests standardmaessig. In dieser Umgebung verhindert die
Maven-Zertifikatskette das Nachladen des Surefire-JUnit-Providers. Als lokale
Alternative fuehrt `scripts/Invoke-PlanningTests.ps1` die fokussierten,
selbstaendigen JUnit-Testmethoden mit dem exakten Maven-Test-Classpath aus.
Dieser Runner ersetzt nicht den regulaeren JUnit-Runner fuer Tests mit
Extensions, Parameterisierung oder Test-Fixtures. Zertifikatspruefungen werden
nicht deaktiviert.

Die Tests vergleichen alle Vorlagenspalten, importieren die 1.236 Beispielzeilen,
pruefen Nummernberichte, wiederverwendete Baugruppen, Prozesszeitvorrang,
sequenzielle Arbeitsschritte, Mehrfachressourcen, Vorlagenrevisionierung, Line
Balancing, Lieferzyklen, Materialmengen, Zonengrenzen und den Projektlebenszyklus
auf isolierten H2-Datenbanken im Oracle-Modus.
Tests mit Originaldateien werden ohne diese lokalen Dateien uebersprungen.

Lokale Browservorschau:

```powershell
./scripts/Start-PlanningPreview.ps1 -Port 8181
```

Die Vorschau ist eine Testanwendung mit Loopback-Bindung, H2 und einer festen
lokalen Testidentitaet. Sie verwendet echte Templates, CSRF und Endpunkte,
aber weder Oracle noch AD. Daten sind beim Stoppen verloren. Die Preview-Klasse
liegt ausschliesslich unter `src/test` und wird nicht ins Produktions-JAR gepackt.
Die regulaere Anwendung verwendet weiterhin ihre vorhandene Anmeldung,
Berechtigungen und Datenbank.