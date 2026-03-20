package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("requirement_assets")
public class RequirementAssetEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_id")
    private Long versionId;

    @TableField("asset_code")
    private String assetCode;

    @TableField("relation_code")
    private String relationCode;

    @TableField("asset_type")
    private String assetType;

    private String title;
    private String content;

    @TableField("file_path")
    private String filePath;

    @TableField("file_name")
    private String fileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("mime_type")
    private String mimeType;

    private String source;

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
