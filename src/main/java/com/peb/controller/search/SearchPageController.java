package com.peb.controller.search;

import com.peb.dto.search.SearchResultPageDTO;
import com.peb.service.search.SearchService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Volle Suchergebnis-Seite, erreichbar über Enter in der Navbar-Suche
 * oder den "Alle Ergebnisse anzeigen"-Link im Autocomplete-Dropdown.
 */
@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchPageController {

    private final SearchService searchService;

    @GetMapping
    public String searchPage(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "type", required = false) ObjectType objectType,
            @RequestParam(value = "status", required = false) String releaseStatus,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        model.addAttribute("objectTypes", ObjectType.values());
        model.addAttribute("selectedType", objectType);
        model.addAttribute("selectedStatus", releaseStatus);

        if (query == null || query.isBlank()) {
            model.addAttribute("result", null);
            return "search/search-results";
        }

        SearchResultPageDTO result = searchService.search(query, objectType, releaseStatus, page);
        model.addAttribute("result", result);
        return "search/search-results";
    }
}
