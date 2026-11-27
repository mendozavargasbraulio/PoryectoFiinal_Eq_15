package com.englishlab.service;

import com.englishlab.domain.MasteryConfig;
import com.englishlab.domain.MasteryConfig.ObjectiveState;
import com.englishlab.domain.MasteryService;
import com.englishlab.domain.MasteryService.ObjectiveDomain;
import com.englishlab.dto.ActivityDtos.ActivityStudentDetailDto;
import com.englishlab.dto.ActivityDtos.ActivityStudentDto;
import com.englishlab.dto.AssignmentDtos.AssignmentCreateRequest;
import com.englishlab.dto.AssignmentDtos.AssignmentResponse;
import com.englishlab.dto.AssignmentDtos.StudentAssignmentResponse;
import com.englishlab.dto.ObjectiveDtos.ProgressResponse;
import com.englishlab.dto.PageResponse;
import com.englishlab.exception.AppExceptions.BadRequestException;
import com.englishlab.exception.AppExceptions.NotFoundException;
import com.englishlab.model.entity.Activity;
import com.englishlab.model.entity.Assignment;
import com.englishlab.model.entity.Assignment.AssignmentStatus;
import com.englishlab.model.entity.LearnerSkillState;
import com.englishlab.model.entity.Objective;
import com.englishlab.model.entity.User;
import com.englishlab.repository.ActivityRepository;
import com.englishlab.repository.AssignmentRepository;
import com.englishlab.repository.LearnerSkillStateRepository;
import com.englishlab.repository.ObjectiveRepository;
import com.englishlab.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignaciones: creación por el TEACHER, listado del alumno y progreso por alumno.
 */
