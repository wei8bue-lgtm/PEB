package com.peb.dto;

import lombok.Data;

@Data
public class RevisionDTO {
    private String objectId;
    private String revision;
    private String releaseStatus;
}