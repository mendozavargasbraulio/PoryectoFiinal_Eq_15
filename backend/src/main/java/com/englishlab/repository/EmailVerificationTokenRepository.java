package com.englishlab.repository;

import com.englishlab.model.entity.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Último token emitido para el usuario (cooldown de reenvío). */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** Tokens emitidos desde {@code since} (límite por hora). */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    /**
     * Consumo atómico: solo uno de dos clics simultáneos sobre el mismo enlace obtiene 1;
     * el otro obtiene 0. Garantiza el "un solo uso" también bajo concurrencia.
     */
    @Modifying
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** Invalida el resto de enlaces pendientes del usuario tras verificar. */
    @Modifying
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int markAllUsedForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
