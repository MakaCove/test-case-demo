package com.testcase.backend.domain;

import java.time.LocalDateTime;

public record Project(
        Long id,
        String name,
        String code,
        String description,
        String owner,
        boolean archived,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
