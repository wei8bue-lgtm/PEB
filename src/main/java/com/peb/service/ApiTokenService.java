// src/main/java/com/peb/service/ApiTokenService.java
package com.peb.service;

import com.peb.model.PebUser;
import com.peb.repository.PebUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@Transactional
public class ApiTokenService {

    private final PebUserRepository userRepository;

    public ApiTokenService(PebUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Generiert einen sicheren API-Token für einen Benutzer.
     * Token Format: peb_<32 zufällige Bytes als Base64>
     */
    @PreAuthorize("hasRole('ADMIN')")
    public String generateToken(Long userId,
                                 int validDays,
                                 String adminUsername) {
        PebUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User nicht gefunden"));

        // Kryptographisch sicherer Zufallsgenerator
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = "peb_" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        user.setApiToken(token);
        user.setApiTokenExpires(
                LocalDateTime.now().plusDays(validDays));
        userRepository.save(user);

        return token;
    }

    /**
     * Validiert einen API-Token und gibt den User zurück.
     */
    public Optional<PebUser> validateToken(String token) {
        if (token == null || !token.startsWith("peb_")) {
            return Optional.empty();
        }

        return userRepository.findByApiToken(token)
                .filter(u -> u.getStatus() == PebUser.Status.ACTIVE)
                .filter(u -> u.getApiTokenExpires() != null
                          && u.getApiTokenExpires()
                              .isAfter(LocalDateTime.now()));
    }

    /**
     * Token widerrufen.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void revokeToken(Long userId) {
        PebUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User nicht gefunden"));
        user.setApiToken(null);
        user.setApiTokenExpires(null);
        userRepository.save(user);
    }
}