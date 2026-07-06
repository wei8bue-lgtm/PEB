// src/main/java/com/peb/client/DialogflowCxAgentClient.java
package com.peb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import com.peb.dto.BmkAgentTreeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP Client für den Dialogflow CX / Gemini Agent.
 * Null-safe: funktioniert auch ohne GoogleCredentials.
 */
@Component
@Slf4j
public class DialogflowCxAgentClient {

    // ── @Nullable: Spring injiziert null wenn Bean null ist ──
    @Nullable
    private final GoogleCredentials googleCredentials;

    private final ObjectMapper objectMapper;

    @Value("${peb.gcp.project-id:NOT_CONFIGURED}")
    private String projectId;

    @Value("${peb.gcp.location:europe-west3}")
    private String location;

    @Value("${peb.gcp.agent-id:NOT_CONFIGURED}")
    private String agentId;

    @Value("${peb.gcp.dialogflow-base-url:" +
           "https://europe-west3-dialogflow.googleapis.com/v3}")
    private String baseUrl;

    @Value("${peb.gcp.agent-timeout-ms:30000}")
    private int timeoutMs;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(5000))
            .build();

    // ── Konstruktor mit @Autowired(required=false) ────────────
    @Autowired
    public DialogflowCxAgentClient(
            @Nullable GoogleCredentials googleCredentials,
            ObjectMapper objectMapper) {
        this.googleCredentials = googleCredentials;
        this.objectMapper      = objectMapper;

        if (googleCredentials == null) {
            log.info("DialogflowCxAgentClient initialisiert " +
                     "(ohne Credentials – KI-Modus nicht verfügbar).");
        } else {
            log.info("DialogflowCxAgentClient initialisiert " +
                     "(mit Credentials – KI-Modus verfügbar).");
        }
    }

    /**
     * Prüft ob der Client einsatzbereit ist.
     * Wird vom Service und Controller genutzt.
     */
    public boolean isAvailable() {
        return googleCredentials != null
               && !projectId.equals("NOT_CONFIGURED")
               && !agentId.equals("NOT_CONFIGURED");
    }

    /**
     * Sendet den Strukturbaum an den Gemini Agenten.
     * Wirft IllegalStateException wenn Client nicht verfügbar.
     */
    public Map<String, String> generateBmkMap(
            BmkAgentTreeDTO treeDto,
            String          username) throws IOException {

        // Sicherheits-Check
        if (!isAvailable()) {
            throw new IllegalStateException(
                "KI-Agent nicht verfügbar. " +
                "Mögliche Ursachen: " +
                "(1) Service Account Key fehlt, " +
                "(2) GCP Project ID nicht konfiguriert, " +
                "(3) peb.bmk.ai-agent-enabled=false");
        }

        String sessionId = "peb-bmk-" + UUID.randomUUID();
        String endpoint  = buildEndpointUrl(sessionId);
        String body      = buildRequestBody(treeDto, username);

        log.info("Sende {} Relationen an Gemini Agent. Session: {}",
                 treeDto.getRelationCount(), sessionId);

        String bearerToken = getAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type",  "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + bearerToken)
                .header("X-Goog-User-Project", projectId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Agent Request unterbrochen", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException(
                "Gemini Agent HTTP " + response.statusCode() +
                ": " + truncate(response.body()));
        }

        return parseAgentResponse(response.body());
    }

    // ── Private Helpers (identisch zur vorherigen Version) ────

    private String buildEndpointUrl(String sessionId) {
        return String.format(
            "%s/projects/%s/locations/%s/agents/%s/sessions/%s:detectIntent",
            baseUrl, projectId, location, agentId, sessionId);
    }

    private String buildRequestBody(BmkAgentTreeDTO treeDto, String username)
            throws IOException {

        String treeJson = objectMapper.writeValueAsString(treeDto);

        String queryText = String.format(
            "Generiere die vollständigen BMK für alle Verbauungspositionen " +
            "im folgenden PLM-Strukturbaum nach Bosch N54 D6 / DIN EN 81346-2. " +
            "Baum: %s. " +
            "Antworte NUR mit JSON: {\"bmkMap\": {\"<relationId>\": \"<bmk>\"}}",
            treeJson);

        ObjectNode root       = objectMapper.createObjectNode();
        ObjectNode queryInput = objectMapper.createObjectNode();
        ObjectNode text       = objectMapper.createObjectNode();

        text.put("text",         queryText);
        text.put("languageCode", "de");
        queryInput.set("text",   text);
        root.set("queryInput",   queryInput);

        return objectMapper.writeValueAsString(root);
    }

    private Map<String, String> parseAgentResponse(String body)
            throws IOException {

        JsonNode root          = objectMapper.readTree(body);
        JsonNode messages      = root.path("queryResult")
                                     .path("responseMessages");

        String agentText = null;
        for (JsonNode msg : messages) {
            JsonNode textArr = msg.path("text").path("text");
            if (textArr.isArray() && !textArr.isEmpty()) {
                agentText = textArr.get(0).asText();
                break;
            }
        }

        if (agentText == null || agentText.isBlank()) {
            throw new IOException("Agent-Antwort enthält keinen Text.");
        }

        return extractBmkMap(agentText);
    }

    private Map<String, String> extractBmkMap(String text)
            throws IOException {

        String cleaned = text.trim()
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("^```\\s*",     "")
                .replaceFirst("```\\s*$",     "")
             .trim();

     JsonNode parsed  = objectMapper.readTree(cleaned);
     Map<String, String> result = new HashMap<>();

     JsonNode mapNode = parsed.has("bmkMap")
             ? parsed.get("bmkMap")
             : parsed;

     mapNode.fields().forEachRemaining(e ->
             result.put(e.getKey(), e.getValue().asText()));

     log.info("Agent hat {} BMKs generiert.", result.size());
     return result;
 }

 private String getAccessToken() throws IOException {
     googleCredentials.refreshIfExpired();
     return googleCredentials.getAccessToken().getTokenValue();
 }

 private String truncate(String s) {
     return s == null ? "null"
          : s.length() > 300 ? s.substring(0, 300) + "..." : s;
 }
}