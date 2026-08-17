package com.peb.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peb.client.DialogflowCxAgentClient;
import com.peb.dto.chat.ChatResponseDTO;
import com.peb.dto.chat.ChatResultItemDTO;
import com.peb.dto.search.SearchResultPageDTO;
import com.peb.search.SearchDocument;
import com.peb.service.search.SearchService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PepperoniChatService {

    public static final String BOT_NAME = "PEPperoni";
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("\\b([A-Za-z]{2}\\d{6,10})\\b");
    private static final Pattern ALL_REQUEST_PATTERN = Pattern.compile("(?i)\\b(alle|all|list|liste|zeige\\s+alle|zeig\\s+alle)\\b");

    private final SearchService searchService;
    private final DialogflowCxAgentClient dialogflowClient;
    private final ObjectMapper objectMapper;

    @Value("${peb.chat.llm-enabled:false}")
    private boolean llmEnabled;

    @Value("${peb.chat.llm-fallback-enabled:true}")
    private boolean llmFallbackEnabled;

    @Value("${peb.chat.llm-language:de}")
    private String llmLanguage;

    @Value("${peb.chat.result-limit:5}")
    private int chatResultLimit;

    public ChatResponseDTO query(String userQuery, ObjectType forcedType, String forcedStatus, int page) {
        if (userQuery == null || userQuery.isBlank()) {
            return ChatResponseDTO.builder()
                    .botName(BOT_NAME)
                    .userQuery("")
                    .normalizedQuery("")
                    .responseText("Bitte gib einen Suchbegriff an, z.B. Name, ID oder Objekttyp.")
                    .results(List.of())
                    .appliedType(forcedType)
                    .appliedStatus(normalizeStatus(forcedStatus).orElse(null))
                    .page(0)
                    .totalPages(0)
                    .totalHits(0)
                    .searchUnavailable(false)
                    .build();
        }

        String normalizedInput = userQuery.trim();
                SearchIntent intent = resolveIntent(normalizedInput, forcedType, forcedStatus);

                if (intent.needsClarification()) {
                    return ChatResponseDTO.builder()
                        .botName(BOT_NAME)
                        .userQuery(userQuery)
                        .normalizedQuery(intent.query())
                        .responseText(intent.clarificationQuestion())
                        .appliedType(intent.objectType())
                        .appliedStatus(intent.releaseStatus())
                        .totalHits(0)
                        .page(0)
                        .totalPages(0)
                        .searchUnavailable(false)
                        .results(List.of())
                        .build();
                }

        String queryText = intent.query();
        ObjectType appliedType = intent.objectType();
        String appliedStatus = intent.releaseStatus();

        SearchResultPageDTO resultPage = searchService.search(queryText, appliedType, appliedStatus, Math.max(page, 0));

        List<ChatResultItemDTO> items = resultPage.getResults().stream()
            .limit(Math.max(chatResultLimit, 1))
                .map(this::toChatResult)
                .toList();

        String responseText = buildResponseText(queryText, resultPage, appliedType, appliedStatus, intent.exactIdMode(), intent.allMode(), intent.source());

        return ChatResponseDTO.builder()
                .botName(BOT_NAME)
                .userQuery(userQuery)
                .normalizedQuery(queryText)
                .responseText(responseText)
                .appliedType(appliedType)
                .appliedStatus(appliedStatus)
                .totalHits(resultPage.getTotalHits())
                .page(resultPage.getPage())
                .totalPages(resultPage.getTotalPages())
                .searchUnavailable(resultPage.isSearchUnavailable())
                .results(items)
                .build();
    }

    private SearchIntent resolveIntent(String userInput, ObjectType forcedType, String forcedStatus) {
        if (llmEnabled) {
            Optional<SearchIntent> llmIntent = parseIntentViaLlm(userInput, forcedType, forcedStatus);
            if (llmIntent.isPresent()) {
                return llmIntent.get();
            }
            if (!llmFallbackEnabled) {
                return new SearchIntent(
                        "",
                        forcedType,
                        normalizeStatus(forcedStatus).orElse(null),
                        false,
                        false,
                        "LLM_UNAVAILABLE",
                        true,
                        "Ich bin PEPperoni. Ich konnte die Anfrage gerade nicht sicher interpretieren. Bitte formuliere die Suche konkreter, z.B. 'alle Designs mit Status released'."
                );
            }
        }
        return parseIntentRuleBased(userInput, forcedType, forcedStatus);
    }

    private ChatResultItemDTO toChatResult(SearchDocument doc) {
        return new ChatResultItemDTO(
                doc.getObjectId(),
                doc.getName(),
                doc.getObjectType() != null ? doc.getObjectType().name() : "",
                doc.getObjectTypeDisplayName(),
                doc.getRevision(),
                doc.getReleaseStatus(),
                doc.getDetailUrl()
        );
    }

    private Optional<String> detectObjectId(String text) {
        Matcher matcher = OBJECT_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    private Optional<ObjectType> detectObjectType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);

        if (lower.contains("designobjekt") || lower.contains("designobjekte") || lower.contains("designs")) {
            return Optional.of(ObjectType.DESIGN);
        }
        if (lower.contains("zeichnung") || lower.contains("zeichnungen") || lower.contains("drawings")) {
            return Optional.of(ObjectType.DRAWING);
        }
        if (lower.contains("part") || lower.contains("teile") || lower.contains("teil")) {
            return Optional.of(ObjectType.PART);
        }
        if (lower.contains("material") || lower.contains("materialien")) {
            return Optional.of(ObjectType.MATERIAL);
        }
        if (lower.contains("lieferant") || lower.contains("supplier") || lower.contains("suppliers")) {
            return Optional.of(ObjectType.SUPPLIER);
        }

        for (ObjectType type : ObjectType.values()) {
            if (lower.contains(type.name().toLowerCase(Locale.ROOT))
                    || lower.contains(type.getDisplayName().toLowerCase(Locale.ROOT))
                    || lower.contains(type.getPrefix().toLowerCase(Locale.ROOT))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private Optional<String> detectStatus(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("released") || lower.contains("freigegeben")) {
            return Optional.of("RELEASED");
        }
        if (lower.contains("working") || lower.contains("bearbeitung")) {
            return Optional.of("WORKING");
        }
        if (lower.contains("obsolete") || lower.contains("veraltet")) {
            return Optional.of("OBSOLETE");
        }
        return Optional.empty();
    }

    private Optional<String> normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }

        String raw = status.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        if ("released".equals(lower)
                || "release".equals(lower)
                || lower.contains("freigegeben")) {
            return Optional.of("RELEASED");
        }
        if ("working".equals(lower)
                || lower.contains("bearbeitung")
                || lower.contains("in arbeit")) {
            return Optional.of("WORKING");
        }
        if ("obsolete".equals(lower)
                || lower.contains("veraltet")) {
            return Optional.of("OBSOLETE");
        }

        String normalized = raw.toUpperCase(Locale.ROOT);
        if ("WORKING".equals(normalized) || "RELEASED".equals(normalized) || "OBSOLETE".equals(normalized)) {
            return Optional.of(normalized);
        }
        return Optional.empty();
    }

    private String sanitizeQuery(String input) {
        return input
                .replaceAll("(?i)\\b(find|suche|such|zeige|zeig|finde|objekt|objekte|objektliste|name|id|bitte|hallo|hi|hey|pepperoni|mir|alle|all|list|liste)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeFreeTextQuery(String query, String fallbackInput) {
        String base = (query == null || query.isBlank()) ? fallbackInput : query;

        String cleaned = base
                .replace('"', ' ')
                .replace('\'', ' ')
                .replace('`', ' ')
                .replaceAll("[^\\p{L}\\p{N}\\s*?_-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Wildcards auf Mehrwort-Freitext liefern in ES häufig ungültige Query-Konstrukte.
        if (cleaned.contains(" ") && (cleaned.contains("*") || cleaned.contains("?"))) {
            cleaned = cleaned.replace("*", "").replace("?", "").trim();
        }

        if (cleaned.isBlank()) {
            return "";
        }

        return cleaned;
    }

    private SearchIntent parseIntentRuleBased(String userInput, ObjectType forcedType, String forcedStatus) {
        Optional<String> objectId = detectObjectId(userInput);
        Optional<ObjectType> detectedType = detectObjectType(userInput);
        Optional<String> detectedStatus = detectStatus(userInput);

        ObjectType finalType = forcedType != null ? forcedType : detectedType.orElse(null);
        String finalStatus = normalizeStatus(forcedStatus).orElseGet(() -> detectedStatus.orElse(null));

        boolean allMode = ALL_REQUEST_PATTERN.matcher(userInput).find();
        boolean exactIdMode = objectId.isPresent();

        String queryText;
        if (exactIdMode) {
            queryText = objectId.get();
        } else if (allMode && finalType != null) {
            queryText = "";
        } else {
            queryText = normalizeFreeTextQuery(sanitizeQuery(userInput), userInput);
        }

        return new SearchIntent(queryText, finalType, finalStatus, exactIdMode, allMode, "RULES", false, null);
    }

    private Optional<SearchIntent> parseIntentViaLlm(String userInput, ObjectType forcedType, String forcedStatus) {
        if (!dialogflowClient.isAvailable()) {
            return Optional.empty();
        }

        try {
            String prompt = buildIntentPrompt(userInput, forcedType, forcedStatus);
            String raw = dialogflowClient.detectIntentText(prompt, "peb-user", llmLanguage);
            return parseLlmIntentResponse(raw, userInput, forcedType, forcedStatus);
        } catch (Exception e) {
            log.warn("PEPperoni LLM-Intent fehlgeschlagen, nutze Fallback-Regeln. Fehler: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildIntentPrompt(String userInput, ObjectType forcedType, String forcedStatus) {
        String forcedTypeValue = forcedType == null ? "null" : forcedType.name();
        String forcedStatusValue = normalizeStatus(forcedStatus).orElse("null");

        return "Du bist ein Such-Intent Parser für PEB. " +
                "Antworte NUR als JSON ohne Markdown. " +
                "Schema: {\"intent\":\"search|clarify\",\"query\":\"string\",\"objectType\":\"DESIGN|DRAWING|MACHINE|SYSTEM|SUBSYSTEM|MODULE|SUBMODULE|PART|AUXILIARY|PRODUCT_DATA|DOCUMENT_TEMPLATE|DOCUMENT|MATERIAL|SUPPLIER|null\",\"releaseStatus\":\"WORKING|RELEASED|OBSOLETE|null\",\"allMode\":true|false,\"exactIdMode\":true|false,\"needsClarification\":true|false,\"clarificationQuestion\":\"string|null\"}. " +
                "Wenn die Anfrage unklar ist, setze needsClarification=true und gib eine kurze Frage zurück. " +
                "Erfinde keine Treffer. " +
                "UserInput: " + userInput + ". " +
                "ForcedType: " + forcedTypeValue + ". ForcedStatus: " + forcedStatusValue + ".";
    }

    private Optional<SearchIntent> parseLlmIntentResponse(String raw, String userInput, ObjectType forcedType, String forcedStatus) {
        try {
            String cleaned = stripJsonFence(raw);
            JsonNode root = objectMapper.readTree(cleaned);

            String query = root.path("query").asText("");
            String objectTypeText = root.path("objectType").asText("");
            String releaseStatusText = root.path("releaseStatus").asText("");
            boolean allMode = root.path("allMode").asBoolean(false);
            boolean exactIdMode = root.path("exactIdMode").asBoolean(false);
            boolean needsClarification = root.path("needsClarification").asBoolean(false);
            String clarificationQuestion = root.path("clarificationQuestion").isMissingNode()
                    || root.path("clarificationQuestion").isNull()
                    ? null
                    : root.path("clarificationQuestion").asText();

            ObjectType resolvedType = forcedType != null
                    ? forcedType
                    : resolveObjectTypeFromToken(objectTypeText).orElse(null);

                String resolvedStatus = normalizeStatus(forcedStatus)
                    .orElseGet(() -> normalizeStatus(releaseStatusText)
                        .orElseGet(() -> detectStatus(userInput).orElse(null)));

            String normalizedQuery;
            if (exactIdMode) {
                normalizedQuery = detectObjectId(query).orElse(query);
            } else if (allMode && resolvedType != null) {
                normalizedQuery = "";
            } else {
                normalizedQuery = normalizeFreeTextQuery(query, query);
            }

            if (normalizedQuery.isBlank() && !allMode && !needsClarification && resolvedType == null) {
                needsClarification = true;
                clarificationQuestion = "Meinst du einen bestimmten Objekttyp (z.B. Design, Drawing, Part) oder eine konkrete ID?";
            }

            return Optional.of(new SearchIntent(
                    normalizedQuery,
                    resolvedType,
                    resolvedStatus,
                    exactIdMode,
                    allMode,
                    "LLM",
                    needsClarification,
                    clarificationQuestion
            ));
        } catch (Exception ex) {
            log.warn("LLM-Intent JSON ungültig, nutze Fallback-Regeln. Antwort war: {}", truncateForLog(raw));
            return Optional.empty();
        }
    }

    private Optional<ObjectType> resolveObjectTypeFromToken(String token) {
        if (token == null || token.isBlank() || "null".equalsIgnoreCase(token)) {
            return Optional.empty();
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(ObjectType.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return detectObjectType(token);
        }
    }

    private String stripJsonFence(String text) {
        if (text == null) {
            return "{}";
        }
        String cleaned = text.trim()
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("^```\\s*", "")
                .replaceFirst("```\\s*$", "")
                .trim();

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private String truncateForLog(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() > 250 ? text.substring(0, 250) + "..." : text;
    }

    private String buildResponseText(
            String query,
            SearchResultPageDTO resultPage,
            ObjectType appliedType,
            String appliedStatus,
            boolean exactIdMode,
            boolean allMode,
            String source) {

        if (resultPage.isSearchUnavailable()) {
            return "Ich bin PEPperoni. Die Suche ist aktuell nicht erreichbar, versuche es bitte gleich nochmal.";
        }

        StringBuilder filterInfo = new StringBuilder();
        if (appliedType != null) {
            filterInfo.append(" Typ=").append(appliedType.getDisplayName()).append(".");
        }
        if (appliedStatus != null) {
            filterInfo.append(" Status=").append(appliedStatus).append(".");
        }

        if (resultPage.getTotalHits() == 0) {
            if (allMode && appliedType != null) {
                return "Ich bin PEPperoni und habe keine Treffer für den Typ " + appliedType.getDisplayName() + " gefunden." + filterInfo;
            }
            return "Ich bin PEPperoni und habe keine Treffer für '" + query + "' gefunden." + filterInfo;
        }

        if (exactIdMode) {
            return "Ich bin PEPperoni. Ich habe " + resultPage.getTotalHits()
                    + " Treffer zur ID-Suche gefunden. Ich zeige dir die relevantesten Ergebnisse." + filterInfo;
        }

        if (allMode && appliedType != null) {
            return "Ich bin PEPperoni. Ich habe " + resultPage.getTotalHits()
                    + " Treffer für den Typ " + appliedType.getDisplayName() + " gefunden. Ich zeige dir die Top-Ergebnisse." + filterInfo;
        }

        String prefix = "Ich bin PEPperoni" + ("LLM".equals(source) ? " (LLM)" : "");
        return prefix + ". Ich habe " + resultPage.getTotalHits()
            + " Treffer für '" + query + "' gefunden. Ich zeige dir die Top-Ergebnisse." + filterInfo;
    }

    private record SearchIntent(
            String query,
            ObjectType objectType,
            String releaseStatus,
            boolean exactIdMode,
            boolean allMode,
            String source,
            boolean needsClarification,
            String clarificationQuestion) {
    }
}
