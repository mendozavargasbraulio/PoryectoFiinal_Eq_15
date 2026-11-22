package com.englishlab.dto;

import com.englishlab.domain.MasteryConfig.AttemptMode;
import com.englishlab.dto.ActivityDtos.ActivityOption;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

/**
 * DTOs de intentos de resolución.
 */
public final class AttemptDtos {

    private AttemptDtos() {
    }

    /**
     * Petición de un intento. En modo CHALLENGE el answer puede venir vacío (timeout) y no se
     * llama al calificador.
     */
    @RequiredAnswerInNormalMode
    public record AttemptRequest(
            @NotNull(message = "La actividad es obligatoria")
            Long activityId,

            String answer,

            Long assignmentId,

            @Pattern(regexp = AttemptMode.PATTERN, message = "El modo solo puede ser NORMAL o CHALLENGE")
            String mode
    ) {

        public AttemptRequest {
            mode = (mode == null || mode.isBlank()) ? AttemptMode.DEFAULT_MODE : mode;
        }

        /** Compatibilidad: sin modo explícito equivale a NORMAL. */
        public AttemptRequest(Long activityId, String answer, Long assignmentId) {
            this(activityId, answer, assignmentId, null);
        }
    }

    /** Resultado de un intento; solución y explicación solo se envían después de calificar. */
    public record AttemptResponse(
            Long attemptId,
            boolean correct,
            double score,
            String solution,
            String explanation,
            double mastery
    ) {}

    /** Detalle de un intento ya evaluado (propio del alumno), para revisar resultados. */
    public record AttemptDetailResponse(
            Long attemptId,
            Long activityId,
            String activityTitle,
            String activityType,
            List<ActivityOption> options,
            String answer,
            boolean correct,
            double score,
            String solution,
            String explanation,
            double mastery,
            Instant attemptedAt
    ) {}
}
