package com.peb.search.mapper;

import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class SystemSearchIndexMapper implements SearchIndexMapper<com.peb.model.ebom.System> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.SYSTEM;
    }

    @Override
    public SearchDocument toSearchDocument(com.peb.model.ebom.System system) {
        // Route bestätigt: EBOMController @RequestMapping("/ebom")
        return EBOMSearchMapping.map(system, getObjectType(), "/ebom/");
    }
}
