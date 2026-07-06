// src/main/java/com/peb/exception/GlobalExceptionHandler.java
package com.peb.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Globaler Exception Handler für REST-Controller
 * Gibt strukturierte Fehlermeldungen zurück statt Stack Traces
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ══════════════════════════════════════════════════════════
    // Fehlender Request-Parameter (z.B. revisionId fehlt)
    // HTTP 400 Bad Request
    // ══════════════════════════════════════════════════════════
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {

        log.warn("Fehlender Parameter: {} ({})",
                ex.getParameterName(), ex.getParameterType());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "status",    400,
                    "error",     "Bad Request",
                    "message",   "Pflichtparameter fehlt: '"
                                 + ex.getParameterName()
                                 + "' (Typ: " + ex.getParameterType() + ")",
                    "parameter", ex.getParameterName(),
                    "timestamp", LocalDateTime.now().toString()
                ));
    }

    // ══════════════════════════════════════════════════════════
    // Objekt nicht gefunden
    // HTTP 404 Not Found
    // ══════════════════════════════════════════════════════════
    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ObjectNotFoundException ex) {

        log.warn("Objekt nicht gefunden: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "status",    404,
                    "error",     "Not Found",
                    "message",   ex.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
    }

    // ══════════════════════════════════════════════════════════
    // Validierungsfehler (IllegalArgumentException)
    // HTTP 400 Bad Request
    // ══════════════════════════════════════════════════════════
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn("Validierungsfehler: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "status",    400,
                    "error",     "Bad Request",
                    "message",   ex.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
    }

    // ══════════════════════════════════════════════════════════
    // Allgemeiner Fallback
    // HTTP 500 Internal Server Error
    // ══════════════════════════════════════════════════════════
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex) {

        log.error("Unerwarteter Fehler: {}", ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status",    500,
                    "error",     "Internal Server Error",
                    "message",   "Unerwarteter Fehler: " + ex.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
    }
}