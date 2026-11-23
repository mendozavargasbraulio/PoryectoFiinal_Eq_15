package com.englishlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de la aplicación en un solo lugar: propiedades tipadas ({@code app.*}) y CORS
 * para el frontend React + Vite en desarrollo.
 *
 * <p>No usar {@code allowedOriginPatterns("*")} junto con {@code allowCredentials(true)}.
 */
@Configuration
@EnableConfigurationProperties({
        AppConfig.AuthProperties.class,
        AppConfig.MailProperties.class,
        AppConfig.JwtProperties.class
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

    /** Autenticación; el log del token de recuperación es solo para desarrollo local. */
    @ConfigurationProperties(prefix = "app.auth")
    public record AuthProperties(
            @DefaultValue("false") boolean logResetToken
    ) {}

    /** Correo; con {@code enabled=false} no se exige un servidor SMTP. */
    @ConfigurationProperties(prefix = "app.mail")
    public record MailProperties(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("no-reply@englishlab.local") String from
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
}
