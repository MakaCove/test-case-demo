package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口/API 测试用例表 {@code api_test_cases}：请求体、预期、断言等以 JSON 字符串存储；
 * 执行/评审状态见 {@link com.testcase.backend.common.StatusConstants} 各子类。
 */
@Data
@TableName("api_test_cases")
public class ApiTestCaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("case_no")
    private String caseNo;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_id")
    private Long versionId;

    /** 来源 AI 生成任务 ID，手工创建可为 null */
    @TableField("source_task_id")
    private Long sourceTaskId;

    @TableField("module_name")
    private String moduleName;

    @TableField("feature_name")
    private String featureName;

    private String title;

    /** HTTP 请求描述（JSON） */
    @TableField("request_json")
    private String requestJson;

    /** 预期结果（JSON） */
    @TableField("expected_json")
    private String expectedJson;

    /** 断言规则（JSON） */
    @TableField("assertions_json")
    private String assertionsJson;

    private String priority;

    @TableField("execution_status")
    private String executionStatus;

    @TableField("review_status")
    private String reviewStatus;

    @TableField("last_executed_by")
    private Long lastExecutedBy;

    @TableField("last_executed_at")
    private LocalDateTime lastExecutedAt;

    @TableField("reviewed_by")
    private Long reviewedBy;

    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    @TableField("review_comment")
    private String reviewComment;

    private String remark;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    private Integer isDeleted;
}
