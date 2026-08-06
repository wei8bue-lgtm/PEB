package com.peb.dto.search;

import com.peb.util.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Schlankes DTO für die Navbar-Autocomplete-Liste. Bewusst ohne
 * Audit-Felder (createdBy/createdDate etc.), da das Dropdown nur
 * Name, Typ und Sprungziel braucht.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestionDTO {
    private String objectId;
    private ObjectType objectType;
    private String objectTypeDisplayName;
    private String name;
    private String revision;
    private String releaseStatus;
    private String detailUrl;
}
