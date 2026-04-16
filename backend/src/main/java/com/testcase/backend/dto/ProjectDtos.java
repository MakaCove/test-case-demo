package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 项目 HTTP 请求体：创建、更新、批量操作（如归档）。
 */
public class ProjectDtos {
    public record CreateProjectRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "code is required") String code,
            String description,
            /** MVP 中可能未落库，仅预留 */
            String owner
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank(message = "name is required") String name,
            String description,
            String owner
    ) {
    }

    /** 批量操作：当前仅支持 action=ARCHIVE */
    public record BatchUpdateProjectRequest(
            java.util.List<Long> ids,
            String action
    ) {
    }
}
