package com.peb.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Ersetzt die Whitelabel Error Page
 * Zeigt eine benutzerfreundliche Fehlerseite
 */
@Controller
@Slf4j
public class PEBErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {

        // Fehler-Details aus Request holen
        Object statusCode = request.getAttribute(
                RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(
                RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(
                RequestDispatcher.ERROR_REQUEST_URI);
        Object exception = request.getAttribute(
                RequestDispatcher.ERROR_EXCEPTION);

        // Logging für Diagnose
        log.error("=== FEHLER AUFGETRETEN ===");
        log.error("URL:        {}", requestUri);
        log.error("Status:     {}", statusCode);
        log.error("Meldung:    {}", errorMessage);
        if (exception != null) {
            log.error("Exception:  {}", exception.toString());
        }

        // Status bestimmen
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (statusCode != null) {
            try {
                status = HttpStatus.valueOf(Integer.parseInt(statusCode.toString()));
            } catch (Exception ignored) {}
        }

        // Model befüllen
        model.addAttribute("statusCode",   statusCode != null ? statusCode : 500);
        model.addAttribute("statusText",   status.getReasonPhrase());
        model.addAttribute("requestUri",   requestUri != null ? requestUri : "Unbekannt");
        model.addAttribute("errorMessage", errorMessage != null ? errorMessage : "Unbekannter Fehler");

        // Exception-Details (nur in Dev-Modus hilfreich)
        if (exception != null) {
            Throwable t = (Throwable) exception;
            model.addAttribute("exceptionType",    t.getClass().getSimpleName());
            model.addAttribute("exceptionMessage", t.getMessage());
        }

        return "error";
    }
}