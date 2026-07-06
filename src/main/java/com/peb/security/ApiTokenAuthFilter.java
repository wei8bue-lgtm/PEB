// src/main/java/com/peb/security/ApiTokenAuthFilter.java
package com.peb.security;

import com.peb.model.PebUser;
import com.peb.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApiTokenAuthFilter extends OncePerRequestFilter {

    private final ApiTokenService apiTokenService;

    public ApiTokenAuthFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain)
            throws ServletException, IOException {

        // Token aus Header lesen
        String token = request.getHeader("X-API-Token");

        if (token != null
                && !token.isBlank()
                && SecurityContextHolder.getContext()
                                        .getAuthentication() == null) {

            apiTokenService.validateToken(token)
                .ifPresent(user -> {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    user.getUsername(),
                                    null,
                                    resolveAuthorities(user.getRole()));
                    SecurityContextHolder.getContext()
                                         .setAuthentication(auth);
                });
        }

        chain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(String role) {
        List<SimpleGrantedAuthority> list = new ArrayList<>();
        switch (role) {
            case "ROLE_ADMIN" -> {
                list.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                list.add(new SimpleGrantedAuthority("READ"));
                list.add(new SimpleGrantedAuthority("WRITE"));
                list.add(new SimpleGrantedAuthority("DELETE"));
                list.add(new SimpleGrantedAuthority("EXECUTE"));
            }
            case "ROLE_USER" -> {
                list.add(new SimpleGrantedAuthority("ROLE_USER"));
                list.add(new SimpleGrantedAuthority("READ"));
                list.add(new SimpleGrantedAuthority("WRITE"));
            }
            default -> {
                list.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
                list.add(new SimpleGrantedAuthority("READ"));
            }
        }
        return list;
    }
}