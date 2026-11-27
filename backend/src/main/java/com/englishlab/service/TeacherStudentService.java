package com.englishlab.service;

import com.englishlab.domain.MasteryConfig;
import com.englishlab.domain.MasteryService;
import com.englishlab.dto.PageResponse;
import com.englishlab.dto.UserDtos.StudentClassResponse;
import com.englishlab.dto.UserDtos.StudentOverviewResponse;
import com.englishlab.dto.UserDtos.StudentResponse;
import com.englishlab.dto.UserDtos.TeacherSummaryResponse;
import com.englishlab.exception.AppExceptions.BusinessRuleException;
import com.englishlab.exception.AppExceptions.NotFoundException;
import com.englishlab.model.entity.Assignment.AssignmentStatus;
import com.englishlab.model.entity.LearnerSkillState;
import com.englishlab.model.entity.Role.RoleName;
import com.englishlab.model.entity.User;
import com.englishlab.repository.AssignmentRepository;
import com.englishlab.repository.LearnerSkillStateRepository;
import com.englishlab.repository.ObjectiveRepository;
import com.englishlab.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de estudiantes por parte del TEACHER: vincula un estudiante a su
 * {@code teacher_id} y lista únicamente sus propios alumnos (con resumen sin N+1).
 */
@Service
public class TeacherStudentService {

    private final UserRepository userRepository;
    private final ObjectiveRepository objectiveRepository;
    private final LearnerSkillStateRepository learnerSkillStateRepository;
    private final AssignmentRepository assignmentRepository;
    private final MasteryService masteryService;

    public TeacherStudentService(
            UserRepository userRepository,
            ObjectiveRepository objectiveRepository,
            LearnerSkillStateRepository learnerSkillStateRepository,
            AssignmentRepository assignmentRepository,
            MasteryService masteryService) {
        this.userRepository = userRepository;
        this.objectiveRepository = objectiveRepository;
        this.learnerSkillStateRepository = learnerSkillStateRepository;
        this.assignmentRepository = assignmentRepository;
        this.masteryService = masteryService;
    }

    @Transactional
    public StudentResponse assign(Long teacherId, String email) {
        User student = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado"));
        if (student.getRole().getName() != RoleName.STUDENT) {
            throw new BusinessRuleException("El usuario no es un estudiante");
        }
        if (student.getTeacher() != null) {
            throw new BusinessRuleException("El estudiante ya tiene un profesor asignado");
        }
        student.setTeacher(userRepository.getReferenceById(teacherId));
        return toResponse(student);
    }

    @Transactional(readOnly = true)
    public StudentClassResponse getStudentClass(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado"));
        return buildClassResponse(student);
    }

    @Transactional
    public StudentClassResponse joinTeacher(Long studentId, Long teacherId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado"));
        if (student.getTeacher() != null) {
            throw new BusinessRuleException("Ya estás inscrito con un profesor. Sal de la clase actual para cambiar.");
        }
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("Profesor no encontrado"));
        if (teacher.getRole().getName() != RoleName.TEACHER) {
            throw new BusinessRuleException("El usuario seleccionado no es un profesor");
        }
        student.setTeacher(teacher);
        userRepository.save(student);
        return buildClassResponse(student);
    }

    @Transactional
    public StudentClassResponse leaveTeacher(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado"));
        student.setTeacher(null);
        userRepository.save(student);
        return buildClassResponse(student);
    }

    private StudentClassResponse buildClassResponse(User student) {
        User teacher = student.getTeacher();
        if (teacher != null) {
            return new StudentClassResponse(
                    new TeacherSummaryResponse(teacher.getId(), teacher.getName(), teacher.getEmail()),
                    List.of());
        }
        List<TeacherSummaryResponse> teachers = userRepository.findByRole_NameOrderByNameAsc(RoleName.TEACHER)
                .stream()
                .map(t -> new TeacherSummaryResponse(t.getId(), t.getName(), t.getEmail()))
                .toList();
        return new StudentClassResponse(null, teachers);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> list(Long teacherId, String text, Pageable pageable) {
        Page<User> page = userRepository.findAll(byTeacherAndText(teacherId, text), pageable);
        List<StudentResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Resumen paginado de progreso y asignaciones por alumno del profesor.
     * Ejecuta consultas agregadas por lote (sin N+1).
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentOverviewResponse> listOverview(Long teacherId, String text, Pageable pageable) {
        Page<User> page = userRepository.findAll(byTeacherAndText(teacherId, text), pageable);
        List<User> students = page.getContent();
        if (students.isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }

        int totalObjectives = Math.toIntExact(objectiveRepository.count());
        List<Long> studentIds = students.stream().map(User::getId).toList();

        Map<Long, List<LearnerSkillState>> statesByStudent = learnerSkillStateRepository.findByUserIdIn(studentIds)
                .stream()
                .collect(Collectors.groupingBy(LearnerSkillState::getUserId));

        Map<Long, long[]> assignmentsByStudent = new HashMap<>(); // [completed, pending]
        for (Object[] row : assignmentRepository.countByTeacherAndStudentIdsGroupByStatus(teacherId, studentIds)) {
            Long studentId = (Long) row[0];
            AssignmentStatus status = (AssignmentStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long[] pair = assignmentsByStudent.computeIfAbsent(studentId, k -> new long[2]);
            if (status == AssignmentStatus.COMPLETED) {
                pair[0] = count;
            } else {
                pair[1] = count;
            }
        }

        List<StudentOverviewResponse> content = students.stream().map(student -> {
            List<LearnerSkillState> states = statesByStudent.getOrDefault(student.getId(), List.of());
            int untouched = Math.max(0, totalObjectives - states.size());
            double masterySum = states.stream().mapToDouble(s -> s.getMastery().doubleValue()).sum()
                    + untouched * MasteryConfig.DEFAULT_MASTERY;
            double averageMastery = totalObjectives > 0
                    ? masteryService.roundMastery(masterySum / totalObjectives)
                    : 0.0;
            int mastered = (int) states.stream()
                    .filter(s -> s.getMastery().doubleValue() >= MasteryConfig.MASTERED_THRESHOLD)
                    .count();
            int totalAttempts = states.stream().mapToInt(LearnerSkillState::getAttemptsCount).sum();
            long[] counts = assignmentsByStudent.getOrDefault(student.getId(), new long[]{0L, 0L});

            return new StudentOverviewResponse(
                    student.getId(),
                    student.getName(),
                    student.getEmail(),
                    averageMastery,
                    mastered,
                    totalObjectives,
                    totalAttempts,
                    counts[0],
                    counts[1]);
        }).toList();

        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private Specification<User> byTeacherAndText(Long teacherId, String text) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("teacher").get("id"), teacherId));
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private StudentResponse toResponse(User user) {
        return new StudentResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
