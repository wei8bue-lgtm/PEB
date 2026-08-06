package com.peb.search.mapper;

import com.peb.model.ebom.Part;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class PartSearchIndexMapper implements SearchIndexMapper<Part> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.PART;
    }

    @Override
    public SearchDocument toSearchDocument(Part part) {
        // Korrigiert: es gibt keinen eigenen PartController, Part läuft wie alle
        // eBOM-Typen (Machine, System, ...) über EBOMController @RequestMapping("/ebom")
        return EBOMSearchMapping.map(part, getObjectType(), "/ebom/");
    }
}
