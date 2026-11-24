package com.englishlab.service;

import com.englishlab.dto.AuthDtos.AuthResponse;
import com.englishlab.dto.AuthDtos.GoogleSignInResult;
import com.englishlab.dto.AuthDtos.MessageResponse;
import com.englishlab.dto.AuthDtos.UserResponse;
import com.englishlab.exception.AppExceptions.InvalidCredentialsException;
import com.englishlab.model.entity.Role;
import com.englishlab.model.entity.Role.RoleName;
import com.englishlab.model.entity.User;
import com.englishlab.model.entity.UserIdentity;
import com.englishlab.model.entity.UserIdentity.Provider;
import com.englishlab.repository.RoleRepository;
import com.englishlab.repository.UserIdentityRepository;
import com.englishlab.repository.UserRepository;
import com.englishlab.security.GoogleTokenVerifier;
import com.englishlab.security.GoogleTokenVerifier.GoogleIdentity;
import com.englishlab.security.JwtService;
import com.englishlab.util.AfterCommit;
import com.englishlab.util.TokenUtils;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro e inicio de sesión con Google. Convive con el registro tradicional ({@link AuthService})
 * sin mezclar credenciales:
 *
 * <ul>
 *   <li>La identidad se resuelve por el {@code sub} de Google (tabla {@code user_identities}), no por correo.</li>
 *   <li>Se respeta {@code email_verified}: si Google lo certifica, la cuenta queda verificada y NO se
 *       envía correo de verificación; si no, no se verifica nada y se usa el flujo de enlace por correo.</li>
 *   <li>Las cuentas nuevas son siempre STUDENT y llevan una contraseña aleatoria que nadie conoce, por lo
 *       que el login con contraseña no puede usarse sobre ellas.</li>
 *   <li>Si había un registro tradicional SIN verificar con ese correo, se anula su contraseña antes de
 *       vincularlo (evita que quien lo registró primero conserve acceso a la cuenta del titular real).</li>
 * </ul>
 *
 * El JWT emitido es el mismo que el del login tradicional.
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    static final String PENDING_MESSAGE = "Google no ha confirmado tu correo. Te hemos enviado un enlace "
            + "de verificación; ábrelo para activar tu cuenta y vuelve a iniciar sesión.";

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationService emailVerificationService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public GoogleAuthService(
            GoogleTokenVerifier googleTokenVerifier,
            UserRepository userRepository,
            UserIdentityRepository identityRepository,
            RoleRepository roleRepository,
            EmailVerificationService emailVerificationService,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.googleTokenVerifier = googleTokenVerifier;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.emailVerificationService = emailVerificationService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public GoogleSignInResult signIn(String credential) {
        GoogleIdentity google = googleTokenVerifier.verify(credential);
        String email = google.email().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        Optional<UserIdentity> linked = identityRepository
                .findByProviderAndProviderSubject(Provider.GOOGLE, google.subject());

        User user;
        if (linked.isPresent()) {
            user = linked.get().getUser();
            linked.get().setLastLoginAt(now);
            // Solo se da por verificado el correo que Google certificó si es el de la cuenta.
            if (google.emailVerified() && !user.isEmailVerified() && email.equalsIgnoreCase(user.getEmail())) {
                confirmByGoogle(user);
            }
        } else {
            Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
            if (existing.isPresent()) {
                user = existing.get();
                if (!google.emailVerified()) {
                    // Google no certifica el correo: no se vincula ni se toca la cuenta existente.
                    emailVerificationService.resendIfAllowed(user);
                    return pending();
                }
                linkToExistingAccount(user, google, email, now);
            } else {
                user = createStudent(google, email, now);
                link(user, google, email, now);
                if (!google.emailVerified()) {
                    emailVerificationService.issueAndSend(user);
                    return pending();
                }
                welcomeAfterCommit(user);
            }
        }

        if (!user.isEmailVerified()) {
            // Cuenta vinculada pero con el correo aún sin certificar: mismo flujo de enlace por correo.
            emailVerificationService.resendIfAllowed(user);
            return pending();
        }
        String token = jwtService.generateToken(user.getId(), user.getRole().getName());
        return new GoogleSignInResult(new AuthResponse(token, toResponse(user)), null);
    }

    /** Google certifica el correo de una cuenta que ya existía (tradicional o creada por un admin). */
    private void linkToExistingAccount(User user, GoogleIdentity google, String email, Instant now) {
        if (identityRepository.existsByUserIdAndProvider(user.getId(), Provider.GOOGLE)) {
            // Esa cuenta ya está vinculada a OTRO sub de Google: no se permite una segunda identidad.
            log.warn("Intento de vincular un segundo Google sub a la cuenta {}", user.getId());
            throw new InvalidCredentialsException("No se pudo verificar tu cuenta de Google.");
        }
        if (!user.isEmailVerified()) {
            // Registro tradicional pendiente: quien lo creó pudo no ser el titular del correo, así
            // que su contraseña deja de valer. El titular puede fijar otra con "Olvidé mi contraseña".
            user.setPasswordHash(unusablePasswordHash());
            confirmByGoogle(user);
            log.info("Cuenta pendiente {} verificada por Google; contraseña anterior anulada", user.getId());
        }
        link(user, google, email, now);
    }

    private void confirmByGoogle(User user) {
        emailVerificationService.markVerifiedByTrustedProvider(user);
        welcomeAfterCommit(user);
    }

    private User createStudent(GoogleIdentity google, String email, Instant now) {
        Role student = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new IllegalStateException(
                        "Rol STUDENT no encontrado en la base de datos; ¿se aplicaron las migraciones/seed?"));
        User user = new User();
        user.setName(displayName(google.name(), email));
        user.setEmail(email);
        user.setPasswordHash(unusablePasswordHash());
        user.setRole(student);
        user.setCreatedAt(now);
        // Solo se respeta lo que Google certifica; nunca se asume verificado.
        user.setEmailVerified(google.emailVerified());
        user.setEmailVerifiedAt(google.emailVerified() ? now : null);
        return userRepository.saveAndFlush(user);
    }

    private void link(User user, GoogleIdentity google, String email, Instant now) {
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setProvider(Provider.GOOGLE);
        identity.setProviderSubject(google.subject());
        identity.setEmail(email);
        identity.setCreatedAt(now);
        identity.setLastLoginAt(now);
        identityRepository.saveAndFlush(identity);
    }

    private void welcomeAfterCommit(User user) {
        final Long userId = user.getId();
        final String to = user.getEmail();
        AfterCommit.run(() -> notificationService.sendWelcome(userId, to));
    }

    /** Hash BCrypt de un secreto aleatorio que se descarta: ninguna contraseña lo satisface. */
    private String unusablePasswordHash() {
        return passwordEncoder.encode(TokenUtils.newHexToken(32));
    }

    private MessageResponse pendingMessage() {
        return new MessageResponse(PENDING_MESSAGE);
    }

    private GoogleSignInResult pending() {
        return new GoogleSignInResult(null, pendingMessage());
    }

    private static String displayName(String googleName, String email) {
        String name = googleName == null ? "" : googleName.trim();
        if (name.isEmpty()) {
            int at = email.indexOf('@');
            name = at > 0 ? email.substring(0, at) : email;
        }
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().getName().name());
    }
}
