package com.englishlab.service;

import com.englishlab.dto.ActivityDtos.CategoryRequest;
import com.englishlab.dto.ActivityDtos.CategoryResponse;
import com.englishlab.dto.PageResponse;
import com.englishlab.exception.AppExceptions.BusinessRuleException;
import com.englishlab.exception.AppExceptions.NotFoundException;
import com.englishlab.model.entity.Category;
import com.englishlab.repository.ActivityRepository;
import com.englishlab.repository.CategoryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de categorías. Escritura ADMIN (seguridad en el controller); lectura ADMIN+TEACHER.
 * El conteo de actividades se calcula con una sola consulta agregada por página (sin N+1).
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ActivityRepository activityRepository;

    public CategoryService(CategoryRepository categoryRepository, ActivityRepository activityRepository) {
        this.categoryRepository = categoryRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(String text, Pageable pageable) {
        Specification<Category> spec = (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like));
        };
        Page<Category> page = categoryRepository.findAll(spec, pageable);
        List<Category> categories = page.getContent();

        Map<Long, Long> counts = new HashMap<>();
        if (!categories.isEmpty()) {
            List<Long> ids = categories.stream().map(Category::getId).toList();
            for (Object[] row : activityRepository.countByCategoryIds(ids)) {
                counts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }

        List<CategoryResponse> content = categories.stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Ya existe una categoría con ese nombre");
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(request.description());
        return toResponse(categoryRepository.save(category), 0L);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada"));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessRuleException("Ya existe una categoría con ese nombre");
        }
        category.setName(name);
        category.setDescription(request.description());
        return toResponse(category, activityRepository.countByCategoryId(id));
    }

    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada"));
        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category category, long activitiesCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), activitiesCount);
/*  */    }
}
