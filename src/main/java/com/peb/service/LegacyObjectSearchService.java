package com.peb.service;

import com.peb.dto.EclassSimilarResultDTO;
import com.peb.model.cad.Design;
import com.peb.repository.DesignRepository;
import com.peb.repository.ObjectClassificationRepository;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for searching objects.
 * Supports wildcard searches across all attributes and ECLASS-based search.
 *
 * HINWEIS (umbenannt von "SearchService"): siehe LegacySearchController für
 * den Hintergrund. Die ECLASS-Suche (searchByEclassCode/searchByEclassTerm)
 * bleibt hier bewusst unverändert bestehen (siehe Absprache: erstmal nicht
 * anfassen) - die neue zentrale Suche deckt aktuell nur strukturierte
 * Attributsuche ab, keine ECLASS-Klassifizierung.
 */
@Service
@RequiredArgsConstructor
public class LegacyObjectSearchService {

    private final DesignRepository designRepository;
    private final ObjectClassificationRepository objectClassificationRepository;
    private final EclassService eclassService;

    @Transactional(readOnly = true)
    public List<Object> searchAllObjects(String searchTerm) {
        List<Object> results = new ArrayList<>();

        // Search designs
        String wildcardTerm = searchTerm.replace("*", "%");
        results.addAll(designRepository.findByNameContainingIgnoreCase(wildcardTerm));

        // Search other object types
        // ...

        return results;
    }

    @Transactional(readOnly = true)
    public List<Design> searchDesignsByAttribute(String attributeName, String value) {
        String wildcardValue = value.replace("*", "%");

        return switch (attributeName.toLowerCase()) {
            case "name"       -> designRepository.findByNameContainingIgnoreCase(wildcardValue);
            case "department" -> designRepository.findByDepartmentContainingIgnoreCase(wildcardValue);
            default           -> new ArrayList<>();
        };
    }

    /**
     * Sucht alle PEB-Objekte (Design + eBOM) mit einem bestimmten ECLASS-Code.
     */
    @Transactional(readOnly = true)
    public List<EclassSimilarResultDTO> searchByEclassCode(String eclassCode) {
        if (eclassCode == null || eclassCode.isBlank()) return List.of();
        return eclassService.findByEclassCode(eclassCode.trim());
    }

    /**
     * Sucht alle Objekte mit einer ECLASS-Klasse, die dem Suchbegriff entspricht
     * (Klassen-Name oder Code enthält den Begriff).
     */
    @Transactional(readOnly = true)
    public List<EclassSimilarResultDTO> searchByEclassTerm(String term) {
        if (term == null || term.isBlank()) return List.of();
        // Alle Klassifizierungen über den ECLASS-Suchbegriff finden
        return objectClassificationRepository.findByEclassCode(term.trim())
                .stream()
                .map(oc -> eclassService.findByEclassCode(oc.getEclassClass().getCode()))
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }
}