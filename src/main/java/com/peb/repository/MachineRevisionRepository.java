package com.peb.repository;

import com.peb.model.ebom.MachineRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MachineRevisionRepository extends JpaRepository<MachineRevision, Long> {
    List<MachineRevision> findByMachineIdOrderByRevisionDesc(String machineId);
    Optional<MachineRevision> findFirstByMachineIdOrderByRevisionDesc(String machineId);
    Optional<MachineRevision> findByMachineIdAndReleaseStatus(String machineId, ReleaseStatus status);
    List<MachineRevision> findByMachineIdAndReleaseStatusNot(String machineId, ReleaseStatus status);
}