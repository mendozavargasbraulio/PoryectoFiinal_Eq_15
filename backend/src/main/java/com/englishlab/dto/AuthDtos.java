package com.englishlab.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTOs de autenticación: registro, login y recuperación de contraseña.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "El nombre es obligatorio")
            String name,

            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no es válido")
            String email,

            @NotBlank(message = "La contraseña es obligatoria")
            @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                    message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial"
            )
            String password
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

    public record UserResponse(Long id, String name, String email, String role) {}

    public record AuthResponse(String token, UserResponse user) {}
}
