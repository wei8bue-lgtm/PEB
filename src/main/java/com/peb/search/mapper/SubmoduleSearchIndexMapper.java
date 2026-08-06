package com.peb.search.mapper;

import com.peb.model.ebom.Submodule;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class SubmoduleSearchIndexMapper implements SearchIndexMapper<Submodule> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.SUBMODULE;
    }

    @Override
    public SearchDocument toSearchDocument(Submodule submodule) {
        // Route bestätigt: EBOMController @RequestMapping("/ebom")
        return EBOMSearchMapping.map(submodule, getObjectType(), "/ebom/");
    }
}
