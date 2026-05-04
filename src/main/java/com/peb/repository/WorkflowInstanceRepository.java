package com.peb.repository;

import com.peb.model.workflow.WorkflowInstance;
import com.peb.model.workflow.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {

    List<WorkflowInstance> findByObjectId(String objectId);

    List<WorkflowInstance> findByStatus(WorkflowStatus status);

    List<WorkflowInstance> findByCreatedBy(String username);
}