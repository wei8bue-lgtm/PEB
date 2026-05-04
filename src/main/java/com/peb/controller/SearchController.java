package com.peb.controller;

import com.peb.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Search operations
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<Object>> searchAll(@RequestParam String query) {
        List<Object> results = searchService.searchAllObjects(query);
        return ResponseEntity.ok(results);
    }
}