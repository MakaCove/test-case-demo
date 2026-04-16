package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接口/API 用例相关传输对象：列表项、详情、创建/更新、批量与状态补丁。
 * 与 {@link com.testcase.backend.controller.ApiTestCaseController} 及 API 用例实体字段对应。
 */
public class ApiTestCaseDtos {
    /** 列表中展示的需求资产摘要 */
    public record RequirementAssetBrief(
            String assetCode,
            String title,
            String assetType
    ) {
    }

    /**
     * 列表/简要详情：含请求体、预期、断言等 JSON 字符串及执行/评审状态；
     * {@code requirementAssets} 为展示用关联需求。
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

    /** 详情包装：目前仅内含 {@link CaseItem} */
    public record CaseDetail(CaseItem testCase) {
    }

    /** POST创建接口用例 */
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

    /** PUT 全量更新（不含状态字段时用 {@link UpdateStatusRequest}） */
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

    /** 批量改字段：{@code fields} 为服务端约定的补丁键值 */
    public record BatchUpdateRequest(
            List<Long> ids,
            Map<String, Object> fields,
            String reviewComment,
            String reason
    ) {
    }

    /** PATCH 执行/评审状态及备注 */
    public record UpdateStatusRequest(
            String executionStatus,
            String reviewStatus,
            String reviewComment,
            String reason
    ) {
    }

    /** 批量删除 */
    public record BatchDeleteRequest(
            @NotNull(message = "ids is required") List<Long> ids
    ) {
    }
}
