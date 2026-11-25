package com.englishlab.security;

import com.englishlab.config.AppConfig.GoogleProperties;
import com.englishlab.exception.AppExceptions.GoogleAuthUnavailableException;
import com.englishlab.exception.AppExceptions.InvalidCredentialsException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Valida el ID token de Google Identity Services: firma RS256 contra el JWKS de Google,
 * {@code iss} de Google, {@code aud} igual a nuestro Client ID y no expirado.
 * Devuelve los claims que usa English Lab; la decisión sobre {@code email_verified} es del llamador.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

    /** Datos del ID token que ya pasó todas las validaciones. */
    public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {}

    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(GoogleProperties props) {
        String clientId = props.clientId() == null ? "" : props.clientId().trim();
        this.decoder = clientId.isEmpty() ? null : buildDecoder(clientId);
    }

    /** Para pruebas: inyecta un decodificador propio (sin red). */
    GoogleTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    private static JwtDecoder buildDecoder(String clientId) {
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(60)),
                issuerValidator(),
                audienceValidator(clientId)));
        return nimbus;
    }

    static OAuth2TokenValidator<Jwt> issuerValidator() {
        return jwt -> {
            String iss = jwt.getClaimAsString("iss");
            return iss != null && ISSUERS.contains(iss)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emisor no válido", null));
        };
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiencia no válida", null));
    }

    public GoogleIdentity verify(String idToken) {
        if (decoder == null) {
            throw new GoogleAuthUnavailableException(
                    "El acceso con Google no está disponible en este momento.");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken.trim());
        } catch (JwtException e) {
            // Firma, expiración, iss/aud inválidos o JWKS inaccesible: nunca se detalla al cliente.
            log.warn("ID token de Google rechazado: {}", e.getMessage());
            throw new InvalidCredentialsException("No se pudo verificar tu cuenta de Google.");
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new InvalidCredentialsException("No se pudo verificar tu cuenta de Google.");
        }
        return new GoogleIdentity(subject, email, isTrue(jwt.getClaim("email_verified")), jwt.getClaimAsString("name"));
    }

    /** Google envía un booleano, pero algunos emisores/tokens antiguos lo mandan como cadena. */
    private static boolean isTrue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }
}
