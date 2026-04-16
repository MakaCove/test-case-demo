package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计表 {@code operation_logs}：记录业务对象变更前后 JSON快照及操作人。
 */
@Data
@TableName("operation_logs")
public class OperationLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型，如 PROJECT、ASSET、MODEL_CONFIG */
    @TableField("object_type")
    private String objectType;

    @TableField("object_id")
    private Long objectId;

    /** 动作枚举，如 CREATE、UPDATE、DELETE */
    private String action;

    @TableField("before_json")
    private String beforeJson;

    @TableField("after_json")
    private String afterJson;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
