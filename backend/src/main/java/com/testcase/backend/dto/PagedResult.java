package com.testcase.backend.dto;

import java.util.List;

public record PagedResult<T>(
        List<T> records,
        int pageNo,
        int pageSize,
        long total
) {
}
