package com.peb.repository.planning;

import com.peb.model.planning.PlanningRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlanningRevisionRepository extends JpaRepository<PlanningRevision, String> {
    Optional<PlanningRevision> findByProjectIdAndNumber(String projectId, int number);
    List<PlanningRevision> findByProjectIdOrderByNumberDesc(String projectId);
}