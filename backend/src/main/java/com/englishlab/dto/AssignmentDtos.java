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
            Long activityId
    ) {}

    public record AssignmentResponse(
            Long id,
            Long studentId,
            Long activityId,
            String status,
            Instant assignedAt,
            Instant completedAt
    ) {}

    /** Asignación vista por el alumno, con la actividad sin clave de respuesta. */
    public record StudentAssignmentResponse(
            Long assignmentId,
            Long activityId,
            String status,
            ActivityDtos.ActivityStudentDto activity
    ) {}
}
