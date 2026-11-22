package com.englishlab.dto;

import com.englishlab.domain.MasteryConfig.ObjectiveState;
import java.util.List;

/**
 * DTOs de objetivos de aprendizaje, progreso del alumno y recomendación.
 */
public final class ObjectiveDtos {

    private ObjectiveDtos() {
    }

    public record ObjectiveResponse(
            Long id,
            String code,
            String name,
            String cefrLevel,
            String skill
    ) {}

    /**
     * Estado de un objetivo para el alumno autenticado.
     *
     * @param previousMastery dominio antes del último intento; null = sin sesiones previas
     */
    public record ObjectiveStateResponse(
            Long objectiveId,
            String code,
            String name,
            String cefrLevel,
            String skill,
            double mastery,
            Double previousMastery,
            ObjectiveState state
    ) {}

    /** Sesión de práctica: ids de actividad en orden; el detalle se pide aparte, sin clave. */
    public record PracticeSessionResponse(List<Long> activityIds) {}

    /** Dominio de un alumno en un objetivo (vista del TEACHER). */
    public record ProgressResponse(
            Long objectiveId,
            String code,
            String name,
            String cefrLevel,
            String skill,
            double mastery
    ) {}

    /**
     * Recomendación de la siguiente actividad. En el caso normal {@code objectiveId},
     * {@code activityId} y {@code reason} vienen informados; si todo está dominado, quedan en
     * {@code null} y {@code message} explica la situación.
     */
    public record RecommendationResponse(
            Long objectiveId,
            Long activityId,
            String reason,
            String message
    ) {}
}
