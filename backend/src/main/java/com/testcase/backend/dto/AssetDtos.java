package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求/原型资产 DTO：纯文本创建、更新、列表项及按 relationCode 批量删除。
 */
public class AssetDtos {
    /** 在版本下创建 TEXT 类型资产 */
    public record CreateTextAssetRequest(
            @NotBlank(message = "title is required") String title,
            @NotBlank(message = "content is required") String content,
            /** 可选；为空时服务端生成 */
            String relationCode
    ) {
    }

    /** TEXT/FILE 可改标题与正文；原型等类型仅标题等由服务层约束 */
    public record UpdateAssetRequest(
            @NotBlank(message = "title is required") String title,
            String content
    ) {
    }

    /**
     * 资产列表/详情：{@code content} 对 FILE 类型为抽取的正文；
     * {@code filePath} 对 PROTOTYPE 为相对配置根路径。
     */
    public record AssetItem(
            Long id,
            Long projectId,
            Long versionId,
            String assetCode,
            String relationCode,
            String assetType,
            String title,
            String content,
            String filePath,
            String fileName,
            Long fileSize,
            String mimeType,
            String source,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            /** 展示用：项目名称 */
            String projectName,
            /** 展示用：项目编码 */
            String projectCode,
            /** 展示用：版本名称（可为空） */
            String versionName,
            /** 展示用：业务版本号，如 v1.0 */
            String versionNo
    ) {
    }

    /** 按 relationCode 或 LEGACY-id 分组软删 */
    public record BatchDeleteRequest(
            List<String> relationCodes
    ) {
    }
}
