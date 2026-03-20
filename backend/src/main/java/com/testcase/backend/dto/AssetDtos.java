package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public class AssetDtos {
    public record CreateTextAssetRequest(
            @NotBlank(message = "title is required") String title,
            @NotBlank(message = "content is required") String content,
            String relationCode
    ) {
    }

    public record UpdateAssetRequest(
            @NotBlank(message = "title is required") String title,
            String content
    ) {
    }

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

    public record BatchDeleteRequest(
            List<String> relationCodes
    ) {
    }
}
