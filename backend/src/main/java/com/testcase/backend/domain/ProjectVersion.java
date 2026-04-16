package com.testcase.backend.domain;

import java.time.LocalDateTime;

/**
 * 项目版本领域视图：由 {@link com.testcase.backend.controller.VersionController} 等映射返回，
 * 状态取值见 {@link com.testcase.backend.common.StatusConstants.Version}（如 DRAFT、PUBLISHED）。
 *
 * @param id 主键
 * @param projectId 所属项目 ID
 * @param versionNo 版本号/代号（同一 project 内唯一）
 * @param name 版本显示名称
 * @param description 描述
 * @param status 生命周期状态（英文枚举）
 * @param deleted 是否已软删
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ProjectVersion(
        Long id,
        Long projectId,
        String versionNo,
        String name,
        String description,
        String status,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
