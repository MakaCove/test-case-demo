package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI NL 执行任务表 {@code ui_nl_tasks}：规划流水线与对接外部 Runner 的执行分轨；
 * {@code status} 多为规划阶段；{@code last_exec_status} 为最近一轮真实浏览器执行结果。
 */
@Data
@TableName("ui_nl_tasks")
public class UiNlTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_id")
    private Long versionId;

    @TableField("ui_nl_case_id")
    private Long uiNlCaseId;

    @TableField("task_no")
    private String taskNo;

    private String status;

    /** 最近一轮浏览器自动化：RUNNING/COMPLETED/FAILED/CANCELLED；与 {@link #status}（步骤生成流程）分离 */
    @TableField("last_exec_status")
    private String lastExecStatus;

    @TableField("submitted_by")
    private Long submittedBy;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("plan_started_at")
    private LocalDateTime planStartedAt;

    @TableField("plan_finished_at")
    private LocalDateTime planFinishedAt;

    @TableField("exec_started_at")
    private LocalDateTime execStartedAt;

    @TableField("exec_finished_at")
    private LocalDateTime execFinishedAt;

    /** 与 ui-runner 返回的 runId 对齐 */
    @TableField("runner_run_id")
    private String runnerRunId;

    @TableField("model_config_id")
    private Long modelConfigId;

    @TableField("prompt_template_id")
    private Long promptTemplateId;

    /** 0/1 与布尔对应 */
    private Integer headless;

    @TableField("browser_name")
    private String browserName;

    @TableField("model_key")
    private String modelKey;

    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    @TableField("result_summary")
    private String resultSummary;

    @TableField("interrupt_reason")
    private String interruptReason;

    @TableField("error_message")
    private String errorMessage;

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
