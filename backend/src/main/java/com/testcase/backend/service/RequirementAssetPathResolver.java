package com.testcase.backend.service;

import com.testcase.backend.entity.RequirementAssetEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 将 {@link RequirementAssetEntity#getFilePath()} 解析为服务器本地 {@link Path}。
 * 相对路径一律相对于 {@code app.storage.prototype-base-path}（原型图上传落盘目录）。
 */
@Component
public class RequirementAssetPathResolver {

    private final String prototypeBasePath;

    public RequirementAssetPathResolver(
            @Value("${app.storage.prototype-base-path:uploads/prototypes}") String prototypeBasePath
    ) {
        this.prototypeBasePath = prototypeBasePath == null ? "uploads/prototypes" : prototypeBasePath.trim();
    }

    public Path resolve(RequirementAssetEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getFilePath())) {
            return null;
        }
        String fp = entity.getFilePath().trim().replace("\\", "/");
        return Paths.get(prototypeBasePath).resolve(fp);
    }
}
