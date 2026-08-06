package com.peb.search;

import com.peb.util.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Zentrales, generisches Such-Dokument für ALLE PLM-Objekttypen
 * (Part, Design, Document, Material, Supplier, ...).
 *
 * Ein Dokument bildet immer die aktuellste Revision eines Objekts ab
 * (siehe SearchIndexMapper). Bei jedem create/update/delete eines
 * Fachobjekts wird das korrespondierende SearchDocument nachgezogen.
 *
 * Index-Design: EIN Index für alle Typen (statt Index pro Typ), da
 * die zentrale Suche typübergreifend sein soll ("finde alles zu X").
 * Die Filterung nach Typ erfolgt über das Feld objectType.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "peb-search-index")
public class SearchDocument {

    /**
     * Dokument-ID im Index. Format: "<OBJECT_TYPE>_<objectId>",
     * z.B. "PART_PT00000042". So bleibt die ID eindeutig, auch wenn
     * (theoretisch) IDs zwischen Typen kollidieren würden.
     */
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String objectId; // z.B. "PT00000042"

    @Field(type = FieldType.Keyword)
    private ObjectType objectType; // PART, DESIGN, DOCUMENT, ...

    @Field(type = FieldType.Keyword)
    private String objectTypeDisplayName; // "Part", "Design", ... (für UI/Facetten)

    /**
     * Primäres Suchfeld. Text-analysiert für Volltext-artiges Matching
     * auf einzelne Wörter/Präfixe im Namen.
     */
    @Field(type = FieldType.Text)
    private String name;

    /**
     * Unanalysierte Kopie von name für exaktes Matching und Sortierung
     * (z.B. alphabetische Sortierung der Ergebnisliste).
     */
    @Field(type = FieldType.Keyword, name = "name_sort")
    private String nameSort;

    @Field(type = FieldType.Keyword)
    private String revision;

    /**
     * Als String statt typisiertem Enum gespeichert: nicht alle Objekttypen
     * nutzen com.peb.util.ReleaseStatus - ProductData z.B. hat einen eigenen
     * com.peb.model.product.ManufacturingStatus (gleiche Werte WORKING/
     * RELEASED/OBSOLETE, aber andere Klasse). String ist der kleinste
     * gemeinsame Nenner über alle 14 Objekttypen hinweg.
     */
    @Field(type = FieldType.Keyword)
    private String releaseStatus;

    @Field(type = FieldType.Keyword)
    private String department;

    @Field(type = FieldType.Keyword)
    private String material;

    @Field(type = FieldType.Keyword)
    private String supplier;

    /**
     * Zusammengesetztes Volltextfeld: enthält name + objectId + material
     * + supplier + department, damit EIN Suchbegriff über mehrere
     * Attribute hinweg matchen kann (z.B. Suche nach Lieferantenname
     * findet auch Parts, die diesen Lieferanten referenzieren).
     * Wird beim Mapping befüllt, nicht direkt vom User editiert.
     */
    @Field(type = FieldType.Text)
    private String searchText;

    @Field(type = FieldType.Keyword)
    private String createdBy;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdDate;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime lastModifiedDate;

    /**
     * Relative URL zur Detailansicht, z.B. "/parts/PT00000042" oder
     * "/designs/DS00000001". Wird vom jeweiligen Mapper gesetzt, damit
     * der Controller/das Frontend nicht selbst wissen muss, wie die
     * Route pro Objekttyp aufgebaut ist.
     */
    @Field(type = FieldType.Keyword, index = false)
    private String detailUrl;
}
