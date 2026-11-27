package com.repomap.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.repomap.exception.ConflictException;
import com.repomap.exception.UnauthorizedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(AuthTokenRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(userRepository, tokenRepository, passwordEncoder, 24);
    }

    @Test
    void registerStoresBcryptHashAndIssuesToken() {
        when(userRepository.existsByUsername("ann")).thenReturn(false);
        when(userRepository.existsByEmail("ann@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 7L);
                    return user;
                });

        AuthService.AuthResult result = authService.register("ann", "ann@example.com", "sup3r-secret");

        assertThat(result.token()).hasSize(64);
        assertThat(result.expiresAt()).isAfter(Instant.now());
        assertThat(result.user().getId()).isEqualTo(7L);
        assertThat(result.user().getPasswordHash()).isNotEqualTo("sup3r-secret");
        assertThat(passwordEncoder.matches("sup3r-secret", result.user().getPasswordHash())).isTrue();
        verify(tokenRepository).save(any(AuthToken.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("ann")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("ann", "other@example.com", "sup3r-secret"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("ann@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("bob", "ann@example.com", "sup3r-secret"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void loginAcceptsUsernameAndPassword() {
        User user = new User("ann", "ann@example.com", passwordEncoder.encode("sup3r-secret"));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 3L);
        when(userRepository.findByUsername("ann")).thenReturn(Optional.of(user));

        AuthService.AuthResult result = authService.login("ann", "sup3r-secret");

        assertThat(result.token()).hasSize(64);
        assertThat(result.user().getUsername()).isEqualTo("ann");
    }

    @Test
    void loginAcceptsEmailIdentifier() {
        User user = new User("ann", "ann@example.com", passwordEncoder.encode("sup3r-secret"));
        when(userRepository.findByEmail("ann@example.com")).thenReturn(Optional.of(user));

        assertThat(authService.login("ann@example.com", "sup3r-secret").user().getUsername()).isEqualTo("ann");
    }

    @Test
    void loginRejectsWrongPasswordWithSameMessageAsUnknownUser() {
        User user = new User("ann", "ann@example.com", passwordEncoder.encode("sup3r-secret"));
        when(userRepository.findByUsername("ann")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        String wrongPassword = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> authService.login("ann", "wrong-pass"), UnauthorizedException.class).getMessage();
        String unknownUser = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> authService.login("ghost", "wrong-pass"), UnauthorizedException.class).getMessage();

        assertThat(wrongPassword).isEqualTo(unknownUser);
    }

    @Test
    void expiredTokensResolveToNoUser() {
        User user = new User("ann", "ann@example.com", "hash");
        AuthToken expired = new AuthToken(user, "deadbeef", Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThat(authService.findUserByToken("any")).isEmpty();
    }
}
