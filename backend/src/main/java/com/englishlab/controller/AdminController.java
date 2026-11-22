package com.englishlab.controller;

import com.englishlab.dto.ActivityDtos.CategoryRequest;
import com.englishlab.dto.ActivityDtos.CategoryResponse;
import com.englishlab.dto.PageResponse;
import com.englishlab.dto.UserDtos.UserAdminRequest;
import com.englishlab.dto.UserDtos.UserAdminResponse;
import com.englishlab.security.JwtService.AuthenticatedUser;
import com.englishlab.service.AdminUserService;
import com.englishlab.service.CategoryService;
import com.englishlab.util.PageableFactory;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * CRUD de administración: usuarios (solo ADMIN) y categorías de actividad
 * (lectura para ADMIN/TEACHER, escritura solo ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Set<String> USER_SORT = Set.of("id", "name", "email", "createdAt");
    private static final Set<String> CATEGORY_SORT = Set.of("id", "name");

    private final AdminUserService adminUserService;
    private final CategoryService categoryService;

    public AdminController(AdminUserService adminUserService, CategoryService categoryService) {
        this.adminUserService = adminUserService;
        this.categoryService = categoryService;
    }

    // ---------------------------------------------------------------- usuarios

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserAdminResponse>> users(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(adminUserService.list(role, text,
                PageableFactory.of(page, size, sort, USER_SORT, "id")));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAdminResponse> createUser(@Valid @RequestBody UserAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAdminResponse> updateUser(
            @PathVariable Long id, @Valid @RequestBody UserAdminRequest request) {
        return ResponseEntity.ok(adminUserService.update(id, request));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        adminUserService.delete(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- categorías

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<PageResponse<CategoryResponse>> categories(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(categoryService.list(text,
                PageableFactory.of(page, size, sort, CATEGORY_SORT, "id")));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
