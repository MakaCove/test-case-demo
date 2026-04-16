package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板表 {@code prompt_templates}：可按 {@code scope_type}/{@code scope_id} 绑定项目等；
 * {@code version_no} 为内容修订序号；{@code status} 见 {@link com.testcase.backend.common.StatusConstants.Switch}。
 */
@Data
@TableName("prompt_templates")
public class PromptTemplateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("scope_type")
    private String scopeType;

    @TableField("scope_id")
    private Long scopeId;

    @TableField("version_no")
    private Integer versionNo;

    private String content;
    private String status;
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
