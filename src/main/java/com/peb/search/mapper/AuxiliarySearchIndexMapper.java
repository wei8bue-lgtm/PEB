package com.peb.search.mapper;

import com.peb.model.ebom.Auxiliary;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class AuxiliarySearchIndexMapper implements SearchIndexMapper<Auxiliary> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.AUXILIARY;
    }

    @Override
    public SearchDocument toSearchDocument(Auxiliary auxiliary) {
        // Route bestätigt: EBOMController @RequestMapping("/ebom")
        return EBOMSearchMapping.map(auxiliary, getObjectType(), "/ebom/");
    }
}
