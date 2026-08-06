package com.peb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving view templates
 *
 * HINWEIS: Die Route GET /search wurde hier entfernt (war ein leerer
 * Platzhalter ohne Logik: return "search";). Sie wird jetzt vollständig
 * von com.peb.controller.search.SearchPageController übernommen (echte
 * Such-Logik inkl. Filter/Paginierung). Die alte templates/search.html
 * kann gelöscht werden (bestätigt: leerer Platzhalter, nicht genutzt).
 */
@Controller
public class ViewController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}