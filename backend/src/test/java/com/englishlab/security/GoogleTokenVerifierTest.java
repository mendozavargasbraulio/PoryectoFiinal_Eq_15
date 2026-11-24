package com.englishlab.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishlab.config.AppConfig.GoogleProperties;
import com.englishlab.exception.AppExceptions.GoogleAuthUnavailableException;
import com.englishlab.exception.AppExceptions.InvalidCredentialsException;
import com.englishlab.security.GoogleTokenVerifier.GoogleIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/** Claims y validadores del ID token de Google (sin red: el decodificador se simula). */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "client-123.apps.googleusercontent.com";

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(claims)).build();
    }

    @Test
    void withoutClientId_googleSignInIsUnavailable() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier(new GoogleProperties(""));
        assertThatThrownBy(() -> verifier.verify("x")).isInstanceOf(GoogleAuthUnavailableException.class);
    }

    @Test
    void issuerValidator_acceptsOnlyGoogleIssuers() {
        assertThat(GoogleTokenVerifier.issuerValidator().validate(jwt(Map.of("iss", "https://accounts.google.com"))).hasErrors()).isFalse();
        assertThat(GoogleTokenVerifier.issuerValidator().validate(jwt(Map.of("iss", "accounts.google.com"))).hasErrors()).isFalse();
        assertThat(GoogleTokenVerifier.issuerValidator().validate(jwt(Map.of("iss", "https://evil.example"))).hasErrors()).isTrue();
    }

    @Test
    void audienceValidator_requiresOurClientId() {
        var validator = GoogleTokenVerifier.audienceValidator(CLIENT_ID);
        assertThat(validator.validate(jwt(Map.of("aud", List.of(CLIENT_ID)))).hasErrors()).isFalse();
        assertThat(validator.validate(jwt(Map.of("aud", List.of("otra-app")))).hasErrors()).isTrue();
    }

    @Test
    void verify_mapsClaims_includingEmailVerifiedAsBooleanOrString() {
        GoogleTokenVerifier ok = new GoogleTokenVerifier(t -> jwt(Map.of(
                "sub", "42", "email", "ana@gmail.com", "email_verified", true, "name", "Ana")));
        GoogleIdentity id = ok.verify("tok");
        assertThat(id.subject()).isEqualTo("42");
        assertThat(id.emailVerified()).isTrue();

        GoogleTokenVerifier asString = new GoogleTokenVerifier(t -> jwt(Map.of(
                "sub", "42", "email", "ana@gmail.com", "email_verified", "true")));
        assertThat(asString.verify("tok").emailVerified()).isTrue();

        GoogleTokenVerifier notVerified = new GoogleTokenVerifier(t -> jwt(Map.of(
                "sub", "42", "email", "ana@gmail.com", "email_verified", false)));
        assertThat(notVerified.verify("tok").emailVerified()).isFalse();

        GoogleTokenVerifier missing = new GoogleTokenVerifier(t -> jwt(Map.of("sub", "42", "email", "ana@gmail.com")));
        assertThat(missing.verify("tok").emailVerified()).isFalse();
    }

    @Test
    void verify_rejectsInvalidSignatureOrMissingClaims() {
        GoogleTokenVerifier bad = new GoogleTokenVerifier(t -> {
            throw new JwtException("firma inválida");
        });
        assertThatThrownBy(() -> bad.verify("tok")).isInstanceOf(InvalidCredentialsException.class);

        GoogleTokenVerifier noEmail = new GoogleTokenVerifier(t -> jwt(Map.of("sub", "42")));
        assertThatThrownBy(() -> noEmail.verify("tok")).isInstanceOf(InvalidCredentialsException.class);
    }
}
