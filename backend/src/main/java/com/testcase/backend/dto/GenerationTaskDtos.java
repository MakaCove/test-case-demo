package com.testcase.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 生成用例任务：提交参数、列表项、详情、中断与更新（非 RUNNING 时可改模型/模板/引用资产等）。
 */
public class GenerationTaskDtos {

    /** 列表/详情中展示：任务目标版本下的需求资产（与生成上下文一致） */
    public record RequirementAssetBrief(String assetCode, String title, String assetType) {
    }

    /** POST 提交生成任务 */
    public record SubmitTaskRequest(
            @NotNull(message = "projectId is required") Long projectId,
            @NotNull(message = "versionId is required") Long versionId,
            @NotNull(message = "modelConfigId is required") Long modelConfigId,
            @NotNull(message = "promptTemplateId is required") Long promptTemplateId,
            List<Long> referenceVersionIds,
            /** 选择要参与生成的需求资产批次（relation_code 列表）。为空表示使用该版本下全部资产。 */
            List<String> referenceAssetRelationCodes,
            String strategy,
            Integer caseLimit,
            /** FUNCTIONAL (default) or API */
            String caseCategory
    ) {
    }

    /** 任务摘要行 */
    public record TaskItem(
            Long id,
            Long projectId,
            Long versionId,
            String taskNo,
            String status,
            Boolean queueAutoEnabled,
            Long queueNo,
            Long submittedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime submittedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long interruptBy,
            String interruptReason,
            String errorMessage,
            Long modelConfigId,
            Long promptTemplateId,
            String caseCategory,
            List<RequirementAssetBrief> requirementAssets
    ) {
    }

    /** 详情：在 {@link TaskItem} 基础上增加原始 payload 与结果摘要 */
    public record TaskDetail(
            TaskItem task,
            List<Long> referenceVersionIds,
            String payloadJson,
            String resultSummary
    ) {
    }

    public record InterruptRequest(String reason) {
    }

    public record BatchDeleteRequest(
            List<Long> taskIds
    ) {
    }

    /**
     * 更新任务参数：仅在非 RUNNING 时允许（如启动前、重试/重新生成前）。
     * {@code referenceAssetRelationCodes} 为空表示沿用原任务。
     */
    public record UpdateTaskRequest(
            @NotNull(message = "modelConfigId is required") Long modelConfigId,
            @NotNull(message = "promptTemplateId is required") Long promptTemplateId,
            List<Long> referenceVersionIds,
            /** 选择要参与生成的需求资产批次（relation_code 列表）。为空表示沿用原任务设置。 */
            List<String> referenceAssetRelationCodes,
            String strategy,
            Integer caseLimit,
            /** FUNCTIONAL (default) or API */
            String caseCategory
    ) {
    }
}
