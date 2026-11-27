package com.englishlab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishlab.model.entity.Role.RoleName;
import com.englishlab.security.JwtService;
import com.englishlab.util.TokenUtils;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flujo completo de verificación de correo contra PostgreSQL real (requiere Docker).
 * El JavaMailSender es un mock que captura el MimeMessage: no se envía nada de verdad.
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
class EmailVerificationFlowIT {

    private static final String PASSWORD = "Clave#2026x";
    private static final Pattern TOKEN_IN_LINK =
            Pattern.compile("https://english-labeq15\\.tech/verificar-correo\\?token=([0-9a-f]{64})");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JavaMailSender mailSender;

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @BeforeEach
    void stubMimeFactory() {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    // ---------- registro y envío ----------

    @Test
    void register_createsUnverifiedUser_hashesPasswordAndToken_sendsHttpsLink_andIssuesNoJwt() throws Exception {
        String email = newEmail();

        ResponseEntity<Map> res = register("Ana Prueba", email);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(res.getBody()).doesNotContainKey("token").doesNotContainKey("user");

        Map<String, Object> user = jdbc.queryForMap(
                "select id, email_verified, password_hash from users where email = ?", email);
        assertThat(user.get("email_verified")).isEqualTo(false);
        assertThat((String) user.get("password_hash")).startsWith("$2").isNotEqualTo(PASSWORD);

        MimeMessage mail = lastMail();
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(mail.getFrom()[0].toString()).contains("noreply@english-labeq15.tech");
        assertThat(mail.getSubject()).contains("Verifica tu correo");
        String html = part(mail, "text/html");
        assertThat(html).contains("Verificar mi cuenta").contains("https://english-labeq15.tech/verificar-correo?token=");
        assertThat(part(mail, "text/plain")).contains("https://english-labeq15.tech/verificar-correo?token=");

        // Solo se guarda el hash del token, nunca el token en claro
        String rawToken = tokenFrom(mail);
        List<String> hashes = jdbc.queryForList(
                "select token_hash from email_verification_tokens where user_id = ?", String.class, user.get("id"));
        assertThat(hashes).containsExactly(TokenUtils.sha256Hex(rawToken));
        assertThat(hashes).doesNotContain(rawToken);
    }

    @Test
    void register_withExistingEmail_isRejectedWith422() {
        String email = newEmail();
        ResponseEntity<Map> first = register("Ana", email);
        ResponseEntity<Map> second = register("Otra Ana", email);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject("select count(*) from users where lower(email) = ?", Integer.class, email)).isEqualTo(1);

        // También con una cuenta ya verificada (p. ej. el admin del seed)
        ResponseEntity<Map> verified = register("Intruso", "admin@englishlab.com");
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void mailFailure_doesNotVerifyAccount_andIsLogged() throws Exception {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));
        String email = newEmail();

