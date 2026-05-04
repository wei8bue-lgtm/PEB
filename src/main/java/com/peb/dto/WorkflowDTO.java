package com.peb.dto;

import com.peb.model.workflow.WorkflowType;
import lombok.Data;

@Data
public class WorkflowDTO {
    private WorkflowType workflowType;
    private String objectId;
    private Long revisionId;
}