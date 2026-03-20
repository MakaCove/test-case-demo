package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ApiTestCaseDtos {
    public record RequirementAssetBrief(
            String assetCode,
            String title,
            String assetType
    ) {
    }

    public record CaseItem(
            Long id,
            String caseNo,
            Long projectId,
            Long versionId,
            Long sourceTaskId,
            String moduleName,
            String featureName,
            String title,
            String requestJson,
            String expectedJson,
            String assertionsJson,
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

    public record CaseDetail(CaseItem testCase) {
    }

    public record CreateRequest(
            @NotNull(message = "projectId is required") Long projectId,
            @NotNull(message = "versionId is required") Long versionId,
            Long sourceTaskId,
            @NotBlank(message = "moduleName is required") String moduleName,
            @NotBlank(message = "featureName is required") String featureName,
            @NotBlank(message = "title is required") String title,
            @NotBlank(message = "requestJson is required") String requestJson,
            @NotBlank(message = "expectedJson is required") String expectedJson,
            @NotBlank(message = "assertionsJson is required") String assertionsJson,
            String priority,
            String remark
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "moduleName is required") String moduleName,
            @NotBlank(message = "featureName is required") String featureName,
            @NotBlank(message = "title is required") String title,
            @NotBlank(message = "requestJson is required") String requestJson,
            @NotBlank(message = "expectedJson is required") String expectedJson,
            @NotBlank(message = "assertionsJson is required") String assertionsJson,
            String priority,
            String remark
    ) {
    }

    public record BatchUpdateRequest(
            List<Long> ids,
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

    public record BatchDeleteRequest(
            @NotNull(message = "ids is required") List<Long> ids
    ) {
    }
}
