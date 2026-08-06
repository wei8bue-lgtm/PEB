package com.peb.search;

import com.peb.util.ObjectType;

/**
 * Ein Mapper pro Objekttyp, der das Fachobjekt (z.B. Part) in ein
 * generisches {@link SearchDocument} übersetzt.
 *
 * WARUM dieses Pattern:
 * Wir haben ~15 unterschiedliche Objekttypen (Part, Design, Document,
 * Material, ...), die alle unterschiedliche Java-Klassen sind, aber
 * gemeinsam in EINEM Index durchsuchbar sein sollen. Statt eines riesigen
 * if/else-Blocks im SearchIndexService registriert sich jeder Mapper als
 * Spring-Bean für "seinen" ObjectType. Der SearchIndexService sammelt beim
 * Start alle SearchIndexMapper-Beans und wählt anhand getObjectType() den
 * richtigen Mapper aus.
 *
 * NEUEN OBJEKTTYP ANBINDEN → 2 Schritte:
 *   1. Diese Schnittstelle implementieren (siehe PartSearchIndexMapper
 *      als Vorlage) und mit @Component registrieren.
 *   2. Im jeweiligen Fach-Service (z.B. MaterialService) nach create/
 *      update/delete den SearchIndexService aufrufen (siehe docs/
 *      SEARCH_INTEGRATION.md für die genauen Call-Sites pro Service).
 *
 * @param <T> Der Fachobjekt-Typ (z.B. Part, Design, Document)
 */
public interface SearchIndexMapper<T> {

    /**
     * Für welchen ObjectType ist dieser Mapper zuständig.
     */
    ObjectType getObjectType();

    /**
     * Übersetzt das Fachobjekt (inkl. seiner aktuellsten Revision) in
     * ein SearchDocument. Gibt null zurück, wenn das Objekt (noch) keine
     * indexierbare Revision hat (z.B. neu angelegtes Objekt ohne Revision) -
     * der SearchIndexService überspringt es dann.
     */
    SearchDocument toSearchDocument(T entity);
}
