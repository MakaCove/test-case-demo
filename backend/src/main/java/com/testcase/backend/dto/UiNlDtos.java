package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UI 自然语言自动化：用例、任务、规划/执行步骤、报告及 Runner 相关请求体。
 * {@code phase} 在步骤上区分规划（plan）与执行（exec）等，由服务层定义取值。
 */
public class UiNlDtos {
    /** 创建 NL 用例：含目标环境、入口 URL、凭证引用等 */
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

    /** 创建执行任务：绑定用例、模型、模板及浏览器/超时等 Runner 参数 */
    public record CreateTaskRequest(
            @NotNull(message = "projectId is required") Long projectId,
            @NotNull(message = "versionId is required") Long versionId,
            @NotNull(message = "uiNlCaseId is required") Long uiNlCaseId,
            @NotNull(message = "modelConfigId is required") Long modelConfigId,
            @NotNull(message = "promptTemplateId is required") Long promptTemplateId,
            Boolean headless,
            String browserName,
            String modelKey,
            Integer timeoutSeconds
    ) {
    }

    public record UpdateTaskRequest(
            @NotNull(message = "uiNlCaseId is required") Long uiNlCaseId,
            @NotNull(message = "modelConfigId is required") Long modelConfigId,
            @NotNull(message = "promptTemplateId is required") Long promptTemplateId,
            Boolean headless,
            String browserName,
            String modelKey,
            Integer timeoutSeconds
    ) {
    }

    /**
     * 任务摘要：{@code status} 多为规划流水线状态，{@code lastExecStatus} 为最近对接 Runner 的执行结果；
     * {@code runnerRunId} 与外部 ui-runner 对齐。
     */
    public record TaskItem(
            Long id,
            Long projectId,
            Long versionId,
            Long uiNlCaseId,
            String taskNo,
            String status,
            String lastExecStatus,
            Long submittedBy,
            LocalDateTime submittedAt,
            LocalDateTime planStartedAt,
            LocalDateTime planFinishedAt,
            LocalDateTime execStartedAt,
            LocalDateTime execFinishedAt,
            String runnerRunId,
            Long modelConfigId,
            Long promptTemplateId,
            Boolean headless,
            String browserName,
            String modelKey,
            Integer timeoutSeconds,
            String resultSummary,
            String interruptReason,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 单步：规划或执行轨迹中的一条，{@code phase} 区分来源表 */
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
            String rawLog,
            String phase
    ) {
    }

    /** 仅更新规划步骤（ui_nl_task_steps）；定位信息仅在执行轨迹 {@code ui_nl_task_exec_steps} 中落库 */
    public record UpdatePlanStepRequest(
            @NotBlank(message = "stepTitle is required") String stepTitle,
            String actionType,
            @NotBlank(message = "inputValue is required") String inputValue,
            String expectJson
    ) {
    }

    /** 执行报告元数据；HTML 路径等用于下载接口 */
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
            String artifactsJson,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String reportFilePath,
            LocalDateTime reportGeneratedAt
    ) {
    }

    /** 预留：启动任务（若路由使用 body占位） */
    public record StartTaskRequest() {
    }

    /** 预留：触发规划/执行（若路由使用 body 占位） */
    public record ExecuteTaskRequest() {
    }

    public record CancelTaskRequest(String reason) {
    }

    public record InterruptTaskRequest(String reason) {
    }
}
