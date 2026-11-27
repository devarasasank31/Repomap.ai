package com.repomap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Kept out of {@code SecurityConfig} on purpose: the security filter chain
 * depends on the authentication service, which in turn depends on the password
 * encoder, so the encoder must be defined independently to avoid a bean cycle.
 */
@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
