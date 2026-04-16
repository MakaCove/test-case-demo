package com.testcase.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用例内容快照历史表 {@code test_case_histories}：{@code snapshot_json} 存当时用例全量或增量结构。
 */
@Data
@TableName("test_case_histories")
public class TestCaseHistoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("case_id")
    private Long caseId;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("changed_by")
    private Long changedBy;

    @TableField("changed_at")
    private LocalDateTime changedAt;

    @TableField("change_type")
    private String changeType;
}
