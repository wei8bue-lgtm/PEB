package com.peb.repository;

import com.peb.model.document.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, String> {

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(d.id, 3) AS long)), 0) FROM DocumentTemplate d")
    Long findMaxSequence();
}
