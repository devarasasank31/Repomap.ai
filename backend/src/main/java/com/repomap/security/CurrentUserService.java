package com.repomap.security;

import java.time.Instant;

import com.repomap.security.AuthService.AuthResult;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Small helper for controllers to read the authenticated caller. */
public final class CurrentUserService {

    private CurrentUserService() {
    }

    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
    }

    public static java.util.Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(user);
    }

    public record TokenView(String token, String tokenType, Instant expiresAt, AuthDtos.UserResponse user) {
        public static TokenView from(AuthResult result) {
            return new TokenView(result.token(), "Bearer", result.expiresAt(),
                    AuthDtos.UserResponse.from(result.user()));
        }
    }

    public record UserView(long id, String username, String email) {
    }
}
