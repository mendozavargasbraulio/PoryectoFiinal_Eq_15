package com.englishlab.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * DTOs de autenticación: registro, login, verificación de correo y recuperación de contraseña.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
            String name,

            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no es válido")
            @Size(max = 255, message = "El email no puede superar los 255 caracteres")
            String email,

            @NotBlank(message = "La contraseña es obligatoria")
            @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                    message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial"
            )
            String password,

            @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
            String phone
    ) {}

    public record LoginRequest(
            @NotBlank(message = "El email es obligatorio")
            String email,

            @NotBlank(message = "La contraseña es obligatoria")
            String password
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no es válido")
            String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "El token es obligatorio")
            String token,

            @NotBlank(message = "La nueva contraseña es obligatoria")
            @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                    message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial"
            )
            String newPassword
    ) {}

    public record ForgotPasswordSmsRequest(
            @NotBlank(message = "El teléfono es obligatorio")
            @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
            String phone
    ) {}

    public record ResetPasswordSmsRequest(
            @NotBlank(message = "El teléfono es obligatorio")
            @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
            String phone,

            @NotBlank(message = "El código es obligatorio")
            @Pattern(regexp = "^\\d{4,10}$", message = "El código debe contener solo dígitos")
            String code,

            @NotBlank(message = "La nueva contraseña es obligatoria")
            @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                    message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial"
            )
            String newPassword
    ) {}

    /** Reenvío del correo de verificación; la respuesta es idéntica exista o no la cuenta. */
    public record ResendVerificationRequest(
            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no es válido")
            @Size(max = 255, message = "El email no puede superar los 255 caracteres")
            String email
    ) {}

    public record VerifyEmailRequest(
            @NotBlank(message = "El token es obligatorio")
            @Size(max = 128, message = "Token inválido")
            String token
    ) {}

    /** ID token (JWT) que entrega Google Identity Services en el campo {@code credential}. */
    public record GoogleSignInRequest(
            @NotBlank(message = "La credencial de Google es obligatoria")
            @Size(max = 8192, message = "Credencial inválida")
            String credential
    ) {}

    /**
     * Resultado del acceso con Google: {@code session} si ya hay sesión (HTTP 200) o
     * {@code pending} si la cuenta queda pendiente de verificar por correo (HTTP 202).
     */
    public record GoogleSignInResult(AuthResponse session, MessageResponse pending) {}

    /** Respuesta genérica (registro y reenvío): no distingue cuentas nuevas de existentes. */
    public record MessageResponse(String message) {}

    public record UserResponse(Long id, String name, String email, String role) {}

    public record UserProfileResponse(
            Long id,
            String name,
            String email,
            String phone,
            boolean emailVerified,
            Instant createdAt,
            String teacherName,
            String teacherEmail
    ) {}

    public record AuthResponse(String token, UserResponse user) {}
}
