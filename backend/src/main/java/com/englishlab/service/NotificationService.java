package com.englishlab.service;

import com.englishlab.config.AppConfig.MailProperties;
import com.englishlab.model.entity.NotificationsLog;
import com.englishlab.model.entity.NotificationsLog.NotificationChannel;
import com.englishlab.model.entity.NotificationsLog.NotificationStatus;
import com.englishlab.repository.NotificationsLogRepository;
import com.englishlab.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envío de correo y registro en {@code notifications_log}. Asíncrono y a prueba de fallos:
 * un error de correo nunca rompe el flujo que lo dispara. El envío real solo ocurre si
 * {@code app.mail.enabled=true}.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public static final String TYPE_WELCOME = "WELCOME";
    public static final String TYPE_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String TYPE_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationsLogRepository notificationsLogRepository;
    private final UserRepository userRepository;
    private final MailProperties mailProperties;

    public NotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            NotificationsLogRepository notificationsLogRepository,
            UserRepository userRepository,
            MailProperties mailProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.notificationsLogRepository = notificationsLogRepository;
        this.userRepository = userRepository;
        this.mailProperties = mailProperties;
    }

    @Async
    public void sendWelcome(Long userId, String to) {
        send(userId, to, "¡Bienvenido a English Lab!",
                "Tu cuenta ha sido creada. ¡A aprender inglés!", TYPE_WELCOME);
    }

    @Async
    public void sendPasswordReset(Long userId, String to, String rawToken) {
        send(userId, to, "Recuperación de contraseña",
                "Usa este token para restablecer tu contraseña: " + rawToken, TYPE_PASSWORD_RESET);
    }

    /** Correo HTML (con alternativa en texto) con el enlace de verificación. */
    @Async
    public void sendEmailVerification(Long userId, String to, String name, String link, Duration ttl) {
        send(userId, to, EmailVerificationTemplate.SUBJECT,
                EmailVerificationTemplate.text(name, link, ttl),
                EmailVerificationTemplate.html(name, link, ttl),
                TYPE_EMAIL_VERIFICATION);
    }

    private void send(Long userId, String to, String subject, String body, String type) {
        send(userId, to, subject, body, null, type);
    }

    /** {@code html == null} -> texto plano; si no, multipart text + html. */
    private void send(Long userId, String to, String subject, String body, String html, String type) {
        // Con el envío desactivado no se ha mandado nada: la fila queda PENDING, no SENT.
        NotificationStatus status = mailProperties.enabled() ? NotificationStatus.SENT : NotificationStatus.PENDING;
        String error = null;
        try {
            if (mailProperties.enabled()) {
                JavaMailSender sender = mailSenderProvider.getIfAvailable();
                if (sender == null) {
                    throw new IllegalStateException("JavaMailSender no disponible (¿spring.mail.host configurado?)");
                }
                if (html == null) {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(mailProperties.from());
                    message.setTo(to);
                    message.setSubject(subject);
                    message.setText(body);
                    sender.send(message);
                } else {
                    MimeMessage mime = sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
                    helper.setFrom(mailProperties.from(), "English Lab");
                    helper.setTo(to);
                    helper.setSubject(subject);
                    helper.setText(body, html);
                    sender.send(mime);
                }
            }
        } catch (Exception e) {
            status = NotificationStatus.FAILED;
            error = e.getMessage();
            log.warn("Fallo al enviar correo {} a {}: {}", type, to, e.getMessage());
        } finally {
            try {
                persist(userId, type, status, error);
            } catch (Exception e) {
                log.warn("No se pudo registrar la notificación {} en notifications_log: {}", type, e.getMessage());
            }
        }
    }

    private void persist(Long userId, String type, NotificationStatus status, String error) {
        NotificationsLog entry = new NotificationsLog();
        entry.setUser(userId == null ? null : userRepository.getReferenceById(userId));
        entry.setChannel(NotificationChannel.EMAIL);
        entry.setType(type);
        entry.setStatus(status);
        entry.setErrorMessage(error);
        entry.setSentAt(Instant.now());
        notificationsLogRepository.save(entry);
    }
}
