package com.englishlab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishlab.security.GoogleTokenVerifier;
import com.englishlab.security.GoogleTokenVerifier.GoogleIdentity;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceso con Google contra PostgreSQL real (requiere Docker). El verificador del ID token y el
 * JavaMailSender son mocks: no hay llamadas a Google ni se envía correo de verdad.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "app.mail.enabled=true",
        "app.mail.from=noreply@english-labeq15.tech",
        "app.verification.frontend-base-url=https://english-labeq15.tech",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"
})
class GoogleAuthFlowIT {

    private static final String PASSWORD = "Clave#2026x";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JavaMailSender mailSender;
    @MockitoBean GoogleTokenVerifier googleVerifier;

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void stubMimeFactory() {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void newGoogleUser_withVerifiedEmail_getsSession_isVerified_andNoVerificationMailIsSent() {
        String email = newEmail();
        stubGoogle("sub-" + UUID.randomUUID(), email, true);

        ResponseEntity<Map> res = google();

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) res.getBody().get("token")).isNotBlank();
        assertThat(jdbc.queryForObject("select email_verified from users where email = ?", Boolean.class, email)).isTrue();
        assertThat(jdbc.queryForObject("select r.name from users u join roles r on r.id = u.role_id where u.email = ?",
                String.class, email)).isEqualTo("STUDENT");
        assertThat(jdbc.queryForObject("select count(*) from email_verification_tokens t join users u on u.id = t.user_id "
                + "where u.email = ?", Integer.class, email)).isZero();
        // El token emitido es el mismo JWT del sistema: abre rutas protegidas.
        ResponseEntity<String> protectedRoute = http.exchange("/api/v1/me/objectives",
                org.springframework.http.HttpMethod.GET, bearer((String) res.getBody().get("token")), String.class);
        assertThat(protectedRoute.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void googleOnlyAccount_cannotLoginWithPassword() {
        String email = newEmail();
        stubGoogle("sub-" + UUID.randomUUID(), email, true);
        google();

        ResponseEntity<Map> login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void secondGoogleLogin_reusesTheSameAccountBySub() {
        String sub = "sub-" + UUID.randomUUID();
        String email = newEmail();
        stubGoogle(sub, email, true);
        google();
        google();

        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_identities where provider_subject = ?",
                Integer.class, sub)).isEqualTo(1);
    }

    @Test
    void googleWithUnverifiedEmail_createsPendingAccount_sendsOurLink_andIssuesNoJwt() {
        String email = newEmail();
        stubGoogle("sub-" + UUID.randomUUID(), email, false);

        ResponseEntity<Map> res = google();

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(res.getBody()).doesNotContainKey("token");
        assertThat(jdbc.queryForObject("select email_verified from users where email = ?", Boolean.class, email)).isFalse();
        verify(mailSender, timeout(5000)).send(any(MimeMessage.class));
    }

    @Test
    void preHijackedRegistration_losesItsPassword_whenTheRealOwnerSignsInWithGoogle() {
        String email = newEmail();
        // Un tercero registra el correo ajeno con una contraseña que él conoce (queda sin verificar).
        http.postForEntity("/api/v1/auth/register", Map.of("name", "Intruso", "email", email, "password", PASSWORD), Map.class);
        assertThat(jdbc.queryForObject("select email_verified from users where email = ?", Boolean.class, email)).isFalse();

        // El titular real entra con Google (correo certificado).
        stubGoogle("sub-" + UUID.randomUUID(), email, true);
        assertThat(google().getStatusCode()).isEqualTo(HttpStatus.OK);

        // La cuenta está verificada, pero la contraseña del intruso ya no sirve.
        assertThat(jdbc.queryForObject("select email_verified from users where email = ?", Boolean.class, email)).isTrue();
        assertThat(http.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void existingVerifiedLocalAccount_isLinked_andKeepsItsPassword() {
        // admin@englishlab.com viene del seed (verificado); se le vincula Google sin tocar su contraseña.
        String before = jdbc.queryForObject("select password_hash from users where email = 'admin@englishlab.com'", String.class);
        stubGoogle("sub-" + UUID.randomUUID(), "admin@englishlab.com", true);

        assertThat(google().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select password_hash from users where email = 'admin@englishlab.com'", String.class))
                .isEqualTo(before);
    }

    @Test
    void invalidIdToken_is401_andCreatesNothing() {
        when(googleVerifier.verify(any())).thenThrow(
                new com.englishlab.exception.AppExceptions.InvalidCredentialsException("No se pudo verificar tu cuenta de Google."));
        int before = jdbc.queryForObject("select count(*) from users", Integer.class);

        assertThat(google().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(before);
    }

    // ---------- helpers ----------

    private void stubGoogle(String sub, String email, boolean verified) {
        when(googleVerifier.verify(any())).thenReturn(new GoogleIdentity(sub, email, verified, "Ana Google"));
    }

    private ResponseEntity<Map> google() {
        return http.postForEntity("/api/v1/auth/google", Map.of("credential", "id-token"), Map.class);
    }

    private static String newEmail() {
        return "g-" + UUID.randomUUID() + "@example.com";
    }

    private static org.springframework.http.HttpEntity<Void> bearer(String jwt) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(jwt);
        return new org.springframework.http.HttpEntity<>(headers);
    }
}
