package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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

    @TableField("request_json")
    private String requestJson;

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

