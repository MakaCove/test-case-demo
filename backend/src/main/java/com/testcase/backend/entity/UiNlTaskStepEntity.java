package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 任务规划步骤表 {@code ui_nl_task_steps}：Planner生成的步骤（标题、操作类型、输入与预期），
 * 执行时的截图等仅在 {@link UiNlTaskExecStepEntity}。
 */
@Data
@TableName("ui_nl_task_steps")
public class UiNlTaskStepEntity {
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

    @TableField("input_value")
    private String inputValue;

    @TableField("expect_json")
    private String expectJson;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    private Integer isDeleted;
}
