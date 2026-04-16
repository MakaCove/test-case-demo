package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用例字段级状态变更流水表 {@code test_case_status_logs}：如执行状态、评审状态的单字段前后值。
 */
@Data
@TableName("test_case_status_logs")
public class TestCaseStatusLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("case_id")
    private Long caseId;

    @TableField("field_name")
    private String fieldName;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    private String reason;

    @TableField("changed_by")
    private Long changedBy;

    @TableField("changed_at")
    private LocalDateTime changedAt;
}
