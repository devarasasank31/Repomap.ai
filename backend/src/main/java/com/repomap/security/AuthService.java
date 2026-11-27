package com.repomap.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import com.repomap.exception.ConflictException;
import com.repomap.exception.UnauthorizedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and validates opaque bearer tokens and manages local credentials.
 * Passwords are stored with BCrypt; tokens are stored only as SHA-256 hashes.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final long tokenTtlHours;

    public AuthService(
            UserRepository userRepository,
            AuthTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${repomap.security.token-ttl-hours:24}") long tokenTtlHours) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtlHours = tokenTtlHours;
    }

    @Transactional
    public AuthResult register(String username, String email, String rawPassword) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        String hash = passwordEncoder.encode(rawPassword);
        User user = userRepository.save(new User(normalizedUsername, normalizedEmail, hash));
        return issueToken(user);
    }

    @Transactional
    public AuthResult login(String usernameOrEmail, String rawPassword) {
        String identifier = usernameOrEmail.trim();
        Optional<User> candidate = identifier.contains("@")
                ? userRepository.findByEmail(identifier.toLowerCase())
                : userRepository.findByUsername(identifier);

        User user = candidate.orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        // Same message for unknown user and bad password to avoid user enumeration.
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }
        return issueToken(user);
    }

    @Transactional
    public void logout(String rawToken) {
        hashToken(rawToken).flatMap(tokenRepository::findByTokenHash).ifPresent(tokenRepository::delete);
    }

    @Transactional
    public Optional<User> findUserByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return hashToken(rawToken)
                .flatMap(tokenRepository::findByTokenHash)
                .filter(token -> !token.isExpired(Instant.now()))
                .map(AuthToken::getUser);
    }

    private AuthResult issueToken(User user) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        Instant expiresAt = Instant.now().plus(tokenTtlHours, ChronoUnit.HOURS);
        tokenRepository.save(new AuthToken(user, hash(token), expiresAt));
        return new AuthResult(token, expiresAt, user);
    }

    private static Optional<String> hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return Optional.of(HexFormat.of().formatHex(hashed));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String hash(String rawToken) {
        return hashToken(rawToken).orElseThrow();
    }

    public record AuthResult(String token, Instant expiresAt, User user) {
    }
}
