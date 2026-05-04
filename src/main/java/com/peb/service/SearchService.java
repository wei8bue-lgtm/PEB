package com.peb.service;

import com.peb.model.cad.Design;
import com.peb.repository.DesignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for searching objects
 * Supports wildcard searches across all attributes
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final DesignRepository designRepository;
    // Inject other repositories

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
            case "name" -> designRepository.findByNameContainingIgnoreCase(wildcardValue);
            case "department" -> designRepository.findByDepartmentContainingIgnoreCase(wildcardValue);
            default -> new ArrayList<>();
        };
    }
}