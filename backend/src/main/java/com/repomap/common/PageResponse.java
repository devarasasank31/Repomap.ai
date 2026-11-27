package com.repomap.common;

import java.util.List;

/** Uniform pagination envelope for list endpoints. */
public record PageResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
