package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 任务执行轨迹表 {@code ui_nl_task_exec_steps}：对接 Runner 后每步真实结果（截图路径、耗时、原始日志等），
 * 与规划表 {@link UiNlTaskStepEntity} 区分。
 */
@Data
@TableName("ui_nl_task_exec_steps")
public class UiNlTaskExecStepEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("step_no")
    private Integer stepNo;

    @TableField("step_title")
    private String stepTitle;

    @TableField("action_type")
    private String actionType;

    @TableField("target_json")
    private String targetJson;

    @TableField("input_value")
    private String inputValue;

    @TableField("expect_json")
    private String expectJson;

    private String status;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("screenshot_path")
    private String screenshotPath;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("raw_log")
    private String rawLog;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    private Integer isDeleted;
}
