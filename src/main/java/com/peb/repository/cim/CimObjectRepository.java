package com.peb.repository.cim;

import com.peb.model.cim.CimObject;
import com.peb.util.ObjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CimObjectRepository extends JpaRepository<CimObject, String> {

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(c.id, 3) AS long)), 0) FROM CimObject c")
    Long findMaxSequence();

    Optional<CimObject> findBySourceMaeObjectId(String sourceMaeObjectId);

    List<CimObject> findAllByOrderByIdAsc();

    List<CimObject> findBySourceMaeObjectTypeOrderByIdAsc(ObjectType sourceMaeObjectType);
}
