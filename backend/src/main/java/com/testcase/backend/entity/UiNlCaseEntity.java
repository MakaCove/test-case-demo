package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ui_nl_cases")
public class UiNlCaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("case_no")
    private String caseNo;

    @TableField("project_id")
    private Long projectId;

    @TableField("version_id")
    private Long versionId;

    private String title;

    @TableField("nl_text")
    private String nlText;

    private String precondition;

    @TableField("target_env")
    private String targetEnv;

    @TableField("base_url")
    private String baseUrl;

    @TableField("credential_ref")
    private String credentialRef;

    private String status;

    @TableField("tags_json")
    private String tagsJson;

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
