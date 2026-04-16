package com.testcase.backend.domain;

import java.time.LocalDateTime;

/**
 * 项目领域视图：由 {@link com.testcase.backend.controller.ProjectController} 等从实体映射后返回，
 * 与持久层 {@link com.testcase.backend.entity.ProjectEntity} 字段大致对应，便于 API 稳定契约。
 *
 * @param id 主键
 * @param name 项目名称
 * @param code 项目编码（业务唯一）
 * @param description 描述
 * @param owner 负责人展示名（MVP 中可能写死为占位，非必与库中 ownerUserId 同步）
 * @param archived 是否已归档/软删（{@code is_deleted = 1}）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record Project(
        Long id,
        String name,
        String code,
        String description,
        String owner,
        boolean archived,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
