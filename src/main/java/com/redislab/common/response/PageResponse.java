package com.redislab.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <T> PageResponse<T> from(Page<T> springPage, int page, int size) {
        return new PageResponse<>(
                springPage.getContent(),
                page,
                size,
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast());
    }
}
