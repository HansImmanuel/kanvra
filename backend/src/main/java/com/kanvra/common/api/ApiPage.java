package com.kanvra.common.api;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

    /** Builds an ApiPage from an already-materialized list using page/size. */
    public static <T> ApiPage<T> from(PageRequest pageRequest, List<T> all) {
        int total = all.size();
        int from = Math.min(pageRequest.getPageNumber() * pageRequest.getPageSize(), total);
        int to = Math.min(from + pageRequest.getPageSize(), total);
        List<T> content = all.subList(from, to);
        int totalPages = pageRequest.getPageSize() == 0
                ? 0
                : (int) Math.ceil((double) total / pageRequest.getPageSize());
        return new ApiPage<>(content, pageRequest.getPageNumber(), pageRequest.getPageSize(), total, totalPages);
    }
}
