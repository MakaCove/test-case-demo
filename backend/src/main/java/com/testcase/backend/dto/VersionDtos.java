package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 项目版本 HTTP 请求体：创建、更新（含 status）、发布占位。
 * {@code status} 取值见 {@link com.testcase.backend.common.StatusConstants.Version}。
 */
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

    /** 若某路由以 body 传 versionId 时使用（与路径参数二选一场景） */
    public record PublishVersionRequest(
            @NotNull(message = "versionId is required") Long versionId
    ) {
    }
}
