package com.testcase.backend.domain;

import java.time.LocalDateTime;

public record ProjectVersion(
        Long id,
        Long projectId,
        String versionNo,
        String name,
        String description,
        String status,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
