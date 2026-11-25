package com.englishlab.service;

import com.englishlab.config.AppConfig.AuthProperties;
import com.englishlab.config.AppConfig.VerificationProperties;
import com.englishlab.exception.AppExceptions.EmailVerificationException;
import com.englishlab.exception.AppExceptions.EmailVerificationException.Reason;
import com.englishlab.model.entity.EmailVerificationToken;
import com.englishlab.model.entity.User;
import com.englishlab.repository.EmailVerificationTokenRepository;
import com.englishlab.repository.UserRepository;
import com.englishlab.util.AfterCommit;
import com.englishlab.util.TokenUtils;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación de correo: emisión y envío del token, consumo (un solo uso, con expiración),
 * reenvío con límites y purga de cuentas nunca verificadas.
 *
 * <p>El token en claro solo existe en el enlace del correo; en la BD solo está su SHA-256.
 * Los reenvíos que no proceden (cuenta inexistente, ya verificada, cooldown, tope por hora)
 * son silenciosos para no permitir enumerar cuentas.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final VerificationProperties props;
    private final AuthProperties authProperties;
    private final String linkPrefix;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            VerificationProperties props,
            AuthProperties authProperties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.props = props;
        this.authProperties = authProperties;
        this.linkPrefix = buildLinkPrefix(props);
    }

    /** Falla al arrancar si el enlace no sería HTTPS (salvo localhost, para desarrollo). */
    private static String buildLinkPrefix(VerificationProperties props) {
        URI base = URI.create(props.frontendBaseUrl().trim());
        String host = base.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        if (!"https".equalsIgnoreCase(base.getScheme()) && !local) {
            throw new IllegalStateException(
                    "app.verification.frontend-base-url debe ser HTTPS (recibido: " + props.frontendBaseUrl() + ")");
        }
        String root = props.frontendBaseUrl().trim().replaceAll("/+$", "");
        String path = props.path().startsWith("/") ? props.path() : "/" + props.path();
        return root + path + "?token=";
    }

    /** Crea un token nuevo y programa el envío del correo tras el commit. */
    @Transactional
    public void issueAndSend(User user) {
        String rawToken = TokenUtils.newHexToken(TOKEN_BYTES);
        Instant now = Instant.now();

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(TokenUtils.sha256Hex(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(props.tokenTtl()));
        tokenRepository.save(token);

        String link = linkPrefix + rawToken;
        if (authProperties.logVerificationLink()) {
            // Solo desarrollo local (app.auth.log-verification-link=true).
            log.info("Enlace de verificación para {}: {}", user.getEmail(), link);
        }
        final Long userId = user.getId();
        final String to = user.getEmail();
        final String name = user.getName();
        final Duration ttl = props.tokenTtl();
        AfterCommit.run(() -> notificationService.sendEmailVerification(userId, to, name, link, ttl));
    }

    /** Reenvío por petición explícita. Nunca revela si el correo existe ni si se envió algo. */
    @Transactional
    public void resend(String normalizedEmail) {
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(this::resendIfAllowed);
    }

    /** Emite un nuevo enlace si la cuenta está pendiente y no se superan cooldown ni tope por hora. */
    @Transactional
    public void resendIfAllowed(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        Instant now = Instant.now();
        Optional<EmailVerificationToken> last = tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minus(props.resendCooldown()))) {
            log.info("Reenvío de verificación omitido (cooldown) para el usuario {}", user.getId());
            return;
        }
        long sentLastHour = tokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)));
        if (sentLastHour >= props.maxSendsPerHour()) {
            log.warn("Reenvío de verificación omitido (límite por hora) para el usuario {}", user.getId());
            return;
        }
        issueAndSend(user);
    }

    /**
     * Valida y consume el token. Solo marca la cuenta como verificada si el token es válido,
     * no ha expirado y nadie lo había usado antes.
     */
    @Transactional
    public void verify(String rawToken) {
        Instant now = Instant.now();
        EmailVerificationToken token = tokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawToken.trim()))
                .orElseThrow(() -> new EmailVerificationException(
                        Reason.INVALID, "El enlace de verificación no es válido."));
        if (token.getUsedAt() != null) {
            throw new EmailVerificationException(Reason.USED,
                    "Este enlace ya fue utilizado. Si ya verificaste tu correo, inicia sesión.");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new EmailVerificationException(Reason.EXPIRED,
                    "El enlace de verificación ha caducado. Solicita uno nuevo.");
        }
        // Consumo atómico: si otra petición lo gastó entre la lectura y aquí, afecta a 0 filas.
        if (tokenRepository.markUsed(token.getId(), now) == 0) {
            throw new EmailVerificationException(Reason.USED,
                    "Este enlace ya fue utilizado. Si ya verificaste tu correo, inicia sesión.");
        }
        User user = token.getUser();
        tokenRepository.markAllUsedForUser(user.getId(), now);
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
            final Long userId = user.getId();
            final String email = user.getEmail();
            AfterCommit.run(() -> notificationService.sendWelcome(userId, email));
        }
    }

    /**
     * Un proveedor de identidad de confianza (Google, con {@code email_verified=true}) certifica que
     * el titular controla el correo: la cuenta queda verificada y los enlaces pendientes dejan de valer.
     * No envía correo alguno.
     */
    @Transactional
    public void markVerifiedByTrustedProvider(User user) {
        Instant now = Instant.now();
        tokenRepository.markAllUsedForUser(user.getId(), now);
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
        }
    }

    /** Elimina cuentas que nunca verificaron su correo tras el periodo de retención (cada día 03:30). */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeUnverifiedAccounts() {
        Instant cutoff = Instant.now().minus(props.unverifiedRetention());
        int deleted = userRepository.deleteUnverifiedCreatedBefore(cutoff);
        if (deleted > 0) {
            log.info("Purga de cuentas sin verificar: {} eliminada(s)", deleted);
        }
    }
}
