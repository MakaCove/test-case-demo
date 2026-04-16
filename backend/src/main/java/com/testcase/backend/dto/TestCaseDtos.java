package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 功能/手工用例 DTO：列表、详情（含状态日志与历史快照）、CRUD、批量、状态补丁、从生成任务物化。
 */
public class TestCaseDtos {
    public record RequirementAssetBrief(
            String assetCode,
            String title,
            String assetType
    ) {
    }

    /**
     * 列表项：步骤、预期等为可读文本字段；{@code requirementAssets} 为展示用关联需求。
     */
    public record CaseItem(
            Long id,
            String caseNo,
            Long projectId,
            Long versionId,
            Long sourceTaskId,
            String moduleName,
            String featureName,
            String title,
            String precondition,
            String steps,
            String testData,
            String expectedResult,
            String priority,
            String executionStatus,
            String reviewStatus,
            String reviewComment,
            LocalDateTime lastExecutedAt,
            LocalDateTime reviewedAt,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            /** 展示用：项目名称 */
            String projectName,
            /** 展示用：项目编码 */
            String projectCode,
            /** 展示用：版本名称（可为空） */
            String versionName,
            /** 展示用：版本号，如 v1.0 */
            String versionNo,
            /** 展示用：该用例关联版本下的需求资产（assetCode + 标题对应关系） */
            List<RequirementAssetBrief> requirementAssets
    ) {
    }

    /** 单字段状态变更流水 */
    public record StatusLogItem(
            Long id,
            Long caseId,
            String fieldName,
            String oldValue,
            String newValue,
            String reason,
            Long changedBy,
            LocalDateTime changedAt
    ) {
    }

    /** 用例快照历史 */
    public record HistoryItem(
            Long id,
            Long caseId,
            String snapshotJson,
            Long changedBy,
            LocalDateTime changedAt,
            String changeType
    ) {
    }

    /** 详情：用例 + 状态日志 + 历史 */
    public record CaseDetail(
            CaseItem testCase,
            List<StatusLogItem> statusLogs,
            List<HistoryItem> histories
    ) {
    }

    public record CreateRequest(
            @NotNull(message = "projectId is required") Long projectId,
            @NotNull(message = "versionId is required") Long versionId,
            Long sourceTaskId,
            @NotBlank(message = "moduleName is required") String moduleName,
            @NotBlank(message = "featureName is required") String featureName,
            @NotBlank(message = "title is required") String title,
            String precondition,
            @NotBlank(message = "steps is required") String steps,
            String testData,
            @NotBlank(message = "expectedResult is required") String expectedResult,
            String priority,
            String remark
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "moduleName is required") String moduleName,
            @NotBlank(message = "featureName is required") String featureName,
            @NotBlank(message = "title is required") String title,
            String precondition,
            @NotBlank(message = "steps is required") String steps,
            String testData,
            @NotBlank(message = "expectedResult is required") String expectedResult,
            String priority,
            String remark
    ) {
    }

    public record BatchUpdateRequest(
            @NotNull(message = "ids is required") List<Long> ids,
            Map<String, Object> fields,
            String reviewComment,
            String reason
    ) {
    }

    public record UpdateStatusRequest(
            String executionStatus,
            String reviewStatus,
            String reviewComment,
            String reason
    ) {
    }

    /** 从生成任务落库用例时的可选条数上限 */
    public record MaterializeFromTaskRequest(
            Integer count
    ) {
    }

    public record BatchDeleteRequest(
            @NotNull(message = "ids is required") List<Long> ids
    ) {
    }
}
