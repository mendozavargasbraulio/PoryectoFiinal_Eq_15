package com.englishlab.dto;

import com.englishlab.grading.ActivityType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTOs de actividades y de sus categorías.
 */
public final class ActivityDtos {

    private ActivityDtos() {
    }

    /** Vista ADMIN/TEACHER: metadatos + contenido con la clave de respuesta. */
    public record ActivityAdminResponse(
            Long id,
            Long objectiveId,
            String type,
            String level,
            String skill,
            String title,
            Long categoryId,
            String categoryName,
            JsonNode content
    ) {}

    /** Opción de una actividad de opción múltiple. */
    public record ActivityOption(String id, String text) {}

    /** Alta/edición de actividad; el contenido se valida en el servicio según el tipo. */
    public record ActivityRequest(
            @NotNull(message = "El objetivo es obligatorio")
            Long objectiveId,

            @NotNull(message = "El tipo es obligatorio")
            ActivityType type,

            @NotBlank(message = "El nivel es obligatorio")
            @Pattern(regexp = "A1|A2|B1|B2", message = "El nivel debe ser A1, A2, B1 o B2")
            String level,

            @NotBlank(message = "La habilidad es obligatoria")
            @Size(max = 20, message = "La habilidad no puede superar los 20 caracteres")
            String skill,

            @NotBlank(message = "El título es obligatorio")
            @Size(max = 200, message = "El título no puede superar los 200 caracteres")
            String title,

            @NotNull(message = "El contenido es obligatorio")
            JsonNode content,

            Long categoryId
    ) {}

    /** Detalle de una actividad para el estudiante: nunca incluye answer, accepted ni explanation. */
    public record ActivityStudentDetailDto(
            Long id,
            String type,
            String level,
            String skill,
            String title,
            String prompt,
            List<ActivityOption> options
    ) {}

    /** Contenido de una actividad tal como lo ve el estudiante, sin la clave. */
    public record ActivityStudentDto(String prompt, List<ActivityOption> options) {}

    /** Resumen para la práctica libre; el detalle se pide a {@code GET /api/v1/me/activities/{id}}. */
    public record ActivityStudentSummaryDto(
            Long id,
            String title,
            String type,
            String level,
            String skill
    ) {}

    /** Alta/edición de una categoría de actividad. */
    public record CategoryRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
            String name,

            String description
    ) {}

    public record CategoryResponse(Long id, String name, String description, long activitiesCount) {}
}
