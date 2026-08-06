package com.peb.controller.search;

import com.peb.dto.search.SearchSuggestionDTO;
import com.peb.service.search.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JSON-Endpunkt für die Autocomplete-Vorschläge in der Navbar.
 * Vom Frontend-JS per fetch() aufgerufen, siehe
 * templates/fragments/navbar-search.html.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchApiController {

    private final SearchService searchService;

    @GetMapping("/suggest")
    public ResponseEntity<List<SearchSuggestionDTO>> suggest(@RequestParam("q") String query) {
        return ResponseEntity.ok(searchService.suggest(query));
    }
}
