package com.repomap.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Authorization: Bearer <token>} into an authenticated principal.
 * Requests without a (valid) token simply stay anonymous so the standard
 * security chain can answer with 401/403.
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public TokenAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractToken(request)
                    .flatMap(authService::findUserByToken)
                    .ifPresent(user -> {
                        AuthenticatedUser principal =
                                new AuthenticatedUser(user.getId(), user.getUsername(), user.getEmail());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }
}
