package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目版本表 {@code project_versions}：{@code version_no} 在同一 {@code project_id} 下唯一；
 * {@code status} 见 {@link com.testcase.backend.common.StatusConstants.Version}。
 */
@Data
@TableName("project_versions")
public class ProjectVersionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_no")
    private String versionNo;

    private String name;
    private String description;
    private String status;

    @TableField("published_at")
    private LocalDateTime publishedAt;

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
