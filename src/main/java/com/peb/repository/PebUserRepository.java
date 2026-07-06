// src/main/java/com/peb/repository/PebUserRepository.java
package com.peb.repository;

import com.peb.model.PebUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PebUserRepository extends JpaRepository<PebUser, Long> {

    Optional<PebUser> findByUsernameIgnoreCase(String username);

    List<PebUser> findByStatus(PebUser.Status status);

    List<PebUser> findByRoleOrderByUsernameAsc(String role);

    boolean existsByUsernameIgnoreCase(String username);

    // ── NEU: API-Token Suche ──────────────────────────────────
    Optional<PebUser> findByApiToken(String apiToken);

    @Query("SELECT u FROM PebUser u WHERE " +
           "LOWER(u.username)    LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.email)       LIKE LOWER(CONCAT('%', :q, '%'))")
    List<PebUser> search(@Param("q") String query);
}