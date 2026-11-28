package com.englishlab.controller;

import com.englishlab.dto.ActivityDtos.ActivityStudentDetailDto;
import com.englishlab.dto.ActivityDtos.ActivityStudentSummaryDto;
import com.englishlab.dto.AssignmentDtos.StudentAssignmentResponse;
import com.englishlab.dto.AttemptDtos.AttemptDetailResponse;
import com.englishlab.dto.AttemptDtos.AttemptRequest;
import com.englishlab.dto.AttemptDtos.AttemptResponse;
import com.englishlab.dto.ObjectiveDtos.ObjectiveStateResponse;
import com.englishlab.dto.ObjectiveDtos.PracticeSessionResponse;
import com.englishlab.dto.ObjectiveDtos.RecommendationResponse;
import com.englishlab.dto.PageResponse;
import com.englishlab.dto.UserDtos.StudentClassResponse;
import com.englishlab.security.JwtService.AuthenticatedUser;
import com.englishlab.service.ActivityService;
import com.englishlab.service.AssignmentService;
import com.englishlab.service.AttemptService;
import com.englishlab.service.ObjectiveStateService;
import com.englishlab.service.RecommendationService;
import com.englishlab.service.TeacherStudentService;
import com.englishlab.util.PageableFactory;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todo lo que hace el estudiante autenticado: objetivos y su estado, práctica libre,
 * recomendación, intentos y tareas asignadas. Solo rol STUDENT.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private static final Set<String> ACTIVITY_SORT = Set.of("id", "title", "type", "level");
    private static final Set<String> ASSIGNMENT_SORT = Set.of("id", "status", "assignedAt", "dueAt");

    private final ObjectiveStateService objectiveStateService;
    private final ActivityService activityService;
    private final RecommendationService recommendationService;
    private final AttemptService attemptService;
    private final AssignmentService assignmentService;
    private final TeacherStudentService teacherStudentService;

    public StudentController(
            ObjectiveStateService objectiveStateService,
            ActivityService activityService,
            RecommendationService recommendationService,
            AttemptService attemptService,
            AssignmentService assignmentService,
            TeacherStudentService teacherStudentService) {
        this.objectiveStateService = objectiveStateService;
        this.activityService = activityService;
        this.recommendationService = recommendationService;
        this.attemptService = attemptService;
        this.assignmentService = assignmentService;
        this.teacherStudentService = teacherStudentService;
    }

    @GetMapping("/me/objectives")
    public ResponseEntity<List<ObjectiveStateResponse>> objectives(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(objectiveStateService.myObjectives(principal.id()));
    }

    /** Práctica libre de un objetivo desbloqueado: resumen paginado, sin clave de respuesta. */
    @GetMapping("/me/objectives/{objectiveId}/activities")
    public ResponseEntity<PageResponse<ActivityStudentSummaryDto>> objectiveActivities(
            @PathVariable Long objectiveId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(objectiveStateService.objectiveActivities(
                principal.id(), objectiveId,
                PageableFactory.of(page, size, sort, ACTIVITY_SORT, "title")));
    }

    /**
     * Sesión de práctica sin repetición: el SERVIDOR elige las actividades (nunca intentadas
     * primero, luego las más antiguas). Solo ids; el detalle llega con {@code GET /me/activities/{id}}.
     */
    @GetMapping("/me/objectives/{objectiveId}/practice-session")
    public ResponseEntity<PracticeSessionResponse> practiceSession(
            @PathVariable Long objectiveId,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                objectiveStateService.practiceSession(principal.id(), objectiveId, size));
    }

    /** Vista pública de práctica de una actividad: sin clave de respuesta. */
    @GetMapping("/me/activities/{id}")
    public ResponseEntity<ActivityStudentDetailDto> activity(@PathVariable Long id) {
        return ResponseEntity.ok(activityService.studentActivity(id));
    }

    @GetMapping("/recommendations/next")
    public ResponseEntity<RecommendationResponse> nextRecommendation(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(recommendationService.next(principal.id()));
    }

    @PostMapping("/attempts")
    public ResponseEntity<AttemptResponse> submitAttempt(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AttemptRequest request) {
        return ResponseEntity.ok(attemptService.submit(principal.id(), request));
    }

    @GetMapping("/me/attempts/{id}")
    public ResponseEntity<AttemptDetailResponse> myAttempt(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(attemptService.getMyAttempt(principal.id(), id));
    }

    @GetMapping("/me/assignments")
    public ResponseEntity<PageResponse<StudentAssignmentResponse>> myAssignments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(assignmentService.myAssignments(principal.id(),
                PageableFactory.of(page, size, sort, ASSIGNMENT_SORT, "assignedAt")));
    }

    @GetMapping("/me/assignments/{assignmentId}/result")
    public ResponseEntity<AttemptDetailResponse> assignmentResult(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(attemptService.getAssignmentResult(principal.id(), assignmentId));
    }

    @GetMapping("/me/class")
    public ResponseEntity<StudentClassResponse> myClass(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(teacherStudentService.getStudentClass(principal.id()));
    }

    @PostMapping("/me/class/{teacherId}")
    public ResponseEntity<StudentClassResponse> joinClass(
            @PathVariable Long teacherId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(teacherStudentService.joinTeacher(principal.id(), teacherId));
    }

    @DeleteMapping("/me/class")
    public ResponseEntity<StudentClassResponse> leaveClass(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(teacherStudentService.leaveTeacher(principal.id()));
    }
}
