package com.peb.repository;

import com.peb.model.IdSequenceState;
import com.peb.util.ObjectType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdSequenceStateRepository extends JpaRepository<IdSequenceState, ObjectType> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IdSequenceState s where s.objectType = :objectType")
    Optional<IdSequenceState> findForUpdate(@Param("objectType") ObjectType objectType);
}
