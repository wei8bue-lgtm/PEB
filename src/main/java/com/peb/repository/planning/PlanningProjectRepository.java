package com.peb.repository.planning;

import com.peb.model.planning.PlanningProject;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PlanningProjectRepository extends JpaRepository<PlanningProject, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from PlanningProject project where project.id = :id")
    Optional<PlanningProject> findLocked(@Param("id") String id);
}