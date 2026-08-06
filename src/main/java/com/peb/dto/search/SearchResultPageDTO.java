package com.peb.dto.search;

import com.peb.search.SearchDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultPageDTO {
    private String query;
    private List<SearchDocument> results;
    private long totalHits;
    private int page;       // 0-basiert
    private int pageSize;
    private int totalPages;

    /**
     * true, wenn die Suche gerade nicht erreichbar war (z.B. Elasticsearch
     * down) - dann ist "results" leer, aber das bedeutet NICHT "0 Treffer
     * gefunden", sondern "Suche konnte gerade nicht ausgeführt werden".
     * Die UI muss das unterschiedlich anzeigen, siehe search-results.html.
     */
    private boolean searchUnavailable;

    public static SearchResultPageDTO unavailable(String query, int page, int pageSize) {
        return new SearchResultPageDTO(query, List.of(), 0, page, pageSize, 0, true);
    }
}