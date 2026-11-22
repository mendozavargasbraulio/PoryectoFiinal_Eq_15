package com.englishlab.dto;

import java.util.List;

/**
 * Respuesta paginada estándar de todas las listas del API.
 *
 * @param <T> tipo de los elementos de la página.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
