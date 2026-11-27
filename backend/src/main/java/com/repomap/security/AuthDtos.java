package com.repomap.security;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 64)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "must contain only letters, digits, dot, underscore or dash")
            String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 255) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record UserResponse(long id, String username, String email, Instant createdAt) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
        }
    }
}
