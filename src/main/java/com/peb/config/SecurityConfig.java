package com.peb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.http.HttpMethod;

/**
 * Security Konfiguration
 * Unterstützt Form-Login (Browser) und API-Login (VBScript)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // ✅ NEU: Frame-Options auf SAMEORIGIN setzen
        .headers(headers -> headers
            .frameOptions(frame -> frame.sameOrigin())
        )
        .authorizeHttpRequests(authz -> authz
            .requestMatchers(
                "/css/**",
                "/js/**",
                "/images/**",
                "/static/**",
                "/webjars/**",
                "/favicon.ico",
                "/actuator/health",
                "/login",
                "/api/login",
                "/error",
                "/debug/**"
            ).permitAll()
            .requestMatchers(
                HttpMethod.HEAD,
                "/datasets/*/as-pdf"
            ).hasAuthority("READ")
            .requestMatchers("/actuator/**").authenticated()
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login")
            .loginProcessingUrl("/login")
            .defaultSuccessUrl("/", true)
            .failureUrl("/login?error=true")
            .successHandler(
                new SimpleUrlAuthenticationSuccessHandler("/"))
            .permitAll()
        )
        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/login?logout=true")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID")
            .permitAll()
        )
        .csrf(csrf -> csrf
            .csrfTokenRepository(
                CookieCsrfTokenRepository.withHttpOnlyFalse())
            .ignoringRequestMatchers(
                "/api/login",
                "/api/logout-api",
                "/api/v1/datasets/**",
                "/api/v1/ids/**",
                "/api/v1/revisions/**",
                "/api/v1/attributes/**",
                "/api/v1/viewer/**",
                "/datasets/*/as-pdf"
            )
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        )
        .securityContext(context -> context
            .requireExplicitSave(true)
        )
        .httpBasic(basic -> {});

    return http.build();
}

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin123"))
            .authorities("READ", "WRITE", "DELETE", "EXECUTE")
            .build();

        UserDetails user = User.builder()
            .username("user")
            .password(passwordEncoder().encode("user123"))
            .authorities("READ", "WRITE")
            .build();

        UserDetails viewer = User.builder()
            .username("viewer")
            .password(passwordEncoder().encode("viewer123"))
            .authorities("READ")
            .build();

        return new InMemoryUserDetailsManager(admin, user, viewer);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}