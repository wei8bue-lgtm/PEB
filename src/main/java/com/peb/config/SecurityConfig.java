// src/main/java/com/peb/config/SecurityConfig.java
package com.peb.config;

import com.peb.service.AdUserDetailsService;
import com.peb.service.ApiTokenService;
import com.peb.security.ApiTokenAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${peb.ad.domain}")
    private String adDomain;

    @Value("${peb.ad.url}")
    private String adUrl;

    @Value("${peb.ad.root-dn}")
    private String adRootDn;

    @Value("${peb.ad.search-filter:(&(objectClass=user)(sAMAccountName={0}))}")
    private String adSearchFilter;

    private final AdUserDetailsService adUserDetailsService;

    public SecurityConfig(AdUserDetailsService adUserDetailsService) {
        this.adUserDetailsService = adUserDetailsService;
    }

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider adAuthProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider =
                new ActiveDirectoryLdapAuthenticationProvider(
                        adDomain, adUrl, adRootDn);
        provider.setUserDetailsContextMapper(adUserDetailsService);
        provider.setSearchFilter(adSearchFilter);
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(adAuthProvider()));
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
	
// In SecurityConfig.java – apiFilterChain anpassen:

	@Bean
	public SecurityFilterChain apiFilterChain(
			HttpSecurity http,
			ApiTokenService apiTokenService) throws Exception {

		http
			.securityMatcher("/api/**")
			.addFilterBefore(
				new ApiTokenAuthFilter(apiTokenService),
				org.springframework.security.web.authentication.www
					.BasicAuthenticationFilter.class)
			.authorizeHttpRequests(authz -> authz
				// Login ohne Token erlaubt
				.requestMatchers("/api/login", "/api/logout-api").permitAll()
				// Alle anderen API-Endpunkte benötigen Authentifizierung
				.anyRequest().authenticated()
			)
			.csrf(csrf -> csrf.disable())           // API = kein CSRF
			.sessionManagement(session -> session
				.sessionCreationPolicy(
					SessionCreationPolicy.IF_REQUIRED)
			)
			.httpBasic(basic -> {});               // Basic-Auth als Fallback

		return http.build();
	}

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // AD-Provider über AuthenticationManagerBuilder registrieren
        AuthenticationManagerBuilder authBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.authenticationProvider(adAuthProvider());

        http
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31_536_000)
                    .includeSubDomains(true)
                    .preload(true)
                )
                .contentTypeOptions(contentType -> {})
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy
                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
            )

            // ── HTTPS temporär deaktivieren zum Testen ────────
            // Wenn der Login danach funktioniert ist requiresChannel
            // die Ursache des Problems
            // .requiresChannel(channel -> channel
            //     .requestMatchers("/actuator/health").requiresInsecure()
            //     .anyRequest().requiresSecure()
            // )

			.authorizeHttpRequests(authz -> authz
				.requestMatchers(
					"/css/**", "/js/**", "/images/**",
					"/static/**", "/webjars/**", "/favicon.ico",
					"/actuator/health",
					"/login", "/api/login",
					"/error", "/debug/**"
				).permitAll()
                .requestMatchers(
                    HttpMethod.HEAD, "/datasets/*/as-pdf"
                ).hasAuthority("READ")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
				    .requestMatchers(
					HttpMethod.POST, "/**"
				).hasAuthority("WRITE")

				.requestMatchers(
					HttpMethod.PUT, "/**"
				).hasAuthority("WRITE")

				.requestMatchers(
					HttpMethod.DELETE, "/**"
				).hasAuthority("DELETE")
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
                .ignoringRequestMatchers(
                    "/login",
                    "/api/login",
                    "/api/logout-api",
                    "/api/v1/datasets/**",
                    "/api/v1/ids/**",
                    "/api/v1/revisions/**",
                    "/api/v1/attributes/**",
                    "/api/v1/viewer/**",
                    "/datasets/*/as-pdf",
					"/api/bmk/**"
                )
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().migrateSession()
                .maximumSessions(5)
                    .maxSessionsPreventsLogin(false)
            )

            .httpBasic(basic -> {});

        return http.build();
    }
}