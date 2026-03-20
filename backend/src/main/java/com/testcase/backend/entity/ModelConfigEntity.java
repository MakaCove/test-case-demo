package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_configs")
public class ModelConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String provider;

    @TableField("base_url")
    private String baseUrl;

    @TableField("model_key")
    private String modelKey;

    @TableField("api_key_encrypted")
    private String apiKeyEncrypted;

    private java.math.BigDecimal temperature;

    @TableField("max_tokens")
    private Integer maxTokens;

    private String status;

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

