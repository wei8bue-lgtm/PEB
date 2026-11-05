# Fertigungsplanung in SEP4Eng

## Zweck

Mit der Fertigungsplanung werden Produktstruktur, Zonen, Arbeitsplätze, Prozessschritte und Material-/Ressourcenbedarf in einem gemeinsamen, versionierten Planungsprojekt gepflegt. Die Anwendung unterstützt mehrere Planer. Bei zwischenzeitlichen Änderungen durch andere Nutzer wird ein veralteter Speicherversuch abgewiesen; laden Sie dann den aktuellen Stand neu.

## Planungsablauf

1. **Projekt anlegen**  
   Öffnen Sie in SEP4Eng den Bereich **Fertigungsplanung** und legen Sie ein Projekt mit einem aussagekräftigen Namen an.

2. **Produktstruktur importieren**  
   Wechseln Sie auf **Struktur** und importieren Sie eine `.xlsx`-Datei. Die erste Tabelle muss die Produktstruktur enthalten. ERP-Nummer und Revision dienen gemeinsam zur Identifikation eines Artikels. Bosch-SAP-Nummer und SAP-Revision werden, sofern in der Datei vorhanden, separat eingelesen. Prüfen Sie nach dem Import die Struktur und den Artikelstamm auf fehlende oder widersprüchliche Angaben.

   Ein Neuimport in derselben Arbeitsversion ersetzt Struktur und Artikelstamm und kann abhängige Planungsdaten zurücksetzen. Wenn ein bestehender Stand erhalten bleiben soll, aktivieren Sie **Als neue Musterstücklisten-Version**. Die bisherige Revision bleibt dann unverändert erhalten.

3. **Zonen und Lieferungen pflegen**  
   Ordnen Sie im Tab **Zonen** jeder Zielbaugruppe (ERP-Nummer plus Revision) eine Zone zu. Prüfen Sie Zielmenge und Takt. Wenn eine Baugruppe aus einer anderen Zone geliefert wird, tragen Sie die Lieferbeziehung ein. Selbstlieferungen und Lieferzyklen sind nicht zulässig.

4. **Arbeitsplätze festlegen**  
   Erfassen Sie Arbeitsplätze und ordnen Sie sie einer Zone zu. Eine optionale Arbeitsplatz-Taktzeit kann für die spätere Auslastungsanzeige verwendet werden; andernfalls gilt der Zonentakt.

5. **Fertigungsliste erzeugen und prüfen**  
   Die Schaltfläche **Fertigungsliste erzeugen** füllt die Liste aus Struktur und Zonenzuordnung vor. Bei einer Zone mit nur einer Zielbaugruppe wird diese beim Anlegen einer Zeile automatisch vorausgewählt. Prüfen und ergänzen Sie die erzeugten Positionen und pflegen Sie die verbleibenden Felder manuell. Das erneute Erzeugen kann manuelle Angaben ersetzen und erfordert deshalb eine ausdrückliche Bestätigung.

6. **Prozesse und Tätigkeiten planen**  
   Im Tab **Prozesse** legen Sie Schrittgruppen und Prozessvorlagen an, ordnen Prozesse einer Zielbaugruppe zu und erfassen die einzelnen Arbeitsschritte. Pflegen Sie Reihenfolge, Ausführungszahl je Stück, Schätzzeit oder Zeitstudie, Arbeitsplatz und benötigte Ressourcen. Zeiten werden in Sekunden je Ausführung erfasst; eine Zeitstudie hat Vorrang vor der Schätzung. Die Istzeit ist ein separater Messwert.

   Über **Tätigkeiten kopieren** können Sie den Prozessplan einer Zielbaugruppe auf eine andere übertragen. Die Quelle bleibt bestehen. Vorhandene Tätigkeiten am Ziel werden nur nach Bestätigung ersetzt. Anschließend können Sie die Zielverwendung lokal anpassen. Im Editor lassen sich Arbeitsschritte mit den Auf-/Ab-Schaltflächen umordnen.

## Engpässe und Plausibilität

Das Prozess-Line-Balancing zeigt Arbeitsinhalt, verfügbaren Takt und Auslastung je Arbeitsplatz und Zielbaugruppe. Eine Auslastung über 100 % weist auf einen möglichen Engpass hin. Verschieben Sie Arbeitsschritte bei Bedarf manuell und prüfen Sie die Auslastung erneut. Zeiten oder Arbeitsverteilungen werden nicht automatisch optimiert.

Prüfhinweise machen unter anderem fehlende Arbeitsplätze, Zeitansätze oder Artikelzuordnungen sichtbar. Offene Pflichtangaben können eine Freigabe verhindern. Klären Sie außerdem gemeldete unvollständige Artikelstämme und abweichende Unterstrukturen wiederverwendeter Baugruppen.

## Reports und Exporte

Im Tab **Validierung & Reports** können Sie unter anderem:

- den Materialbedarf aus der Stückliste mit dem geplanten Materialverbrauch vergleichen,
- die Zonenversorgung und den Bedarf pro Stunde prüfen,
- ERP-/SAP-Nummernzuordnungen plausibilisieren,
- Material-, Werkzeug-, Hilfsmittel- und Gesamtressourcen filtern.

Wählen Sie den gewünschten Report und bei Bedarf Zone, Arbeitsplatz, Zielbaugruppe oder Status. **Report erzeugen** zeigt die Vorschau; **XLSX herunterladen** exportiert denselben gefilterten Stand. Der allgemeine Excel-Export enthält zusätzlich Struktur, Zonen, Fertigungsliste, Nummernzuordnung, Arbeitsplätze, Prozessplan und Line-Balancing.

## Versionen und Freigabe

Der Revisionswähler öffnet ältere Planungsstände schreibgeschützt. Über **Neue Planungsversion** können Sie einen Arbeitsstand aus dem aktuellen Snapshot anlegen; Änderungen an der neuen Version verändern den Vorgänger nicht. Im Struktur-Tab können zwei Versionen verglichen werden. Der Vergleich hebt hinzugekommene, entfallene und geänderte Positionen hervor.

Vor der Freigabe prüfen Sie Struktur, Zonen, Fertigungsliste, Stammdaten und Prozesshinweise. Eine Freigabe macht den Stand schreibgeschützt. Danach kann eine neue Arbeitsversion erstellt werden. Für freigegebene Stände kann eine **Arbeitsfolgekarte** als XLSX ausgegeben werden; sie enthält die geplanten Arbeitsschritte und Dokumentreferenzen.

## Dokumente und wichtige Hinweise

Dokumente, Zeichnungen, Festlegungen, Notizen und externe Prozessvorgaben können an einem Arbeitsschritt referenziert werden. Externe Vorgaben benötigen eine vollständige `http://`- oder `https://`-Adresse. Die Anwendung speichert Verweise, aber keine Dokumentdateien; stellen Sie daher sicher, dass die referenzierten Speicherorte für die vorgesehenen Nutzer erreichbar bleiben.

Bild-/Thumbnail-Vorschauen und die automatische räumliche Positionierung neuer Bauteile sind derzeit nicht Bestandteil dieses Planungsablaufs. Die automatische Fertigungsliste ist eine Ausgangsbasis und ersetzt nicht die fachliche Prüfung durch die Arbeitsplanung.
