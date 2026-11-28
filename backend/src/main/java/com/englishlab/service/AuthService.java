package com.englishlab.service;

import com.englishlab.config.AppConfig.AuthProperties;
import com.englishlab.dto.AuthDtos.AuthResponse;
import com.englishlab.dto.AuthDtos.ForgotPasswordRequest;
import com.englishlab.dto.AuthDtos.ForgotPasswordSmsRequest;
import com.englishlab.dto.AuthDtos.LoginRequest;
import com.englishlab.dto.AuthDtos.MessageResponse;
import com.englishlab.dto.AuthDtos.RegisterRequest;
import com.englishlab.dto.AuthDtos.ResendVerificationRequest;
import com.englishlab.dto.AuthDtos.ResetPasswordRequest;
import com.englishlab.dto.AuthDtos.ResetPasswordSmsRequest;
import com.englishlab.dto.AuthDtos.UserProfileResponse;
import com.englishlab.dto.AuthDtos.UserResponse;
import com.englishlab.dto.AuthDtos.VerifyEmailRequest;
import com.englishlab.exception.AppExceptions.BadRequestException;
import com.englishlab.exception.AppExceptions.BusinessRuleException;
import com.englishlab.exception.AppExceptions.EmailNotVerifiedException;
import com.englishlab.exception.AppExceptions.InvalidCredentialsException;
import com.englishlab.exception.AppExceptions.NotFoundException;
import com.englishlab.model.entity.PasswordResetToken;
import com.englishlab.model.entity.RevokedToken;
import com.englishlab.model.entity.Role;
import com.englishlab.model.entity.Role.RoleName;
import com.englishlab.model.entity.User;
import com.englishlab.repository.PasswordResetTokenRepository;
import com.englishlab.repository.RevokedTokenRepository;
import com.englishlab.repository.RoleRepository;
import com.englishlab.repository.UserRepository;
import com.englishlab.security.JwtService;
import com.englishlab.security.JwtService.AuthenticatedUser;
import com.englishlab.util.AfterCommit;
import com.englishlab.util.TokenUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Validez del token de recuperacion: 30 minutos. */
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int RESET_TOKEN_BYTES = 32;
    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    /** Misma respuesta para registros nuevos, correos ya registrados y reenvíos: evita enumerar cuentas. */
    static final String REGISTRATION_MESSAGE = "Si el correo puede registrarse, te hemos enviado un enlace "
            + "de verificación. Revisa tu bandeja de entrada y la carpeta de spam.";
    static final String RESEND_MESSAGE = "Si existe una cuenta pendiente de verificar para ese correo, "
            + "te enviaremos un nuevo enlace en unos minutos.";
    static final String FORGOT_SMS_MESSAGE = "Si el número está registrado, recibirás un código por SMS";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final NotificationService notificationService;
    private final EmailVerificationService emailVerificationService;
    private final TwilioVerifyService twilioVerifyService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RevokedTokenRepository revokedTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthProperties authProperties,
            NotificationService notificationService,
            EmailVerificationService emailVerificationService,
            TwilioVerifyService twilioVerifyService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
        this.notificationService = notificationService;
        this.emailVerificationService = emailVerificationService;
        this.twilioVerifyService = twilioVerifyService;
    }

    /**
     * Registro con correo y contraseña. Crea la cuenta SIN verificar y envía el enlace de
     * verificación; no emite JWT. La respuesta es la misma si el correo ya existía.
     */
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        // Se calcula SIEMPRE (también si el correo ya existe) para que el tiempo de respuesta
        // no delate si la cuenta existe.
        String passwordHash = passwordEncoder.encode(request.password());

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            throw new BusinessRuleException("Ya existe una cuenta registrada con ese correo electrónico.");
        }

        Role studentRole = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new IllegalStateException(
                        "Rol STUDENT no encontrado en la base de datos; ¿se aplicaron las migraciones/seed?"));
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(studentRole);
        user.setPhone(phone);
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(false);
        user = userRepository.save(user);

        emailVerificationService.issueAndSend(user);
        return new MessageResponse(REGISTRATION_MESSAGE);
    }

    /** Consume el token del enlace; solo entonces la cuenta queda verificada. */
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
    }

    /** Reenvío del enlace: siempre la misma respuesta, exista o no la cuenta. */
    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        emailVerificationService.resend(normalizeEmail(request.email()));
        return new MessageResponse(RESEND_MESSAGE);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales inválidas");
        }
        // Solo tras acreditar la contraseña (así no se revela nada a quien no la conoce).
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Debes verificar tu correo electrónico antes de iniciar sesión.");
        }
        String token = jwtService.generateToken(user.getId(), user.getRole().getName());
        return new AuthResponse(token, toResponse(user));
    }

    @Transactional
    public void logout(AuthenticatedUser principal) {
        RevokedToken revoked = new RevokedToken();
        revoked.setJti(principal.jti());
        revoked.setUser(userRepository.getReferenceById(principal.id()));
        revoked.setExpiresAt(principal.expiresAt());
        revoked.setRevokedAt(Instant.now());
        revokedTokenRepository.save(revoked);
    }

    /** Responde igual exista o no el correo para evitar enumeración de cuentas. */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            String rawToken = TokenUtils.newHexToken(RESET_TOKEN_BYTES);
            String tokenHash = TokenUtils.sha256Hex(rawToken);

            PasswordResetToken prt = new PasswordResetToken();
            prt.setUser(user);
            prt.setTokenHash(tokenHash);
            prt.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
            prt.setCreatedAt(Instant.now());
            passwordResetTokenRepository.save(prt);

            // El token SOLO se loguea si app.auth.log-reset-token=true (default false),
            // para poder probar en local sin exponerlo nunca en despliegue.
            if (authProperties.logResetToken()) {
                log.info("Token de recuperación de contraseña para {}: {}", email, rawToken);
            }

            final Long userId = user.getId();
            final String userEmail = user.getEmail();
            final String token = rawToken;
            AfterCommit.run(() -> notificationService.sendPasswordReset(userId, userEmail, token));
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = TokenUtils.sha256Hex(request.token());
        PasswordResetToken prt = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessRuleException("Token inválido o expirado"));
        if (prt.getUsedAt() != null) {
            throw new BusinessRuleException("El token ya fue utilizado");
        }
        if (prt.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Token expirado");
        }
        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        prt.setUsedAt(Instant.now());
        userRepository.save(user);
    }

    /**
     * Solicita un código OTP por SMS (Twilio Verify). Devuelve siempre el mismo mensaje genérico
     * exista o no el teléfono para no filtrar cuentas registradas.
     */
    @Transactional(readOnly = true)
    public MessageResponse forgotPasswordBySms(ForgotPasswordSmsRequest request) {
        twilioVerifyService.ensureConfigured();
        String phone = normalizePhone(request.phone());
        userRepository.findByPhone(phone).ifPresent(user -> twilioVerifyService.sendSmsCode(phone));
        return new MessageResponse(FORGOT_SMS_MESSAGE);
    }

    /**
     * Verifica el código SMS en Twilio Verify y actualiza la contraseña del usuario.
     * Si el teléfono no existe o el código es inválido/expirado responde 400 Bad Request.
     */
    @Transactional
    public MessageResponse resetPasswordBySms(ResetPasswordSmsRequest request) {
        twilioVerifyService.ensureConfigured();
        String phone = normalizePhone(request.phone());
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BadRequestException("Código inválido o expirado"));
        if (!twilioVerifyService.checkSmsCode(phone, request.code().trim())) {
            throw new BadRequestException("Código inválido o expirado");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return new MessageResponse("Contraseña actualizada correctamente.");
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        User teacher = user.getTeacher();
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                teacher == null ? null : teacher.getName(),
                teacher == null ? null : teacher.getEmail());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().getName().name());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String cleaned = rawPhone.trim().replaceAll("[\\s\\-().]+", "");
        if (cleaned.length() > 20 || !E164_PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new BadRequestException("El teléfono debe tener formato internacional válido (ej. +529511234567)");
        }
        return cleaned;
    }

    /**
     * Purga los tokens revocados ya vencidos de `revoked_tokens` (filas con
     * {@code expires_at < now()}) para que la tabla no crezca indefinidamente. Cada dia a las 03:00.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredRevokedTokens() {
        long deleted = revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purga de revoked_tokens vencidos: {} fila(s) eliminadas", deleted);
        }
    }
}
