package com.englishlab.controller;

import com.englishlab.dto.AssignmentDtos.AssignmentCreateRequest;
import com.englishlab.dto.AssignmentDtos.AssignmentResponse;
import com.englishlab.dto.AssignmentDtos.AssignmentUpdateRequest;
import com.englishlab.dto.ObjectiveDtos.ProgressResponse;
import com.englishlab.dto.PageResponse;
import com.englishlab.dto.UserDtos.AssignStudentRequest;
import com.englishlab.dto.UserDtos.StudentOverviewResponse;
import com.englishlab.dto.UserDtos.StudentResponse;
import com.englishlab.security.JwtService.AuthenticatedUser;
import com.englishlab.service.AssignmentService;
import com.englishlab.service.TeacherStudentService;
import com.englishlab.util.PageableFactory;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que hace el TEACHER: vincular estudiantes, ver su progreso y asignarles actividades.
 */
@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {

    private static final Set<String> STUDENT_SORT = Set.of("id", "name", "email", "createdAt");
    private static final Set<String> ASSIGNMENT_SORT = Set.of("id", "assignedAt", "dueAt", "completedAt", "status");

    private final TeacherStudentService teacherStudentService;
    private final AssignmentService assignmentService;

    public TeacherController(TeacherStudentService teacherStudentService, AssignmentService assignmentService) {
        this.teacherStudentService = teacherStudentService;
        this.assignmentService = assignmentService;
    }

    @PostMapping("/students")
    public ResponseEntity<StudentResponse> assignStudent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AssignStudentRequest request) {
        return ResponseEntity.ok(teacherStudentService.assign(principal.id(), request.email()));
    }

    @GetMapping("/students")
    public ResponseEntity<PageResponse<StudentResponse>> students(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(teacherStudentService.list(principal.id(), text,
                PageableFactory.of(page, size, sort, STUDENT_SORT, "id")));
    }

    @GetMapping("/students/overview")
    public ResponseEntity<PageResponse<StudentOverviewResponse>> studentsOverview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(teacherStudentService.listOverview(principal.id(), text,
                PageableFactory.of(page, size, sort, STUDENT_SORT, "name")));
    }

    @GetMapping("/students/{id}/progress")
    public ResponseEntity<List<ProgressResponse>> studentProgress(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.progress(principal.id(), id));
    }

    @GetMapping("/assignments")
    public ResponseEntity<PageResponse<AssignmentResponse>> assignments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(assignmentService.teacherAssignments(principal.id(), status,
                PageableFactory.of(page, size, sort, ASSIGNMENT_SORT, "assignedAt")));
    }

    @PostMapping("/assignments")
    public ResponseEntity<AssignmentResponse> createAssignment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AssignmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.create(principal.id(), request));
    }

    @PutMapping("/assignments/{id}")
    public ResponseEntity<AssignmentResponse> updateAssignmentDueDate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestBody AssignmentUpdateRequest request) {
        return ResponseEntity.ok(assignmentService.updateDueDate(principal.id(), id, request == null ? null : request.dueAt()));
    }
}
