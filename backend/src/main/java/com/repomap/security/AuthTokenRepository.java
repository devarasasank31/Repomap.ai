package com.repomap.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
