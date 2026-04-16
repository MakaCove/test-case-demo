package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生成任务与「参考版本」的多对多关联表 {@code generation_task_refs}：
 * 一条表示某任务引用了哪个 {@code project_versions.id} 作为上下文。
 */
@Data
@TableName("generation_task_refs")
public class GenerationTaskRefEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("ref_version_id")
    private Long refVersionId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
