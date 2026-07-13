package com.peb.repository;

import com.peb.model.eclass.ObjectClassification;
import com.peb.util.ObjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObjectClassificationRepository extends JpaRepository<ObjectClassification, Long> {

    Optional<ObjectClassification> findByObjectIdAndObjectType(String objectId, ObjectType objectType);

    /**
     * Alle Objekte einer bestimmten ECLASS-Klasse (für Wiederverwendungssuche)
     */
    List<ObjectClassification> findByEclassClassId(Long classId);

    /**
     * Alle Objekte eines bestimmten Typs mit einer bestimmten ECLASS-Klasse
     */
    List<ObjectClassification> findByEclassClassIdAndObjectType(Long classId, ObjectType objectType);

    /**
     * Alle Objekte mit einem bestimmten ECLASS-Code (code über JOIN)
     */
    @Query("SELECT oc FROM ObjectClassification oc " +
           "JOIN FETCH oc.eclassClass ec " +
           "WHERE ec.code = :code " +
           "ORDER BY oc.objectType, oc.objectId")
    List<ObjectClassification> findByEclassCode(@Param("code") String code);

    /**
     * Ähnliche Objekte: gleiche ECLASS-Klasse wie das Referenz-Objekt,
     * aber andere objectId
     */
    @Query("SELECT oc FROM ObjectClassification oc " +
           "JOIN FETCH oc.eclassClass ec " +
           "WHERE ec.id = :classId AND oc.objectId <> :excludeObjectId " +
           "ORDER BY oc.objectType, oc.objectId")
    List<ObjectClassification> findSimilar(@Param("classId") Long classId,
                                            @Param("excludeObjectId") String excludeObjectId);

    boolean existsByObjectIdAndObjectType(String objectId, ObjectType objectType);
}
