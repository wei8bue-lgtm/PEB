// src/main/java/com/peb/config/SecurityDiagnostic.java
package com.peb.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Component
public class SecurityDiagnostic {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityDiagnostic.class);

    private final ApplicationContext applicationContext;

    public SecurityDiagnostic(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void printFilterChain() {
        try {
            FilterChainProxy proxy = (FilterChainProxy)
                    applicationContext.getBean("springSecurityFilterChain");

            log.info("══════════════════════════════════════════");
            log.info("Aktive Security Filter Chain:");
            for (SecurityFilterChain chain : proxy.getFilterChains()) {
                chain.getFilters().forEach(filter ->
                    log.info("  → {}", filter.getClass().getSimpleName())
                );
            }
            log.info("══════════════════════════════════════════");

        } catch (Exception e) {
            log.error("FilterChain konnte nicht gelesen werden: {}",
                    e.getMessage());
        }
    }
}