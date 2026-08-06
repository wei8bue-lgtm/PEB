package com.peb.search.mapper;

import com.peb.model.ebom.Machine;
import com.peb.search.SearchDocument;
import com.peb.search.SearchIndexMapper;
import com.peb.util.ObjectType;
import org.springframework.stereotype.Component;

@Component
public class MachineSearchIndexMapper implements SearchIndexMapper<Machine> {

    @Override
    public ObjectType getObjectType() {
        return ObjectType.MACHINE;
    }

    @Override
    public SearchDocument toSearchDocument(Machine machine) {
        // Route bestätigt: EBOMController @RequestMapping("/ebom")
        return EBOMSearchMapping.map(machine, getObjectType(), "/ebom/");
    }
}