        ResponseEntity<Map> res = register("Ana", email);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(jdbc.queryForObject("select email_verified from users where email = ?", Boolean.class, email)).isFalse();
        awaitCount("select count(*) from notifications_log n join users u on u.id = n.user_id "
                + "where u.email = '" + email + "' and n.type = 'EMAIL_VERIFICATION' and n.status = 'FAILED'", 1);
    }

    // ---------- acceso bloqueado antes de verificar ----------

    @Test
    void login_beforeVerification_is403_withoutJwt_andWrongPasswordStaysGeneric401() throws Exception {
        String email = newEmail();
        register("Ana", email);

        ResponseEntity<Map> blocked = login(email, PASSWORD);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody()).containsEntry("code", "EMAIL_NOT_VERIFIED").doesNotContainKey("token");

        ResponseEntity<Map> wrong = login(email, "Incorrecta#1");
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void jwtOfUnverifiedUser_isNeverAccepted() throws Exception {
        String email = newEmail();
        register("Ana", email);
        Long id = jdbc.queryForObject("select id from users where email = ?", Long.class, email);

        String forged = jwtService.generateToken(id, RoleName.STUDENT);
        ResponseEntity<String> res = http.exchange("/api/v1/me/objectives", org.springframework.http.HttpMethod.GET,
                bearer(forged), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- verificación ----------

    @Test
    void verify_success_thenLoginWorksAndProtectedRouteIsReachable() throws Exception {
        String email = newEmail();
        register("Ana", email);
        String token = tokenFrom(lastMail());

        ResponseEntity<Map> verify = verifyEmail(token);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select email_verified from users where email = ?", Boolean.class, email)).isTrue();
        assertThat(jdbc.queryForObject(
                "select email_verified_at is not null from users where email = ?", Boolean.class, email)).isTrue();

        ResponseEntity<Map> login = login(email, PASSWORD);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String jwt = (String) login.getBody().get("token");
        assertThat(jwt).isNotBlank();

        ResponseEntity<String> protectedRoute = http.exchange("/api/v1/me/objectives",
                org.springframework.http.HttpMethod.GET, bearer(jwt), String.class);
        assertThat(protectedRoute.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verify_tokenCannotBeReused() throws Exception {
        String email = newEmail();
        register("Ana", email);
        String token = tokenFrom(lastMail());

        assertThat(verifyEmail(token).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> again = verifyEmail(token);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).containsEntry("code", "USED");
    }

    @Test
    void verify_expiredToken_isRejected_andAccountStaysUnverified() throws Exception {
        String email = newEmail();
        register("Ana", email);
        String token = tokenFrom(lastMail());
        jdbc.update("update email_verification_tokens set expires_at = now() - interval '1 minute' "
                + "where token_hash = ?", TokenUtils.sha256Hex(token));

        ResponseEntity<Map> res = verifyEmail(token);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("code", "EXPIRED");
        assertThat(jdbc.queryForObject("select email_verified from users where email = ?", Boolean.class, email)).isFalse();
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void verify_unknownOrMalformedToken_isInvalid() {
        ResponseEntity<Map> res = verifyEmail("0".repeat(64));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("code", "INVALID");

        assertThat(http.postForEntity("/api/v1/auth/verify-email", Map.of("token", ""), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- reenvío y límites ----------

    @Test
    void resend_respectsCooldown_thenIssuesNewWorkingLink_andCapsPerHour() throws Exception {
        String email = newEmail();
        register("Ana", email);
        verify(mailSender, timeout(5000).times(1)).send(any(MimeMessage.class));

        // Dentro del cooldown (60 s): respuesta idéntica pero NO se emite otro token ni otro correo
        assertThat(resend(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tokenCount(email)).isEqualTo(1);

        // Pasado el cooldown: se emite uno nuevo
        ageTokens(email);
        assertThat(resend(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(mailSender, timeout(5000).times(2)).send(any(MimeMessage.class));
        assertThat(tokenCount(email)).isEqualTo(2);

        // El nuevo enlace funciona
        String newToken = tokenFrom(lastMail());
        assertThat(verifyEmail(newToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void resend_isCappedPerHour() throws Exception {
        String email = newEmail();
        register("Ana", email); // 1
        for (int i = 0; i < 6; i++) {
            ageTokens(email);
            resend(email);
        }
        assertThat(tokenCount(email)).isEqualTo(5); // máximo 5 por hora (por defecto)
    }

    @Test
    void resend_forUnknownOrVerifiedEmail_returnsSameResponse_andSendsNothing() throws Exception {
        ResponseEntity<Map> unknown = resend("nadie-" + UUID.randomUUID() + "@example.com");
        ResponseEntity<Map> verified = resend("admin@englishlab.com");
        String pendingEmail = newEmail();
        register("Ana", pendingEmail);
        verify(mailSender, timeout(5000).times(1)).send(any(MimeMessage.class));
        ageTokens(pendingEmail);
        ResponseEntity<Map> pending = resend(pendingEmail);

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknown.getBody()).isEqualTo(verified.getBody()).isEqualTo(pending.getBody());
        assertThat(jdbc.queryForObject("select count(*) from email_verification_tokens t join users u on u.id = t.user_id "
                + "where u.email = 'admin@englishlab.com'", Integer.class)).isZero();
    }

    @Test
    void seededAccounts_migrateAsVerified() {
        // Las cuentas previas a V20 (seed) migran como verificadas y siguen pudiendo entrar.
        assertThat(jdbc.queryForObject("select count(*) from users where email_verified = false and id <= 12", Integer.class))
                .isZero();
    }

    // ---------- helpers ----------

    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResponseEntity<Map> register(String name, String email) {
        return http.postForEntity("/api/v1/auth/register",
                Map.of("name", name, "email", email, "password", PASSWORD), Map.class);
    }

    private ResponseEntity<Map> login(String email, String password) {
        return http.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", password), Map.class);
    }

    private ResponseEntity<Map> verifyEmail(String token) {
        return http.postForEntity("/api/v1/auth/verify-email", Map.of("token", token), Map.class);
    }

    private ResponseEntity<Map> resend(String email) {
        return http.postForEntity("/api/v1/auth/resend-verification", Map.of("email", email), Map.class);
    }

    private HttpEntity<Void> bearer(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return new HttpEntity<>(headers);
    }

    private int tokenCount(String email) {
        return jdbc.queryForObject("select count(*) from email_verification_tokens t join users u on u.id = t.user_id "
                + "where u.email = ?", Integer.class, email);
    }

    /** Retrasa 2 minutos la emisión de los tokens para saltar el cooldown sin esperar. */
    private void ageTokens(String email) {
        jdbc.update("update email_verification_tokens set created_at = created_at - interval '2 minutes' "
                + "where user_id = (select id from users where email = ?)", email);
    }

    private void awaitCount(String sql, int expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Integer n = jdbc.queryForObject(sql, Integer.class);
            if (n != null && n == expected) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(jdbc.queryForObject(sql, Integer.class)).isEqualTo(expected);
    }

    /** Último correo HTML enviado (el envío es asíncrono: se espera con timeout). */
    private MimeMessage lastMail() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).atLeastOnce()).send(captor.capture());
        return roundTrip(captor.getAllValues().get(captor.getAllValues().size() - 1));
    }

    /** Serializa y relee el mensaje para que el contenido se decodifique como lo vería un cliente de correo. */
    private static MimeMessage roundTrip(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(out.toByteArray()));
    }

    private static String part(Part part, String mime) throws Exception {
        if (part.isMimeType(mime)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String found = part(mp.getBodyPart(i), mime);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String tokenFrom(MimeMessage mail) throws Exception {
        Matcher m = TOKEN_IN_LINK.matcher(part(mail, "text/html"));
        assertThat(m.find()).as("el correo debe contener el enlace HTTPS de verificación").isTrue();
        return m.group(1);
    }
}
