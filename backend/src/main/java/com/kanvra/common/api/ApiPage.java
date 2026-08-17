package com.kanvra.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Standard paginated list response shape (docs/SPEC.md §17.1).
 */
public record ApiPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> ApiPage<T> from(Page<T> page) {
        return new ApiPage<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
