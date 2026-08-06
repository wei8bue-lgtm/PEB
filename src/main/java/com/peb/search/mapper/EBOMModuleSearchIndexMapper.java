package com.peb.search.mapper;

import com.peb.model.ebom.EBOMModule;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class EBOMModuleSearchIndexMapper implements SearchIndexMapper<EBOMModule> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.MODULE;
    }

    @Override
    public SearchDocument toSearchDocument(EBOMModule module) {
        // Route bestätigt: EBOMController @RequestMapping("/ebom")
        return EBOMSearchMapping.map(module, getObjectType(), "/ebom/");
    }
}
