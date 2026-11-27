package com.englishlab.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * DTOs de asignaciones de actividades a estudiantes.
 */
public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record AssignmentCreateRequest(
            @NotNull(message = "El alumno es obligatorio")
            Long studentId,

            @NotNull(message = "La actividad es obligatoria")
            Long activityId,

            Instant dueAt
    ) {}

    public record AssignmentUpdateRequest(
            Instant dueAt
    ) {}

    public record AssignmentResponse(
            Long id,
            Long studentId,
            String studentName,
            Long activityId,
            String activityTitle,
            String status,
            Instant assignedAt,
            Instant dueAt,
            Instant completedAt
    ) {}

    /** Asignación vista por el alumno, con metadatos y actividad sin clave de respuesta. */
    public record StudentAssignmentResponse(
            Long assignmentId,
            Long activityId,
            String status,
            Instant assignedAt,
            Instant dueAt,
            Instant completedAt,
            String teacherName,
            ActivityDtos.ActivityStudentDetailDto activity
    ) {}
}
