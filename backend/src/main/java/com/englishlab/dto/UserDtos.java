package com.englishlab.dto;

import com.englishlab.model.entity.Role.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * DTOs de la gestión de usuarios por el ADMIN.
 */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * Alta/edición de usuario. La contraseña es obligatoria al crear y opcional al editar
     * (en blanco conserva la actual); {@code teacherId} debe referenciar a un TEACHER.
     */
    public record UserAdminRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
            String name,

            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no es válido")
            @Size(max = 255, message = "El email no puede superar los 255 caracteres")
            String email,

            @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
            @Pattern(
                    regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                    message = "La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial"
            )
            String password,

            @NotNull(message = "El rol es obligatorio")
            RoleName role,

            Long teacherId,

            @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
            String phone
    ) {}

    /** Vista ADMIN de un usuario; nunca expone el hash de la contraseña. */
    public record UserAdminResponse(
            Long id,
            String name,
            String email,
            String role,
            Long teacherId,
            String teacherName,
            String phone,
            Instant createdAt
    ) {}

    /** Vinculación de un estudiante a un TEACHER por email. */
    public record AssignStudentRequest(
            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no es válido")
            String email
    ) {}

    public record StudentResponse(Long id, String name, String email, Instant createdAt) {}

    /** Resumen paginado del progreso y actividades de cada estudiante del TEACHER. */
    public record StudentOverviewResponse(
            Long id,
            String name,
            String email,
            double averageMastery,
            int masteredObjectives,
            int totalObjectives,
            int totalAttempts,
            long completedAssignments,
            long pendingAssignments
    ) {}
}
