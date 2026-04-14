package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ui_nl_reports")
public class UiNlReportEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("report_no")
    private String reportNo;

    @TableField("task_id")
    private Long taskId;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_id")
    private Long versionId;

    private String status;

    @TableField("total_steps")
    private Integer totalSteps;

    @TableField("passed_steps")
    private Integer passedSteps;

    @TableField("failed_steps")
    private Integer failedSteps;

    private String summary;

    @TableField("report_json")
    private String reportJson;

    @TableField("artifacts_json")
    private String artifactsJson;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    private Integer isDeleted;
}
