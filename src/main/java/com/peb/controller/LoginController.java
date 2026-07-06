// src/main/java/com/peb/controller/LoginController.java
package com.peb.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {

    private final AuthenticationManager     authManager;
    private final SecurityContextRepository contextRepository;

    public LoginController(AuthenticationManager authManager,
                           SecurityContextRepository contextRepository) {
        this.authManager       = authManager;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest  request,
            HttpServletResponse response) {

        try {
            // 1. AD-Authentifizierung
            Authentication authRequest = new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername().trim(),
                    loginRequest.getPassword()
            );
            Authentication authenticated = authManager.authenticate(authRequest);

            // 2. SecurityContext manuell speichern
            //    (notwendig da requireExplicitSave entfernt wurde)
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);

            // 3. Session-ID zurückgeben
            HttpSession session = request.getSession(false);
            String sessionId = (session != null) ? session.getId() : "N/A";

            Map<String, Object> result = new HashMap<>();
            result.put("success",     true);
            result.put("message",     "Login erfolgreich");
            result.put("username",    authenticated.getName());
            result.put("sessionId",   sessionId);
            result.put("authorities", authenticated.getAuthorities()
                    .stream().map(a -> a.getAuthority()).toList());

            return ResponseEntity.ok(result);

        } catch (AuthenticationException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", parseAuthError(e.getMessage())));
        }
    }

    @PostMapping("/logout-api")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Erfolgreich abgemeldet"));
    }

    private String parseAuthError(String message) {
        if (message == null) return "Ungültige Zugangsdaten.";
        if (message.contains("Freischaltung"))
            return "Ihr Konto wartet auf Freischaltung.";
        if (message.contains("deaktiviert"))
            return "Ihr Konto wurde deaktiviert.";
        return "Ungültige Zugangsdaten.";
    }

    static class LoginRequest {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String u) { this.username = u; }
        public String getPassword() { return password; }
        public void setPassword(String p) { this.password = p; }
    }
}