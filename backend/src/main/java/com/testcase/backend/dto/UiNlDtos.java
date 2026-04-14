package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class UiNlDtos {
    public record CreateCaseRequest(
            @NotNull(message = "projectId is required") Long projectId,
            @NotNull(message = "versionId is required") Long versionId,
            @NotBlank(message = "title is required") String title,
            @NotBlank(message = "nlText is required") String nlText,
            String precondition,
            String targetEnv,
            String baseUrl,
            String credentialRef,
            String status,
            String tagsJson
    ) {
    }

    public record UpdateCaseRequest(
            @NotBlank(message = "title is required") String title,
            @NotBlank(message = "nlText is required") String nlText,
            String precondition,
            String targetEnv,
            String baseUrl,
            String credentialRef,
            String status,
            String tagsJson
    ) {
    }

    public record CaseItem(
            Long id,
            String caseNo,
            Long projectId,
            Long versionId,
            String title,
            String nlText,
            String precondition,
            String targetEnv,
            String baseUrl,
            String credentialRef,
            String status,
            String tagsJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CreateTaskRequest(
            @NotNull(message = "projectId is required") Long projectId,
            @NotNull(message = "versionId is required") Long versionId,
            @NotNull(message = "uiNlCaseId is required") Long uiNlCaseId,
            @NotNull(message = "modelConfigId is required") Long modelConfigId,
            @NotNull(message = "promptTemplateId is required") Long promptTemplateId,
            Boolean headless,
            String browserName,
            String modelKey,
            Integer timeoutSeconds,
            String payloadJson
    ) {
    }

    public record UpdateTaskRequest(
            @NotNull(message = "uiNlCaseId is required") Long uiNlCaseId,
            @NotNull(message = "modelConfigId is required") Long modelConfigId,
            @NotNull(message = "promptTemplateId is required") Long promptTemplateId,
            Boolean headless,
            String browserName,
            String modelKey,
            Integer timeoutSeconds,
            String payloadJson
    ) {
    }

    public record TaskItem(
            Long id,
            Long projectId,
            Long versionId,
            Long uiNlCaseId,
            String taskNo,
            String status,
            Long submittedBy,
            LocalDateTime submittedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String runnerRunId,
            Long modelConfigId,
            Long promptTemplateId,
            Boolean headless,
            String browserName,
            String modelKey,
            Integer timeoutSeconds,
            String payloadJson,
            String resultSummary,
            String interruptReason,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record StepItem(
            Long id,
            Long taskId,
            Integer stepNo,
            String stepTitle,
            String actionType,
            String targetJson,
            String inputValue,
            String expectJson,
            String status,
            Long durationMs,
            String errorMessage,
            String screenshotPath,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String rawLog
    ) {
    }

    public record ReportItem(
            Long id,
            String reportNo,
            Long taskId,
            Long projectId,
            Long versionId,
            String status,
            Integer totalSteps,
            Integer passedSteps,
            Integer failedSteps,
            String summary,
            String reportJson,
            String artifactsJson,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record StartTaskRequest() {
    }

    public record ExecuteTaskRequest() {
    }

    public record CancelTaskRequest(String reason) {
    }

    public record InterruptTaskRequest(String reason) {
    }
}
