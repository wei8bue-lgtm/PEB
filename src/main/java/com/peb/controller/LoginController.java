package com.peb.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller für programmatischen Login (VBScript / API Clients)
 *
 * WICHTIG: Authentication muss explizit in der HTTP-Session gespeichert
 *          werden, damit nachfolgende Requests als authentifiziert gelten!
 */
@RestController
@RequestMapping("/api")
public class LoginController {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder    passwordEncoder;

    // Dieser Repository speichert den SecurityContext in der HTTP-Session
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public LoginController(UserDetailsService userDetailsService,
                           PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder    = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest  request,
            HttpServletResponse response) {

        try {
            // 1. User laden
            UserDetails userDetails = userDetailsService
                    .loadUserByUsername(loginRequest.getUsername());

            // 2. Passwort prüfen
            if (!passwordEncoder.matches(loginRequest.getPassword(),
                                         userDetails.getPassword())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false,
                                     "message", "Ungültige Zugangsdaten"));
            }

            // 3. Authentication-Token erstellen
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            // 4. SecurityContext befüllen
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(auth);
            SecurityContextHolder.setContext(securityContext);

            // 5. *** KRITISCH: SecurityContext in HTTP-Session speichern! ***
            //    Ohne diesen Schritt ist der nächste Request nicht authentifiziert
            HttpSession session = request.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext
            );

            // 6. Repository-Methode zum Speichern nutzen (Spring-konform)
            securityContextRepository.saveContext(securityContext, request, response);

            // 7. Antwort zusammenbauen
            Map<String, Object> result = new HashMap<>();
            result.put("success",    true);
            result.put("message",    "Login erfolgreich");
            result.put("username",   loginRequest.getUsername());
            result.put("sessionId",  session.getId());
            result.put("authorities", userDetails.getAuthorities()
                    .stream()
                    .map(a -> a.getAuthority())
                    .toList());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false,
                                 "message", "Ungültige Zugangsdaten"));
        }
    }

    @GetMapping("/logout")
    @PostMapping("/logout-api")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("success", true,
                                        "message", "Erfolgreich abgemeldet"));
    }

    // ── Inner Class ──────────────────────────────────────────
    static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}