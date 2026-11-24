package com.englishlab.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vínculo entre un usuario de English Lab y una identidad externa (por ahora solo Google). */
@Entity
@Table(name = "user_identities")
@Getter
@Setter
@NoArgsConstructor
public class UserIdentity {

    public enum Provider { GOOGLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private Provider provider;

    /** Identificador estable del proveedor (claim {@code sub} de Google). */
    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    /** Correo que informó el proveedor al vincular (solo informativo/auditoría). */
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
