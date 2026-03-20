package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ProjectDtos {
    public record CreateProjectRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "code is required") String code,
            String description,
            String owner
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank(message = "name is required") String name,
            String description,
            String owner
    ) {
    }

    public record BatchUpdateProjectRequest(
            java.util.List<Long> ids,
            String action
    ) {
    }
}
