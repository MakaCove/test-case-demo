package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出任务表 {@code export_records}：异步生成 Markdown 等产物；
 * {@code request_json} 结构见 {@link com.testcase.backend.dto.ExportRequestOptions}；
 * {@code status} 见 {@link com.testcase.backend.common.StatusConstants.Export}。
 */
@Data
@TableName("export_records")
public class ExportRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("export_no")
    private String exportNo;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_id")
    private Long versionId;

    private String format;
    private String scope;
    private String status;

    /** 导出选项 JSON */
    @TableField("request_json")
    private String requestJson;

    /** 生成文件相对/绝对路径 */
    @TableField("file_path")
    private String filePath;

    @TableField("file_size")
    private Long fileSize;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_deleted")
    private Integer isDeleted;
}
