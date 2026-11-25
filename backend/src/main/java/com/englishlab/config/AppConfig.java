package com.englishlab.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración central de propiedades tipadas y política CORS para el frontend.
 * Se restringen orígenes explícitos para mantener compatibilidad con {@code allowCredentials(true)}.
 */
@Configuration
@EnableConfigurationProperties({
        AppConfig.AuthProperties.class,
        AppConfig.MailProperties.class,
        AppConfig.JwtProperties.class,
        AppConfig.VerificationProperties.class,
        AppConfig.GoogleProperties.class,
        AppConfig.TwilioVerifyProperties.class
})
public class AppConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:3000",
                        "https://english-labeq15.tech",
                        "https://www.english-labeq15.tech"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true);
    }

    /** Autenticación; los logs de tokens/enlaces son solo para desarrollo local. */
    @ConfigurationProperties(prefix = "app.auth")
    public record AuthProperties(
            @DefaultValue("false") boolean logResetToken,
            @DefaultValue("false") boolean logVerificationLink
    ) {}

    /**
     * Verificación de correo. {@code frontendBaseUrl} debe ser HTTPS (se admite http solo en
     * localhost para desarrollo); el enlace del correo es {@code frontendBaseUrl + path + "?token=..."}.
     */
    @ConfigurationProperties(prefix = "app.verification")
    public record VerificationProperties(
            @DefaultValue("https://english-labeq15.tech") String frontendBaseUrl,
            @DefaultValue("/verificar-correo") String path,
            @DefaultValue("24h") Duration tokenTtl,
            @DefaultValue("60s") Duration resendCooldown,
            @DefaultValue("5") int maxSendsPerHour,
            @DefaultValue("7d") Duration unverifiedRetention
    ) {}

    /** Correo; con {@code enabled=false} no se exige un servidor SMTP. */
    @ConfigurationProperties(prefix = "app.mail")
    public record MailProperties(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("noreply@english-labeq15.tech") String from
    ) {}

    /**
     * Inicio de sesión / registro con Google. {@code clientId} es el OAuth Client ID (público) de
     * Google Cloud; el ID token solo se acepta si su {@code aud} coincide. Vacío = función desactivada.
     */
    @ConfigurationProperties(prefix = "app.google")
    public record GoogleProperties(
            @DefaultValue("") String clientId
    ) {}

    /**
     * JWT. El secreto viene SIEMPRE de la variable de entorno JWT_SECRET
     * (sin valor por defecto en el código ni en application.properties.example).
     */
    @ConfigurationProperties(prefix = "app.jwt")
    public record JwtProperties(
            String secret,
            @DefaultValue("3600000") long expirationMs
    ) {}

    /**
     * Recuperación de contraseña por SMS con Twilio Verify API. Las credenciales siempre
     * provienen de variables de entorno; con {@code enabled=false} no se realiza ninguna llamada.
     */
    @ConfigurationProperties(prefix = "twilio.verify")
    public record TwilioVerifyProperties(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String accountSid,
            @DefaultValue("") String authToken,
            @DefaultValue("") String serviceSid
    ) {}
}
