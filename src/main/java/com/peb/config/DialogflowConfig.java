// src/main/java/com/peb/config/DialogflowConfig.java
package com.peb.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * GCP Authentifizierung – optional.
 *
 * Wenn kein Service Account Key vorhanden ist oder
 * ai-agent-enabled=false gesetzt ist, wird NULL zurückgegeben.
 * Die App startet trotzdem normal.
 * Der KI-Button im UI wird dann deaktiviert angezeigt.
 */
@Configuration
@Slf4j
public class DialogflowConfig {

    @Value("${peb.gcp.service-account-key-path:#{null}}")
    private String serviceAccountKeyPath;

    @Value("${peb.gcp.oauth-scope:https://www.googleapis.com/auth/cloud-platform}")
    private String oauthScope;

    @Value("${peb.bmk.ai-agent-enabled:false}")
    private boolean aiAgentEnabled;

    /**
     * Erstellt GoogleCredentials – oder gibt NULL zurück wenn:
     *   - peb.bmk.ai-agent-enabled=false
     *   - Key-Pfad nicht konfiguriert
     *   - Key-File nicht vorhanden
     *   - Key-File nicht lesbar
     *
     * NULL ist kein Fehler – der Service behandelt diesen Fall.
     */
    @Bean
    public GoogleCredentials googleCredentials() {

        // ── Feature Flag Check ────────────────────────────────
        if (!aiAgentEnabled) {
            log.info("KI-Agent deaktiviert (peb.bmk.ai-agent-enabled=false). " +
                     "GoogleCredentials werden nicht geladen.");
            return null;
        }

        // ── Key-Pfad Check ────────────────────────────────────
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isBlank()) {
            log.warn("KI-Agent: Kein Service Account Key Pfad konfiguriert " +
                     "(peb.gcp.service-account-key-path). " +
                     "KI-Agent nicht verfügbar.");
            return null;
        }

        // ── File-Existenz Check ───────────────────────────────
        Path keyPath = Paths.get(serviceAccountKeyPath);
        if (!Files.exists(keyPath)) {
            log.warn("KI-Agent: Service Account Key nicht gefunden: '{}'. " +
                     "KI-Agent nicht verfügbar. " +
                     "→ Bitte Service Account erstellen und Key ablegen.",
                     serviceAccountKeyPath);
            return null;
        }

        // ── Credentials laden ─────────────────────────────────
        try (FileInputStream keyStream =
                     new FileInputStream(keyPath.toFile())) {

            GoogleCredentials credentials = ServiceAccountCredentials
                    .fromStream(keyStream)
                    .createScoped(Collections.singletonList(oauthScope));

            credentials.refreshIfExpired();

            log.info("✅ GCP Service Account geladen: {}",
                     ((ServiceAccountCredentials) credentials)
                             .getClientEmail());

            return credentials;

        } catch (IOException e) {
            log.warn("KI-Agent: Service Account Key konnte nicht gelesen " +
                     "werden: {}. KI-Agent nicht verfügbar.",
                     e.getMessage());
            return null;
        }
    }
}