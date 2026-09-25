package com.peb.security;

import com.peb.config.AdDomainProperties;
import com.peb.service.AdUserDetailsService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MultiDomainAuthenticationProvider implements AuthenticationProvider {

    private final String defaultDomain;
    private final Map<String, ActiveDirectoryLdapAuthenticationProvider> providers;
    private final Map<String, String> configuredDomains;

    public MultiDomainAuthenticationProvider(
            AdDomainProperties properties,
            AdUserDetailsService userDetailsService) {
        this.defaultDomain = normalizeDomain(properties.getDefaultDomain());
        this.providers = new HashMap<>();
        this.configuredDomains = new HashMap<>();

        properties.getDomains().forEach((key, configuration) -> {
            String domainKey = normalizeDomain(key);
            if (!configuration.isEnabled()) {
                return;
            }
            requireValue(domainKey, "domain", configuration.getDomain());
            requireValue(domainKey, "url", configuration.getUrl());
            requireValue(domainKey, "root-dn", configuration.getRootDn());

            ActiveDirectoryLdapAuthenticationProvider provider =
                    new ActiveDirectoryLdapAuthenticationProvider(
                            configuration.getDomain(),
                            configuration.getUrl(),
                            configuration.getRootDn());
                provider.setUserDetailsContextMapper(
                    new DomainUserDetailsContextMapper(domainKey, userDetailsService));
            provider.setSearchFilter(configuration.getSearchFilter());
            provider.setConvertSubErrorCodesToExceptions(true);
            provider.setUseAuthenticationRequestCredentials(true);
            providers.put(domainKey, provider);
            configuredDomains.put(domainKey, configuration.getDomain().toLowerCase(Locale.ROOT));
        });

        if (!providers.containsKey(defaultDomain)) {
            throw new IllegalStateException(
                    "Die Standard-AD-Domain ist nicht aktiviert: " + defaultDomain);
        }
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String suppliedUsername = String.valueOf(authentication.getPrincipal()).trim();
        DomainUsername domainUsername = parseUsername(suppliedUsername);
        ActiveDirectoryLdapAuthenticationProvider provider =
                providers.get(domainUsername.domain());

        if (provider == null) {
            throw new BadCredentialsException(
                    "Unbekannte oder deaktivierte AD-Domain: " + domainUsername.domain());
        }

        UsernamePasswordAuthenticationToken routedRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        domainUsername.username(), authentication.getCredentials());
        Authentication authenticated = provider.authenticate(routedRequest);

        if (authenticated == null) {
            return null;
        }

        return UsernamePasswordAuthenticationToken.authenticated(
                authenticated.getName(),
                authenticated.getCredentials(),
                authenticated.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private DomainUsername parseUsername(String suppliedUsername) {
        int separator = suppliedUsername.indexOf('\\');
        if (separator > 0 && separator < suppliedUsername.length() - 1) {
            return new DomainUsername(
                    normalizeDomain(suppliedUsername.substring(0, separator)),
                    suppliedUsername.substring(separator + 1).trim());
        }

        int at = suppliedUsername.indexOf('@');
        if (at > 0 && at < suppliedUsername.length() - 1) {
            String suffix = suppliedUsername.substring(at + 1).trim();
                String domainKey = configuredDomains.entrySet().stream()
                    .filter(entry -> entry.getValue().equalsIgnoreCase(suffix))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(defaultDomain);
            return new DomainUsername(domainKey, suppliedUsername.substring(0, at).trim());
        }

        return new DomainUsername(defaultDomain, suppliedUsername);
    }

    private String normalizeDomain(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireValue(String domainKey, String property, String value) {
        if (value == null || value.isBlank() || value.contains("CHANGE_ME")) {
            throw new IllegalStateException(
                    "Fehlende AD-Konfiguration für " + domainKey + ": " + property);
        }
    }

    private record DomainUsername(String domain, String username) {}

    private static final class DomainUserDetailsContextMapper
            implements UserDetailsContextMapper {
        private final String domainKey;
        private final AdUserDetailsService delegate;

        private DomainUserDetailsContextMapper(
                String domainKey, AdUserDetailsService delegate) {
            this.domainKey = domainKey;
            this.delegate = delegate;
        }

        @Override
        public UserDetails mapUserFromContext(
                DirContextOperations ctx,
                String username,
                java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
            return delegate.mapUserFromContext(ctx, domainKey + "\\" + username, authorities);
        }

        @Override
        public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
            delegate.mapUserToContext(user, ctx);
        }
    }
}