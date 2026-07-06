// src/main/java/com/peb/service/UserManagementService.java
package com.peb.service;

import com.peb.model.PebUser;
import com.peb.repository.PebUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserManagementService {

    private static final Logger log =
            LoggerFactory.getLogger(UserManagementService.class);

    private final PebUserRepository userRepository;

    public UserManagementService(PebUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── Abfragen ─────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    public List<PebUser> getAllUsers() {
        return userRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<PebUser> getPendingUsers() {
        return userRepository.findByStatus(PebUser.Status.PENDING);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<PebUser> searchUsers(String query) {
        if (query == null || query.isBlank()) return getAllUsers();
        return userRepository.search(query.trim());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<PebUser> findById(Long id) {
        return userRepository.findById(id);
    }

    // ── Benutzer anlegen (manuell, vor erstem Login) ─────────

    @PreAuthorize("hasRole('ADMIN')")
    public PebUser createUser(String username, String displayName,
                               String email, String role,
                               String createdBy) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException(
                    "Benutzer '" + username + "' existiert bereits.");
        }

        PebUser user = new PebUser(
                username.toLowerCase().trim(),
                displayName, email, role);
        user.setStatus(PebUser.Status.ACTIVE); // Admin legt direkt aktiv an
        user.setCreatedBy(createdBy);
        user.setCreatedAt(LocalDateTime.now());

        PebUser saved = userRepository.save(user);
        log.info("Benutzer '{}' manuell angelegt von '{}'", username, createdBy);
        return saved;
    }

    // ── Status-Verwaltung ────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    public PebUser activateUser(Long id, String adminUsername) {
        PebUser user = getOrThrow(id);
        user.setStatus(PebUser.Status.ACTIVE);
        log.info("Benutzer '{}' aktiviert von '{}'",
                 user.getUsername(), adminUsername);
        return userRepository.save(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PebUser deactivateUser(Long id, String adminUsername) {
        PebUser user = getOrThrow(id);
        user.setStatus(PebUser.Status.INACTIVE);
        log.info("Benutzer '{}' deaktiviert von '{}'",
                 user.getUsername(), adminUsername);
        return userRepository.save(user);
    }

    // ── Rolle ändern ─────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    public PebUser updateRole(Long id, String newRole, String adminUsername) {
        PebUser user = getOrThrow(id);

        // Eigene Rolle schützen (Admin kann sich nicht selbst degradieren)
        if (user.getUsername().equalsIgnoreCase(adminUsername)
                && !newRole.equals("ROLE_ADMIN")) {
            throw new IllegalArgumentException(
                    "Sie können Ihre eigene Admin-Rolle nicht entfernen.");
        }

        String oldRole = user.getRole();
        user.setRole(newRole);
        log.info("Rolle von '{}' geändert: {} → {} (durch '{}')",
                 user.getUsername(), oldRole, newRole, adminUsername);
        return userRepository.save(user);
    }

    // ── Notizen / Update ─────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    public PebUser updateUser(Long id, String displayName,
                               String email, String role,
                               PebUser.Status status, String notes) {
        PebUser user = getOrThrow(id);
        if (displayName != null) user.setDisplayName(displayName);
        if (email       != null) user.setEmail(email);
        if (role        != null) user.setRole(role);
        if (status      != null) user.setStatus(status);
        if (notes       != null) user.setNotes(notes);
        return userRepository.save(user);
    }

    // ── Löschen ──────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long id, String adminUsername) {
        PebUser user = getOrThrow(id);
        if (user.getUsername().equalsIgnoreCase(adminUsername)) {
            throw new IllegalArgumentException(
                    "Sie können Ihr eigenes Konto nicht löschen.");
        }
        userRepository.delete(user);
        log.info("Benutzer '{}' gelöscht von '{}'",
                 user.getUsername(), adminUsername);
    }

    // ── Statistiken ──────────────────────────────────────────

    public long countByStatus(PebUser.Status status) {
        return userRepository.findByStatus(status).size();
    }

    // ── Privat ──────────────────────────────────────────────
    private PebUser getOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Benutzer mit ID " + id + " nicht gefunden."));
    }
}