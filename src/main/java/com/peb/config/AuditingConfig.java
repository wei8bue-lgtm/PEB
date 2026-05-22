// src/main/java/com/peb/config/AuditingConfig.java
package com.peb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Aktiviert Spring Data JPA Auditing.
 *
 * Wird benötigt, da PEBApplication.java KEIN @EnableJpaAuditing hat.
 * BaseObject verwendet @EntityListeners(AuditingEntityListener.class)
 * → ohne diese Config werden createdBy/createdDate NICHT befüllt.
 *
 * auditorAware liefert den Benutzernamen aus dem SecurityContext
 * (identisch mit SecurityConfig: admin/user/viewer).
 */
@Configuration
/* @EnableJpaAuditing(auditorAwareRef = "auditorAware") */
public class AuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            try {
                Authentication auth = SecurityContextHolder
                        .getContext()
                        .getAuthentication();

                if (auth == null
                        || !auth.isAuthenticated()
                        || "anonymousUser".equals(
                                auth.getPrincipal())) {
                    return Optional.of("system");
                }
                return Optional.of(auth.getName());

            } catch (Exception e) {
                return Optional.of("system");
            }
        };
    }
}