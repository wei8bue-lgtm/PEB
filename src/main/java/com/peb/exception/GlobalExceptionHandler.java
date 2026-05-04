package com.peb.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Globaler Exception Handler
 * Ersetzt die Whitelabel Error Page durch strukturierte JSON/HTML Antworten
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleObjectNotFound(
            ObjectNotFoundException ex,
            HttpServletRequest request) {
        log.warn("ObjectNotFound: {} - URL: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<Map<String, Object>> handleWorkflowException(
            WorkflowException ex,
            HttpServletRequest request) {
        log.warn("WorkflowException: {} - URL: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StructureException.class)
    public ResponseEntity<Map<String, Object>> handleStructureException(
            StructureException ex,
            HttpServletRequest request) {
        log.warn("StructureException: {} - URL: {}", ex.getMessage(), request.getRequestURI());
        return createErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        String uri = request.getRequestURI();
        // favicon stillschweigend ignorieren
        if (uri.contains("favicon")) {
            return ResponseEntity.notFound().build();
        }
        log.warn("NoResourceFound: {}", uri);
        return createErrorResponse("Ressource nicht gefunden: " + uri, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {
        log.warn("NoHandlerFound: {}", request.getRequestURI());
        return createErrorResponse(
                "Endpunkt nicht gefunden: " + request.getRequestURI(),
                HttpStatus.NOT_FOUND);
    }

    /**
     * Alle anderen Exceptions - mit vollem Stack-Trace im Log!
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        // Vollständiger Stack-Trace im Log → zeigt die echte Ursache!
        log.error("Unbehandelte Exception bei URL: {}", request.getRequestURI(), ex);
        log.error("Exception Typ:    {}", ex.getClass().getName());
        log.error("Exception Meldung: {}", ex.getMessage());

        // Cause chain ausgeben
        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            log.error("  Caused by [{}]: {} - {}", depth, cause.getClass().getName(), cause.getMessage());
            cause = cause.getCause();
            depth++;
        }

        return createErrorResponse(
                "Interner Serverfehler: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(
            String message, HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status",    status.value());
        error.put("error",     status.getReasonPhrase());
        error.put("message",   message);
        return new ResponseEntity<>(error, status);
    }
}