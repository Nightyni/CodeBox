package com.codebox.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int pageNum,
        int pageSize,
        long totalCount,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> items, int pageNum, int pageSize, long totalCount) {
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        return new PageResponse<>(items, pageNum, pageSize, totalCount, totalPages);
    }
}
