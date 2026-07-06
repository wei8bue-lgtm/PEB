// src/main/java/com/peb/dto/RefDesUpdateDTO.java
package com.peb.dto;

import lombok.Data;

/**
 * Minimales Payload für den PATCH /api/bmk/relations/{id}/refdes Endpoint.
 * Enthält ausschließlich das Reference Designator Freitextfeld.
 */
@Data
public class RefDesUpdateDTO {

    /**
     * User-provided partial BMK segment, e.g. "=102", "+M", "-B301A".
     * Darf null oder leer sein (= Wert löschen).
     */
    private String referenceDesignator;
}