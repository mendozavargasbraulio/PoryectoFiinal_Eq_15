package com.englishlab.service;

import com.englishlab.domain.MasteryConfig;
import com.englishlab.domain.MasteryConfig.AttemptMode;
import com.englishlab.domain.MasteryService;
import com.englishlab.dto.ActivityDtos.ActivityStudentDto;
import com.englishlab.dto.AttemptDtos.AttemptDetailResponse;
import com.englishlab.dto.AttemptDtos.AttemptRequest;
import com.englishlab.dto.AttemptDtos.AttemptResponse;
import com.englishlab.exception.AppExceptions.BusinessRuleException;
import com.englishlab.exception.AppExceptions.NotFoundException;
import com.englishlab.grading.ActivityType;
import com.englishlab.grading.GradingService;
import com.englishlab.grading.GradingService.GradingResult;
import com.englishlab.model.entity.Activity;
import com.englishlab.model.entity.Assignment;
import com.englishlab.model.entity.Assignment.AssignmentStatus;
import com.englishlab.model.entity.Attempt;
import com.englishlab.model.entity.LearnerSkillState;
import com.englishlab.model.entity.Objective;
import com.englishlab.repository.ActivityRepository;
import com.englishlab.repository.AssignmentRepository;
import com.englishlab.repository.AttemptRepository;
import com.englishlab.repository.LearnerSkillStateRepository;
import com.englishlab.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de intentos (STUDENT). En una única transacción: califica, guarda el intento,
 * actualiza el dominio (upsert) y completa la asignación con el primer acierto.
 *
 * <p>Modo NORMAL: correcta 1.0, incorrecta 0.0. Modo CHALLENGE: correcta 1.0, incorrecta
 * -0.5 (penaliza adivinar) y una pregunta sin responder vale 0.0 sin pasar por el calificador.
 */
@Service
public class AttemptService {

    private final ActivityRepository activityRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final LearnerSkillStateRepository learnerSkillStateRepository;
    private final UserRepository userRepository;
    private final GradingService gradingService;
    private final MasteryService masteryService;
    private final ActivityContentService activityContentService;

    public AttemptService(
            ActivityRepository activityRepository,
            AssignmentRepository assignmentRepository,
            AttemptRepository attemptRepository,
            LearnerSkillStateRepository learnerSkillStateRepository,
            UserRepository userRepository,
            GradingService gradingService,
            MasteryService masteryService,
            ActivityContentService activityContentService) {
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.learnerSkillStateRepository = learnerSkillStateRepository;
        this.userRepository = userRepository;
        this.gradingService = gradingService;
        this.masteryService = masteryService;
        this.activityContentService = activityContentService;
    }

    @Transactional
    public AttemptResponse submit(Long userId, AttemptRequest request) {
        Activity activity = activityRepository.findById(request.activityId())
                .orElseThrow(() -> new NotFoundException("Actividad no encontrada"));

        Assignment assignment = null;
        if (request.assignmentId() != null) {
            assignment = assignmentRepository.findById(request.assignmentId())
                    .orElseThrow(() -> new NotFoundException("Asignación no encontrada"));
            if (!assignment.getStudent().getId().equals(userId)) {
                throw new AccessDeniedException("La asignación no pertenece al estudiante");
            }
            if (assignment.getStatus() != AssignmentStatus.PENDING) {
                throw new BusinessRuleException("La asignación ya fue completada");
            }
            if (!assignment.getActivity().getId().equals(request.activityId())) {
                throw new BusinessRuleException("La actividad no corresponde a la asignación");
            }
        }

        ActivityType type = ActivityType.valueOf(activity.getType());
        boolean challenge = AttemptMode.isChallenge(request.mode());
        // user_answer es NOT NULL: la pregunta sin responder se guarda como cadena vacía.
        String answer = request.answer() == null ? "" : request.answer();

        boolean correct;
        double score;
        if (challenge && answer.isBlank()) {
            // Timeout en modo RETO: no se llama al calificador ni se penaliza.
            correct = false;
            score = MasteryConfig.CHALLENGE_UNANSWERED_SCORE;
        } else {
            GradingResult result = gradingService.grade(type, activity.getContent(), answer);
            correct = result.correct();
            score = challenge
                    ? (correct ? MasteryConfig.CHALLENGE_CORRECT_SCORE : MasteryConfig.CHALLENGE_WRONG_SCORE)
                    : result.score();
        }

        Attempt attempt = new Attempt();
        attempt.setUser(userRepository.getReferenceById(userId));
        attempt.setActivity(activity);
        attempt.setAssignment(assignment);
        attempt.setUserAnswer(answer);
        attempt.setCorrect(correct);
        attempt.setScore(BigDecimal.valueOf(score));
        attempt.setAttemptedAt(Instant.now());
        attempt = attemptRepository.save(attempt);

        Objective objective = activity.getObjective();
        // El score entra tal cual en el dominio: en RETO un fallo (-0.5) puede bajarlo.
        double mastery = applyDomain(userId, objective.getId(), score, correct);

        if (correct && assignment != null && assignment.getStatus() == AssignmentStatus.PENDING) {
            assignment.setStatus(AssignmentStatus.COMPLETED);
            assignment.setCompletedAt(Instant.now());
        }

        JsonNode content = activityContentService.parse(activity.getContent());
        String solution = activityContentService.extractSolution(type, content);
        String explanation = activityContentService.extractExplanation(content);

        return new AttemptResponse(attempt.getId(), correct, score, solution, explanation, mastery);
    }

