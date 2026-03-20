package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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

    @TableField("source_task_id")
    private Long sourceTaskId;

    @TableField("module_name")
    private String moduleName;

    @TableField("feature_name")
    private String featureName;

    private String title;

    @TableField("request_json")
    private String requestJson;

    @TableField("expected_json")
    private String expectedJson;

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
