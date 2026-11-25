package com.englishlab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishlab.dto.AuthDtos.GoogleSignInResult;
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
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Reglas de decisión del acceso con Google, sin BD ni Spring. */
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    private static final String SUB = "1234567890";
    private static final String EMAIL = "ana@gmail.com";

    @Mock GoogleTokenVerifier verifier;
    @Mock UserRepository userRepository;
    @Mock UserIdentityRepository identityRepository;
    @Mock RoleRepository roleRepository;
    @Mock EmailVerificationService emailVerificationService;
    @Mock NotificationService notificationService;
    @Mock JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private GoogleAuthService service;
    private Role studentRole;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthService(verifier, userRepository, identityRepository, roleRepository,
                emailVerificationService, notificationService, passwordEncoder, jwtService);
        studentRole = new Role();
        studentRole.setName(RoleName.STUDENT);
    }

    private void google(boolean emailVerified) {
        when(verifier.verify("cred")).thenReturn(new GoogleIdentity(SUB, EMAIL, emailVerified, "Ana Gómez"));
    }

    private User localUser(boolean verified, String hash) {
        User u = new User();
        u.setId(7L);
        u.setName("Ana");
        u.setEmail(EMAIL);
        u.setPasswordHash(hash);
        u.setRole(studentRole);
        u.setEmailVerified(verified);
        return u;
    }

    @Test
    void newAccount_withVerifiedGoogleEmail_isVerified_noVerificationMail_andGetsJwt() {
        google(true);
        when(identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.of(studentRole));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(11L);
            return u;
        });
        when(jwtService.generateToken(11L, RoleName.STUDENT)).thenReturn("jwt");

        GoogleSignInResult result = service.signIn("cred");

        assertThat(result.session().token()).isEqualTo("jwt");
        assertThat(result.session().user().role()).isEqualTo("STUDENT");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isEmailVerified()).isTrue();
        assertThat(saved.getValue().getEmailVerifiedAt()).isNotNull();
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2");
        verify(emailVerificationService, never()).issueAndSend(any());
        verify(emailVerificationService, never()).resendIfAllowed(any());
        verify(notificationService).sendWelcome(11L, EMAIL);
        ArgumentCaptor<UserIdentity> identity = ArgumentCaptor.forClass(UserIdentity.class);
        verify(identityRepository).saveAndFlush(identity.capture());
        assertThat(identity.getValue().getProviderSubject()).isEqualTo(SUB);
    }

    @Test
    void newAccount_whenGoogleDoesNotCertifyEmail_staysUnverified_sendsOurLink_andNoJwt() {
        google(false);
        when(identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.of(studentRole));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        GoogleSignInResult result = service.signIn("cred");

        assertThat(result.session()).isNull();
        assertThat(result.pending().message()).isNotBlank();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        verify(emailVerificationService).issueAndSend(saved.getValue());
        verify(jwtService, never()).generateToken(any(), any());
        verify(notificationService, never()).sendWelcome(any(), any());
    }

    @Test
    void existingVerifiedLocalAccount_isLinked_keepingItsPassword() {
        google(true);
        User user = localUser(true, "$2a$existing-hash");
        when(identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(identityRepository.existsByUserIdAndProvider(7L, Provider.GOOGLE)).thenReturn(false);
        when(jwtService.generateToken(7L, RoleName.STUDENT)).thenReturn("jwt");

        GoogleSignInResult result = service.signIn("cred");

        assertThat(result.session().token()).isEqualTo("jwt");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$existing-hash");
        verify(identityRepository).saveAndFlush(any(UserIdentity.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void existingUnverifiedLocalAccount_isVerifiedByGoogle_andItsPasswordIsRevoked() {
        google(true);
        User user = localUser(false, "$2a$attacker-chosen-hash");
        when(identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(identityRepository.existsByUserIdAndProvider(7L, Provider.GOOGLE)).thenReturn(false);
        // El servicio de verificación (mock) marca la cuenta como verificada como lo haría el real.
        org.mockito.Mockito.doAnswer(i -> {
            ((User) i.getArgument(0)).setEmailVerified(true);
            return null;
        }).when(emailVerificationService).markVerifiedByTrustedProvider(user);
        when(jwtService.generateToken(7L, RoleName.STUDENT)).thenReturn("jwt");

        GoogleSignInResult result = service.signIn("cred");

        assertThat(result.session()).isNotNull();
        assertThat(user.getPasswordHash()).isNotEqualTo("$2a$attacker-chosen-hash").startsWith("$2");
        verify(emailVerificationService).markVerifiedByTrustedProvider(user);
        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void existingAccount_whenGoogleDoesNotCertifyEmail_isNotLinkedNorVerified() {
        google(false);
        User user = localUser(false, "$2a$hash");
        when(identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        GoogleSignInResult result = service.signIn("cred");

        assertThat(result.session()).isNull();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("$2a$hash");
        verify(identityRepository, never()).saveAndFlush(any());
        verify(emailVerificationService).resendIfAllowed(user);
    }

    @Test
    void accountAlreadyLinkedToAnotherGoogleSub_isRejected() {
        google(true);
        User user = localUser(true, "$2a$hash");
        when(identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(identityRepository.existsByUserIdAndProvider(7L, Provider.GOOGLE)).thenReturn(true);

        assertThatThrownBy(() -> service.signIn("cred")).isInstanceOf(InvalidCredentialsException.class);
        verify(identityRepository, never()).saveAndFlush(any());
    }

    @Test
    void returningUser_isResolvedBySub_evenIfGoogleEmailChanged() {
        when(verifier.verify("cred")).thenReturn(new GoogleIdentity(SUB, "nuevo@gmail.com", true, "Ana"));
        User user = localUser(true, "$2a$hash");
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setProvider(Provider.GOOGLE);
        identity.setProviderSubject(SUB);
        identity.setCreatedAt(Instant.now());
        when(identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, SUB)).thenReturn(Optional.of(identity));
        when(jwtService.generateToken(7L, RoleName.STUDENT)).thenReturn("jwt");

        GoogleSignInResult result = service.signIn("cred");

        assertThat(result.session().user().email()).isEqualTo(EMAIL); // el correo de la cuenta no cambia
        assertThat(identity.getLastLoginAt()).isNotNull();
        verify(userRepository, never()).findByEmailIgnoreCase(eq("nuevo@gmail.com"));
    }

    @Test
    void invalidToken_propagatesAndCreatesNothing() {
        when(verifier.verify("cred")).thenThrow(new InvalidCredentialsException("No se pudo verificar tu cuenta de Google."));

        assertThatThrownBy(() -> service.signIn("cred")).isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }
}
