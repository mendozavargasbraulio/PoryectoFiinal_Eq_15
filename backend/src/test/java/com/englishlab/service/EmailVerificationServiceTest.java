package com.englishlab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishlab.config.AppConfig.AuthProperties;
import com.englishlab.config.AppConfig.VerificationProperties;
import com.englishlab.exception.AppExceptions.EmailVerificationException;
import com.englishlab.exception.AppExceptions.EmailVerificationException.Reason;
import com.englishlab.model.entity.EmailVerificationToken;
import com.englishlab.model.entity.User;
import com.englishlab.repository.EmailVerificationTokenRepository;
import com.englishlab.repository.UserRepository;
import com.englishlab.util.TokenUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Lógica del servicio sin BD ni Spring (sin transacción activa, AfterCommit ejecuta al instante). */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Duration TTL = Duration.ofHours(24);

    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;

    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        VerificationProperties props = new VerificationProperties(
                "https://english-labeq15.tech", "/verificar-correo", TTL,
                Duration.ofSeconds(60), 5, Duration.ofDays(7));
        service = new EmailVerificationService(
                tokenRepository, userRepository, notificationService, props, new AuthProperties(false, false));
        user = new User();
        user.setId(7L);
        user.setName("Ana");
        user.setEmail("ana@example.com");
    }

    @Test
    void issueAndSend_storesOnlyHashAndSendsHttpsLinkWithRawToken() {
        service.issueAndSend(user);

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendEmailVerification(eq(7L), eq("ana@example.com"), eq("Ana"), link.capture(), eq(TTL));

        assertThat(link.getValue()).startsWith("https://english-labeq15.tech/verificar-correo?token=");
        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(rawToken).hasSize(64);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenUtils.sha256Hex(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(Instant.now().plus(TTL).minusSeconds(5), Instant.now().plus(TTL).plusSeconds(5));
    }

    @Test
    void constructor_rejectsNonHttpsBaseUrl() {
        VerificationProperties insecure = new VerificationProperties(
                "http://english-labeq15.tech", "/verificar-correo", TTL, Duration.ofSeconds(60), 5, Duration.ofDays(7));
        assertThatThrownBy(() -> new EmailVerificationService(
                tokenRepository, userRepository, notificationService, insecure, new AuthProperties(false, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void verify_validToken_marksUserVerifiedAndConsumesToken() {
        EmailVerificationToken token = tokenFor("raw", Instant.now().plusSeconds(600), null);
        when(tokenRepository.findByTokenHash(TokenUtils.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(tokenRepository.markUsed(eq(1L), any())).thenReturn(1);

        service.verify("raw");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        verify(tokenRepository).markAllUsedForUser(eq(7L), any());
        verify(userRepository).save(user);
        verify(notificationService).sendWelcome(7L, "ana@example.com");
    }

    @Test
    void verify_unknownToken_isInvalid() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify("nope"))
                .isInstanceOfSatisfying(EmailVerificationException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID));
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verify_expiredToken_isRejectedAndAccountStaysUnverified() {
        EmailVerificationToken token = tokenFor("old", Instant.now().minusSeconds(1), null);
        when(tokenRepository.findByTokenHash(TokenUtils.sha256Hex("old"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verify("old"))
                .isInstanceOfSatisfying(EmailVerificationException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.EXPIRED));
        assertThat(user.isEmailVerified()).isFalse();
        verify(tokenRepository, never()).markUsed(anyLong(), any());
    }

    @Test
    void verify_alreadyUsedToken_isRejected() {
        EmailVerificationToken token = tokenFor("used", Instant.now().plusSeconds(600), Instant.now().minusSeconds(30));
        when(tokenRepository.findByTokenHash(TokenUtils.sha256Hex("used"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verify("used"))
                .isInstanceOfSatisfying(EmailVerificationException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.USED));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_raceLost_isRejectedAsUsed() {
        EmailVerificationToken token = tokenFor("race", Instant.now().plusSeconds(600), null);
        when(tokenRepository.findByTokenHash(TokenUtils.sha256Hex("race"))).thenReturn(Optional.of(token));
        when(tokenRepository.markUsed(eq(1L), any())).thenReturn(0); // otra petición lo consumió antes

        assertThatThrownBy(() -> service.verify("race"))
                .isInstanceOfSatisfying(EmailVerificationException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.USED));
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void resendIfAllowed_skipsVerifiedAccounts() {
        user.setEmailVerified(true);
        service.resendIfAllowed(user);
        verify(tokenRepository, never()).save(any());
        verify(notificationService, never()).sendEmailVerification(any(), any(), any(), any(), any());
    }

    @Test
    void resendIfAllowed_respectsCooldown() {
        EmailVerificationToken recent = tokenFor("r", Instant.now().plusSeconds(600), null);
        recent.setCreatedAt(Instant.now().minusSeconds(10));
        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(recent));

        service.resendIfAllowed(user);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resendIfAllowed_respectsHourlyLimit() {
        EmailVerificationToken old = tokenFor("o", Instant.now().plusSeconds(600), null);
        old.setCreatedAt(Instant.now().minusSeconds(120));
        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(old));
        when(tokenRepository.countByUserIdAndCreatedAtAfter(eq(7L), any())).thenReturn(5L);

        service.resendIfAllowed(user);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resendIfAllowed_sendsWhenOutsideCooldownAndUnderLimit() {
        EmailVerificationToken old = tokenFor("o", Instant.now().plusSeconds(600), null);
        old.setCreatedAt(Instant.now().minusSeconds(120));
        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(old));
        when(tokenRepository.countByUserIdAndCreatedAtAfter(eq(7L), any())).thenReturn(1L);

        service.resendIfAllowed(user);

        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(notificationService).sendEmailVerification(eq(7L), any(), any(), any(), any());
    }

    @Test
    void resend_unknownEmail_doesNothing() {
        when(userRepository.findByEmailIgnoreCase("x@example.com")).thenReturn(Optional.empty());
        service.resend("x@example.com");
        verify(tokenRepository, never()).save(any());
    }

    private EmailVerificationToken tokenFor(String raw, Instant expiresAt, Instant usedAt) {
        EmailVerificationToken t = new EmailVerificationToken();
        t.setId(1L);
        t.setUser(user);
        t.setTokenHash(TokenUtils.sha256Hex(raw));
        t.setExpiresAt(expiresAt);
        t.setUsedAt(usedAt);
        t.setCreatedAt(Instant.now());
        return t;
    }
}
