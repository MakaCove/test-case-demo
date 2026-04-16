package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成用例任务表 {@code generation_tasks}：队列、模型与模板引用、提交/起止时间；
 * {@code status} 见 {@link com.testcase.backend.common.StatusConstants.GenerationTask}；
 * 引用版本多对多关系见 {@link GenerationTaskRefEntity}。
 */
@Data
@TableName("generation_tasks")
public class GenerationTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_id")
    private Long versionId;

    @TableField("task_no")
    private String taskNo;

    private String status;

    @TableField("queue_no")
    private Long queueNo;

    @TableField("submitted_by")
    private Long submittedBy;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("interrupt_by")
    private Long interruptBy;

    @TableField("interrupt_reason")
    private String interruptReason;

    @TableField("error_message")
    private String errorMessage;

    @TableField("model_config_id")
    private Long modelConfigId;

    @TableField("prompt_template_id")
    private Long promptTemplateId;

    /** FUNCTIONAL | API */
    @TableField("case_category")
    private String caseCategory;

    /** 提交参数快照 JSON（引用资产、策略等） */
    @TableField("payload_json")
    private String payloadJson;

    @TableField("result_summary")
    private String resultSummary;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    private Integer isDeleted;
}
