// src/main/java/com/peb/service/AdUserDetailsService.java
package com.peb.service;

import com.peb.model.PebUser;
import com.peb.repository.PebUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@Transactional
public class AdUserDetailsService implements UserDetailsContextMapper {

    private static final Logger log =
            LoggerFactory.getLogger(AdUserDetailsService.class);

    private final PebUserRepository userRepository;

    public AdUserDetailsService(PebUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails mapUserFromContext(
            DirContextOperations ctx,
            String               username,
            Collection<? extends GrantedAuthority> adAuthorities) {

        String normalizedUsername = extractUsername(username);
        log.info("AD-Login für: '{}'", normalizedUsername);

        // ── AD-Attribute auslesen ─────────────────────────────
        String displayName = getAttr(ctx, "displayName", username);
        String email       = getAttr(ctx, "mail", "");

        // ── Whitelist-Prüfung ─────────────────────────────────
        PebUser pebUser = userRepository
                .findByUsernameIgnoreCase(normalizedUsername)
                .orElse(null);

        if (pebUser == null) {
            pebUser = new PebUser(
                    normalizedUsername, displayName, email, "ROLE_VIEWER");
            pebUser.setStatus(PebUser.Status.PENDING);
            pebUser.setCreatedBy("AUTO (AD Login)");
            userRepository.save(pebUser);
            log.warn("Benutzer '{}' als PENDING angelegt", normalizedUsername);
            throw new UsernameNotFoundException(
                    "Ihr Konto wartet auf Freischaltung.");
        }

        if (pebUser.getStatus() != PebUser.Status.ACTIVE) {
            String msg = switch (pebUser.getStatus()) {
                case PENDING  -> "Ihr Konto wartet auf Freischaltung.";
                case INACTIVE -> "Ihr Konto wurde deaktiviert.";
                default       -> "Zugriff nicht erlaubt.";
            };
            throw new UsernameNotFoundException(msg);
        }

        // ── AD-Daten synchronisieren ──────────────────────────
        pebUser.setDisplayName(displayName);
        pebUser.setEmail(email);
        pebUser.setLastLogin(LocalDateTime.now());
        userRepository.save(pebUser);

        // ── Authorities basierend auf PEB-Rolle zuweisen ──────
        List<GrantedAuthority> authorities = resolveAuthorities(pebUser.getRole());

        log.info("✅ Login OK: '{}' | Rolle: '{}' | Authorities: {}",
                normalizedUsername, pebUser.getRole(), authorities);

        return User.builder()
                .username(normalizedUsername)
                .password("")
                .authorities(authorities)
                .accountLocked(false)
                .disabled(false)
                .build();
    }

    /**
     * Mappt PEB-Rollen auf granulare Authorities.
     *
     * ROLE_ADMIN   → ROLE_ADMIN + READ + WRITE + DELETE + EXECUTE
     * ROLE_USER    → ROLE_USER  + READ + WRITE
     * ROLE_VIEWER  → ROLE_VIEWER + READ
     *
     * Die ROLE_* Einträge werden für hasRole() / @PreAuthorize("hasRole()")
     * benötigt, die granularen Rechte für hasAuthority().
     */
    private List<GrantedAuthority> resolveAuthorities(String role) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        switch (role) {
            case "ROLE_ADMIN" -> {
                // Rolle selbst
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                // Granulare Rechte (kompatibel mit alten Demo-Usern)
                authorities.add(new SimpleGrantedAuthority("READ"));
                authorities.add(new SimpleGrantedAuthority("WRITE"));
                authorities.add(new SimpleGrantedAuthority("DELETE"));
                authorities.add(new SimpleGrantedAuthority("EXECUTE"));
            }
            case "ROLE_USER" -> {
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                authorities.add(new SimpleGrantedAuthority("READ"));
                authorities.add(new SimpleGrantedAuthority("WRITE"));
            }
            case "ROLE_VIEWER" -> {
                authorities.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
                authorities.add(new SimpleGrantedAuthority("READ"));
            }
            default -> {
                // Unbekannte Rolle → nur READ als Fallback
                log.warn("Unbekannte Rolle '{}' - nur READ vergeben", role);
                authorities.add(new SimpleGrantedAuthority(role));
                authorities.add(new SimpleGrantedAuthority("READ"));
            }
        }

        return authorities;
    }

    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
        throw new UnsupportedOperationException("Not supported");
    }

    // ── Hilfsmethoden ─────────────────────────────────────────

    private String extractUsername(String username) {
        if (username == null) return "";
        if (username.contains("@"))
            return username.substring(0, username.indexOf("@"))
                          .toLowerCase().trim();
        if (username.contains("\\"))
            return username.substring(username.lastIndexOf("\\") + 1)
                          .toLowerCase().trim();
        return username.toLowerCase().trim();
    }

    private String getAttr(DirContextOperations ctx,
                            String attr, String fallback) {
        try {
            String val = ctx.getStringAttribute(attr);
            return (val != null && !val.isBlank()) ? val : fallback;
        } catch (Exception e) {
            log.debug("AD-Attribut '{}' nicht lesbar: {}", attr, e.getMessage());
            return fallback;
        }
    }
}