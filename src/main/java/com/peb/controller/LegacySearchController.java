package com.peb.controller;

import com.peb.service.LegacyObjectSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Search operations
 *
 * HINWEIS (umbenannt von "SearchController"): Es gibt jetzt eine neue,
 * Elasticsearch-basierte zentrale Suche (siehe com.peb.controller.search.*).
 * Dieser Endpunkt (GET /api/search?query=...) bleibt unverändert bestehen,
 * da er laut Rückmeldung noch aktiv von Frontend/Templates genutzt wird -
 * nur der Klassenname wurde entschärft, um Verwechslungen mit der neuen
 * Lösung zu vermeiden. Mittelfristige Empfehlung: auf die neue Suche
 * migrieren und diese Klasse dann entfernen.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class LegacySearchController {

    private final LegacyObjectSearchService searchService;

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<Object>> searchAll(@RequestParam String query) {
        List<Object> results = searchService.searchAllObjects(query);
        return ResponseEntity.ok(results);
    }
}