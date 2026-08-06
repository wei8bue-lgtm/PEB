package com.peb.repository;

import com.peb.model.document.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(d.id, 3) AS long)), 0) FROM Document d")
    Long findMaxSequence();

    List<Document> findByTemplateId(String templateId);
}