    /** Detalle de un intento ya evaluado perteneciente al estudiante autenticado. */
    @Transactional(readOnly = true)
    public AttemptDetailResponse getMyAttempt(Long userId, Long attemptId) {
        Attempt attempt = attemptRepository.findWithDetailsById(attemptId)
                .orElseThrow(() -> new NotFoundException("Intento no encontrado"));
        if (!attempt.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("El intento no pertenece al estudiante");
        }
        return toDetailResponse(userId, attempt);
    }

    /** Resultado de una asignación ya completada por el estudiante autenticado. */
    @Transactional(readOnly = true)
    public AttemptDetailResponse getAssignmentResult(Long userId, Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Asignación no encontrada"));
        if (!assignment.getStudent().getId().equals(userId)) {
            throw new AccessDeniedException("La asignación no pertenece al estudiante");
        }
        if (assignment.getStatus() != AssignmentStatus.COMPLETED) {
            throw new BusinessRuleException("La asignación todavía está pendiente");
        }
        Attempt attempt = attemptRepository
                .findFirstByAssignmentIdAndUserIdOrderByAttemptedAtDescIdDesc(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("No se encontró el resultado de esta asignación"));
        return toDetailResponse(userId, attempt);
    }

    private AttemptDetailResponse toDetailResponse(Long userId, Attempt attempt) {
        Activity activity = attempt.getActivity();
        ActivityType type = ActivityType.valueOf(activity.getType());
        ActivityStudentDto studentView = activityContentService.toStudentDto(activity.getType(), activity.getContent());
        JsonNode content = activityContentService.parse(activity.getContent());
        String solution = activityContentService.extractSolution(type, content);
        String explanation = activityContentService.extractExplanation(content);
        double mastery = learnerSkillStateRepository
                .findByUserIdAndObjectiveId(userId, activity.getObjective().getId())
                .map(state -> state.getMastery().doubleValue())
                .orElse(MasteryConfig.DEFAULT_MASTERY);

        return new AttemptDetailResponse(
                attempt.getId(),
                activity.getId(),
                activity.getTitle(),
                activity.getType(),
                studentView.options(),
                attempt.getUserAnswer(),
                attempt.isCorrect(),
                attempt.getScore().doubleValue(),
                solution,
                explanation,
                masteryService.roundMastery(mastery),
                attempt.getAttemptedAt());
    }

    /** Calcula el nuevo dominio (recent + historical) y hace el upsert de learner_skill_state. */
    private double applyDomain(Long userId, Long objectiveId, double score, boolean correct) {
        LearnerSkillState state = learnerSkillStateRepository.findByUserIdAndObjectiveId(userId, objectiveId)
                .orElse(null);
        // Dominio efectivo antes de este intento: el guardado o, sin fila, el default.
        double previous = state == null ? MasteryConfig.DEFAULT_MASTERY : state.getMastery().doubleValue();
        double historical = previous;

        List<BigDecimal> recentScores = attemptRepository.findRecentScores(
                userId, objectiveId, PageRequest.of(0, MasteryConfig.RECENT_WINDOW));
        double recent = recentScores.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        double mastery = masteryService.roundMastery(masteryService.computeMastery(recent, historical));

        if (state == null) {
            state = new LearnerSkillState();
            state.setUserId(userId);
            state.setObjectiveId(objectiveId);
            state.setAttemptsCount(0);
            state.setCorrectCount(0);
        }
        // Se guarda ANTES de sobrescribir mastery: el DTO lo usa para dibujar el
        // cambio del ultimo intento (null = aun no habia sesiones previas).
        state.setPreviousMastery(BigDecimal.valueOf(masteryService.roundMastery(previous)));
        state.setMastery(BigDecimal.valueOf(mastery));
        state.setAttemptsCount(state.getAttemptsCount() + 1);
        if (correct) {
            state.setCorrectCount(state.getCorrectCount() + 1);
        }
        state.setLastAttemptAt(Instant.now());
        learnerSkillStateRepository.save(state);

        return mastery;
    }
}