@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ObjectiveRepository objectiveRepository;
    private final LearnerSkillStateRepository learnerSkillStateRepository;
    private final ActivityContentService activityContentService;
    private final MasteryService masteryService;

    public AssignmentService(
            AssignmentRepository assignmentRepository,
            UserRepository userRepository,
            ActivityRepository activityRepository,
            ObjectiveRepository objectiveRepository,
            LearnerSkillStateRepository learnerSkillStateRepository,
            ActivityContentService activityContentService,
            MasteryService masteryService) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.objectiveRepository = objectiveRepository;
        this.learnerSkillStateRepository = learnerSkillStateRepository;
        this.activityContentService = activityContentService;
        this.masteryService = masteryService;
    }

    @Transactional
    public AssignmentResponse create(Long teacherId, AssignmentCreateRequest request) {
        User student = userRepository.findById(request.studentId())
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado"));
        if (student.getTeacher() == null || !student.getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("El estudiante no está vinculado a este profesor");
        }
        Activity activity = activityRepository.findById(request.activityId())
                .orElseThrow(() -> new NotFoundException("Actividad no encontrada"));

        Assignment assignment = new Assignment();
        assignment.setTeacher(userRepository.getReferenceById(teacherId));
        assignment.setStudent(student);
        assignment.setActivity(activity);
        assignment.setStatus(AssignmentStatus.PENDING);
        assignment.setAssignedAt(Instant.now());
        assignment.setDueAt(request.dueAt());
        assignment = assignmentRepository.save(assignment);
        return toResponse(assignment);
    }

    @Transactional
    public AssignmentResponse updateDueDate(Long teacherId, Long assignmentId, Instant dueAt) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Asignación no encontrada"));
        if (!assignment.getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("No tienes permiso para modificar esta asignación");
        }
        assignment.setDueAt(dueAt);
        assignment = assignmentRepository.save(assignment);
        return toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> teacherAssignments(Long teacherId, String status, Pageable pageable) {
        Page<Assignment> page;
        if (status != null && !status.isBlank()) {
            page = assignmentRepository.findByTeacherIdAndStatus(teacherId, parseStatus(status), pageable);
        } else {
            page = assignmentRepository.findByTeacherId(teacherId, pageable);
        }
        List<AssignmentResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentAssignmentResponse> myAssignments(Long studentId, Pageable pageable) {
        Page<Assignment> page = assignmentRepository.findByStudentId(studentId, pageable);
        List<StudentAssignmentResponse> content = page.getContent().stream()
                .map(this::toStudentResponse)
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<ProgressResponse> progress(Long teacherId, Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado"));
        if (student.getTeacher() == null || !student.getTeacher().getId().equals(teacherId)) {
            throw new AccessDeniedException("El estudiante no está vinculado a este profesor");
        }

        List<Objective> objectives = objectiveRepository.findAllWithPrerequisites();
        Map<Long, LearnerSkillState> states = learnerSkillStateRepository.findByUserId(studentId).stream()
                .collect(Collectors.toMap(LearnerSkillState::getObjectiveId, s -> s));

        List<ObjectiveDomain> domains = objectives.stream().map(o -> {
            LearnerSkillState state = states.get(o.getId());
            double masteryValue = state == null
                    ? MasteryConfig.DEFAULT_MASTERY
                    : state.getMastery().doubleValue();
            Set<Long> prerequisites = o.getPrerequisiteLinks().stream()
                    .map(link -> link.getPrerequisite().getId())
                    .collect(Collectors.toSet());
            return new ObjectiveDomain(
                    o.getId(), o.getCode(), o.getName(), o.getCefrLevel(), o.getSkill(), masteryValue, prerequisites);
        }).toList();

        Map<Long, Double> masteryMap = domains.stream()
                .collect(Collectors.toMap(ObjectiveDomain::id, ObjectiveDomain::mastery));
        Optional<Long> recommended = masteryService.recommend(domains, masteryMap);

        return domains.stream()
                .map(d -> {
                    LearnerSkillState skillState = states.get(d.id());
                    boolean allPrereqsMastered = d.prerequisites().stream()
                            .allMatch(p -> masteryMap.getOrDefault(p, MasteryConfig.DEFAULT_MASTERY)
                                    >= MasteryConfig.MASTERED_THRESHOLD);
                    ObjectiveState objState = masteryService.computeState(d.mastery(), allPrereqsMastered);
                    if (recommended.isPresent() && recommended.get().equals(d.id())) {
                        objState = ObjectiveState.RECOMMENDED;
                    }
                    return new ProgressResponse(
                            d.id(),
                            d.code(),
                            d.name(),
                            d.cefrLevel(),
                            d.skill(),
                            masteryService.roundMastery(d.mastery()),
                            skillState == null ? 0 : skillState.getAttemptsCount(),
                            skillState == null ? 0 : skillState.getCorrectCount(),
                            objState);
                })
                .toList();
    }

    private AssignmentStatus parseStatus(String status) {
        try {
            return AssignmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Estado de asignación inválido: " + status);
        }
    }

    private AssignmentResponse toResponse(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getStudent().getId(),
                assignment.getStudent().getName(),
                assignment.getActivity().getId(),
                assignment.getActivity().getTitle(),
                assignment.getStatus().name(),
                assignment.getAssignedAt(),
                assignment.getDueAt(),
                assignment.getCompletedAt());
    }

    private StudentAssignmentResponse toStudentResponse(Assignment assignment) {
        Activity activity = assignment.getActivity();
        ActivityStudentDto content = activityContentService.toStudentDto(activity.getType(), activity.getContent());
        ActivityStudentDetailDto activityDetail = new ActivityStudentDetailDto(
                activity.getId(),
                activity.getType(),
                activity.getLevel(),
                activity.getSkill(),
                activity.getTitle(),
                content.prompt(),
                content.options());
        return new StudentAssignmentResponse(
                assignment.getId(),
                activity.getId(),
                assignment.getStatus().name(),
                assignment.getAssignedAt(),
                assignment.getDueAt(),
                assignment.getCompletedAt(),
                assignment.getTeacher() == null ? null : assignment.getTeacher().getName(),
                activityDetail);
    }
}
