package com.peb.dto.cim;

import com.peb.model.cim.CimImportMode;
import lombok.Data;

@Data
public class CimImportRequestDTO {
    private CimImportMode importMode = CimImportMode.INITIAL;
    private Long configurationId;
    private String productDataName;
    private String productBomName;
    private String manufacturingBomName;
}
