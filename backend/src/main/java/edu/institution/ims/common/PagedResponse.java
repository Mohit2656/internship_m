package edu.institution.ims.common;

import org.springframework.data.domain.Page;
import java.util.List;

public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {
    public static <T> PagedResponse<T> from(Page<T> value) {
        return new PagedResponse<>(value.getContent(), value.getNumber(), value.getSize(), value.getTotalElements(), value.getTotalPages(), value.isFirst(), value.isLast());
    }
}

