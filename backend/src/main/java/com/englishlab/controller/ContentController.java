package com.englishlab.controller;

import com.englishlab.dto.ActivityDtos.ActivityAdminResponse;
import com.englishlab.dto.ActivityDtos.ActivityRequest;
import com.englishlab.dto.ObjectiveDtos.ObjectiveResponse;
import com.englishlab.dto.PageResponse;
import com.englishlab.service.ActivityService;
import com.englishlab.service.ObjectiveStateService;
import com.englishlab.util.PageableFactory;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Banco de contenido para ADMIN/TEACHER: CRUD de actividades y listado de objetivos.
 */
@RestController
@RequestMapping("/api/v1/content")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class ContentController {

    private static final Set<String> ACTIVITY_SORT = Set.of("id", "title", "level", "skill", "type");
    private static final Set<String> OBJECTIVE_SORT = Set.of("id", "code", "name", "cefrLevel", "skill");

    private final ActivityService activityService;
    private final ObjectiveStateService objectiveStateService;

    public ContentController(ActivityService activityService, ObjectiveStateService objectiveStateService) {
        this.activityService = activityService;
        this.objectiveStateService = objectiveStateService;
    }

    @GetMapping("/activities")
    public ResponseEntity<PageResponse<ActivityAdminResponse>> activities(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(activityService.list(level, skill, type, categoryId, text,
                PageableFactory.of(page, size, sort, ACTIVITY_SORT, "id")));
    }

    @PostMapping("/activities")
    public ResponseEntity<ActivityAdminResponse> createActivity(@Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.create(request));
    }

    @PutMapping("/activities/{id}")
    public ResponseEntity<ActivityAdminResponse> updateActivity(
            @PathVariable Long id, @Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(activityService.update(id, request));
    }

    @DeleteMapping("/activities/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        activityService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/objectives")
    public ResponseEntity<PageResponse<ObjectiveResponse>> objectives(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(objectiveStateService.listObjectives(
                PageableFactory.of(page, size, sort, OBJECTIVE_SORT, "id")));
    }
}
