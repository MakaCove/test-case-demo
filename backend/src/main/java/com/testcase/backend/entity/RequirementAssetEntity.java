package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求/原型资产表 {@code requirement_assets}：
 * TEXT 纯文本、FILE 上传解析后的正文、PROTOTYPE 等；{@code asset_type} 为业务枚举大写字符串。
 */
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

    /** 批次/分组键，用于前端多选与批量删除 */
    @TableField("relation_code")
    private String relationCode;

    @TableField("asset_type")
    private String assetType;

    private String title;
    /** FILE 类型可为抽取后的纯文本；PROTOTYPE 可能为空 */
    private String content;

    /** 原型等二进制相对存储根路径的路径 */
    @TableField("file_path")
    private String filePath;

    @TableField("file_name")
    private String fileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("mime_type")
    private String mimeType;

    /** MANUAL | UPLOAD 等 */
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
