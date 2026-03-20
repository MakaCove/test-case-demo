package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class VersionDtos {
    public record CreateVersionRequest(
            @NotBlank(message = "versionNo is required") String versionNo,
            String name,
            String description
    ) {
    }

    public record UpdateVersionRequest(
            @NotBlank(message = "versionNo is required") String versionNo,
            String name,
            String description,
            @NotBlank(message = "status is required") String status
    ) {
    }

    public record PublishVersionRequest(
            @NotNull(message = "versionId is required") Long versionId
    ) {
    }
}
