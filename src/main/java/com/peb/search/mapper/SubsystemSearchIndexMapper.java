package com.peb.search.mapper;

import com.peb.model.ebom.Subsystem;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class SubsystemSearchIndexMapper implements SearchIndexMapper<Subsystem> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.SUBSYSTEM;
    }

    @Override
    public SearchDocument toSearchDocument(Subsystem subsystem) {
        // Route bestätigt: EBOMController @RequestMapping("/ebom")
        return EBOMSearchMapping.map(subsystem, getObjectType(), "/ebom/");
    }
}
