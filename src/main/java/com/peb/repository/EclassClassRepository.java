package com.peb.repository;

import com.peb.model.eclass.EclassClass;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EclassClassRepository extends JpaRepository<EclassClass, Long> {

    Optional<EclassClass> findByCodeAndVersion(String code, String version);

    List<EclassClass> findAllByVersion(String version);

    Optional<EclassClass> findByCode(String code);

    List<EclassClass> findByClassLevelOrderByCode(int classLevel);

    List<EclassClass> findBySegmentAndClassLevelOrderByCode(String segment, int classLevel);

    List<EclassClass> findByMainGroupAndClassLevelOrderByCode(String mainGroup, int classLevel);

    List<EclassClass> findByGroupCodeAndClassLevelOrderByCode(String groupCode, int classLevel);

    @Query("SELECT c FROM EclassClass c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.nameEn) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "c.code LIKE CONCAT('%', :term, '%') OR " +
           "LOWER(c.keywords) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "ORDER BY c.classLevel, c.code")
    List<EclassClass> searchByTerm(@Param("term") String term);

    @Query("SELECT c FROM EclassClass c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.nameEn) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "c.code LIKE CONCAT('%', :term, '%') OR " +
           "LOWER(c.keywords) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "ORDER BY c.classLevel, c.code")
    List<EclassClass> searchByTermTop20(@Param("term") String term,
                                         org.springframework.data.domain.Pageable pageable);

    @Query("SELECT DISTINCT c.version FROM EclassClass c ORDER BY c.version DESC")
    List<String> findDistinctVersions();

    @Query("SELECT COUNT(c) FROM EclassClass c WHERE c.classLevel = 4")
    long countCommodities();

    /**
     * Korrigiert segment, main_group, group_code und class_level aus dem code-Feld.
     * Notwendig nach einem Import mit alter deriveHierarchyFromCode()-Logik.
     * Code-Format: AA-BB-CC-DD (11 Zeichen)
     */
    @Modifying
    @Query(value = """
        UPDATE eclass_classes SET
            segment    = SUBSTR(code, 1, 2),
            main_group = SUBSTR(code, 1, 5),
            group_code = SUBSTR(code, 1, 8),
            class_level = CASE
                WHEN SUBSTR(code, 4, 2) = '00' AND SUBSTR(code, 7, 2) = '00' AND SUBSTR(code, 10, 2) = '00' THEN 1
                WHEN SUBSTR(code, 7, 2) = '00' AND SUBSTR(code, 10, 2) = '00' THEN 2
                WHEN SUBSTR(code, 10, 2) = '00' THEN 3
                ELSE 4
            END
        WHERE LENGTH(code) = 11
        """, nativeQuery = true)
    int fixHierarchyFields();
}
