package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class ExportDtos {
    public record ExportItem(
            Long id,
            String exportNo,
            Long projectId,
            Long versionId,
            String format,
            String scope,
            String status,
            String requestJson,
            /** 展示用：如「功能用例、接口用例」 */
            String exportContent,
            String filePath,
            Long fileSize,
            String errorMessage,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CreateRequest(
            @NotNull(message = "projectId is required") Long projectId,
            @NotNull(message = "versionId is required") Long versionId,
            @NotBlank(message = "format is required") String format,
            @NotBlank(message = "scope is required") String scope,
            String requestJson
    ) {
    }
}

