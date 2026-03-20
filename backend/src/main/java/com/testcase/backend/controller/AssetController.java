package com.testcase.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.AssetDtos;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.entity.ProjectVersionEntity;
import com.testcase.backend.entity.ProjectEntity;
import com.testcase.backend.entity.RequirementAssetEntity;
import com.testcase.backend.mapper.ProjectMapper;
import com.testcase.backend.mapper.ProjectVersionMapper;
import com.testcase.backend.mapper.RequirementAssetMapper;
import com.testcase.backend.service.DocumentTextExtractor;
import com.testcase.backend.service.OperationLogService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AssetController {
    private static final Logger log = LoggerFactory.getLogger(AssetController.class);
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_FILE = "FILE";
    private static final String TYPE_PROTOTYPE = "PROTOTYPE";

    private final RequirementAssetMapper requirementAssetMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final ProjectMapper projectMapper;
    private final OperationLogService operationLogService;
    private final DocumentTextExtractor documentTextExtractor;

    @Value("${app.storage.base-path:uploads}")
    private String storageBasePath;

    @Value("${app.storage.prototype-base-path:uploads/prototypes}")
    private String prototypeBasePath;

    public AssetController(
            RequirementAssetMapper requirementAssetMapper,
            ProjectVersionMapper projectVersionMapper,
            ProjectMapper projectMapper,
            OperationLogService operationLogService,
            DocumentTextExtractor documentTextExtractor
    ) {
        this.requirementAssetMapper = requirementAssetMapper;
        this.projectVersionMapper = projectVersionMapper;
        this.projectMapper = projectMapper;
        this.operationLogService = operationLogService;
        this.documentTextExtractor = documentTextExtractor;
    }

    @PostMapping("/versions/{versionId}/requirements/text")
    public ApiResponse<AssetDtos.AssetItem> createTextAsset(
            @PathVariable Long versionId,
            @Valid @RequestBody AssetDtos.CreateTextAssetRequest request
    ) {
        LocalDateTime now = LocalDateTime.now();
        VersionContext ctx = assertVersionExists(versionId);
        RequirementAssetEntity entity = new RequirementAssetEntity();
        entity.setProjectId(ctx.project().getId());
        entity.setVersionId(versionId);
        entity.setAssetCode(generateAssetCode(versionId));
        entity.setRelationCode(resolveRelationCode(request.relationCode(), ctx.project(), ctx.version()));
        entity.setAssetType(TYPE_TEXT);
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setSource("MANUAL");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        requirementAssetMapper.insert(entity);
        operationLogService.log("ASSET", entity.getId(), "CREATE_TEXT", null, entity, null);
        return ApiResponse.success(toItem(entity));
    }

    @PostMapping(value = "/versions/{versionId}/requirements/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssetDtos.AssetItem> uploadRequirementFile(
            @PathVariable Long versionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "relationCode", required = false) String relationCode,
            @RequestParam(value = "title", required = false) String title
    ) throws IOException {
        return ApiResponse.success(saveRequirementDocumentExtracted(versionId, file, relationCode, title));
    }

    @PostMapping(value = "/versions/{versionId}/prototypes/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssetDtos.AssetItem> uploadPrototypeFile(
            @PathVariable Long versionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "relationCode", required = false) String relationCode
    ) throws IOException {
        return ApiResponse.success(savePrototypeToConfiguredFolder(versionId, file, relationCode));
    }

    @GetMapping("/versions/{versionId}/assets")
    public ApiResponse<PagedResult<AssetDtos.AssetItem>> listAssets(
            @PathVariable Long versionId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String keyword
    ) {
        assertVersionExists(versionId);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getVersionId, versionId)
                .eq(RequirementAssetEntity::getIsDeleted, 0);
        if (StringUtils.hasText(assetType)) {
            wrapper.eq(RequirementAssetEntity::getAssetType, assetType.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(RequirementAssetEntity::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(RequirementAssetEntity::getId);
        Page<RequirementAssetEntity> page = requirementAssetMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<AssetDtos.AssetItem> records = toItems(page.getRecords());
        return ApiResponse.success(new PagedResult<>(records, safePageNo, safePageSize, page.getTotal()));
    }

    @GetMapping("/assets")
    public ApiResponse<PagedResult<AssetDtos.AssetItem>> listAllAssets(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) String relationCode,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String keyword
    ) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0);
        if (projectId != null && projectId > 0) {
            wrapper.eq(RequirementAssetEntity::getProjectId, projectId);
        }
        if (versionId != null && versionId > 0) {
            wrapper.eq(RequirementAssetEntity::getVersionId, versionId);
        }
        if (StringUtils.hasText(relationCode)) {
            wrapper.eq(RequirementAssetEntity::getRelationCode, relationCode.trim());
        }
        if (StringUtils.hasText(assetType)) {
            wrapper.eq(RequirementAssetEntity::getAssetType, assetType.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(RequirementAssetEntity::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(RequirementAssetEntity::getId);
        Page<RequirementAssetEntity> page = requirementAssetMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<AssetDtos.AssetItem> records = toItems(page.getRecords());
        return ApiResponse.success(new PagedResult<>(records, safePageNo, safePageSize, page.getTotal()));
    }

    @PutMapping("/assets/{assetId}")
    public ApiResponse<AssetDtos.AssetItem> updateAsset(
            @PathVariable Long assetId,
            @Valid @RequestBody AssetDtos.UpdateAssetRequest request
    ) {
        LocalDateTime now = LocalDateTime.now();
        var entity = requirementAssetMapper.selectById(assetId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("asset not found");
        }
        var before = toItem(entity);
        entity.setTitle(request.title().trim());
        if (TYPE_TEXT.equals(entity.getAssetType()) || TYPE_FILE.equals(entity.getAssetType())) {
            if (request.content() != null) {
                entity.setContent(request.content());
            }
        }
        entity.setUpdatedBy(1L);
        entity.setUpdatedAt(now);
        requirementAssetMapper.updateById(entity);
        operationLogService.log("ASSET", assetId, "UPDATE", before, toItem(entity), null);
        return ApiResponse.success(toItem(entity));
    }

    @GetMapping("/assets/{assetId}")
    public ApiResponse<AssetDtos.AssetItem> getAsset(@PathVariable Long assetId) {
        var entity = requirementAssetMapper.selectById(assetId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("asset not found");
        }
        return ApiResponse.success(toItem(entity));
    }

    @DeleteMapping("/assets/{assetId}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long assetId) {
        LocalDateTime now = LocalDateTime.now();
        var entity = requirementAssetMapper.selectById(assetId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("asset not found");
        }
        entity.setIsDeleted(1);
        entity.setUpdatedBy(1L);
        entity.setUpdatedAt(now);
        requirementAssetMapper.updateById(entity);
        operationLogService.log("ASSET", assetId, "DELETE", null, null, null);
        return ApiResponse.success(null);
    }

    @PostMapping("/assets/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody AssetDtos.BatchDeleteRequest request) {
        LocalDateTime now = LocalDateTime.now();
        List<String> relationCodes = request == null ? null : request.relationCodes();
        if (relationCodes == null || relationCodes.isEmpty()) {
            throw new IllegalArgumentException("relationCodes is required");
        }
        int affected = 0;
        for (String code : relationCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String normalized = code.trim();
            if (normalized.startsWith("LEGACY-")) {
                Long assetId;
                try {
                    assetId = Long.valueOf(normalized.substring("LEGACY-".length()));
                } catch (Exception e) {
                    continue;
                }
                affected += requirementAssetMapper.update(
                        null,
                        new LambdaUpdateWrapper<RequirementAssetEntity>()
                                .set(RequirementAssetEntity::getIsDeleted, 1)
                                .set(RequirementAssetEntity::getUpdatedBy, 1L)
                                .set(RequirementAssetEntity::getUpdatedAt, now)
                                .eq(RequirementAssetEntity::getId, assetId)
                                .eq(RequirementAssetEntity::getIsDeleted, 0)
                );
                operationLogService.log("ASSET", assetId, "DELETE", null, null, "batch-delete legacy");
            } else {
                affected += requirementAssetMapper.update(
                        null,
                        new LambdaUpdateWrapper<RequirementAssetEntity>()
                                .set(RequirementAssetEntity::getIsDeleted, 1)
                                .set(RequirementAssetEntity::getUpdatedBy, 1L)
                                .set(RequirementAssetEntity::getUpdatedAt, now)
                                .eq(RequirementAssetEntity::getRelationCode, normalized)
                                .eq(RequirementAssetEntity::getIsDeleted, 0)
                );
                operationLogService.log("ASSET", null, "DELETE_BATCH", null, null, "relationCode=" + normalized);
            }
        }
        log.info("batch delete assets, groups={}, affected={}", relationCodes.size(), affected);
        return ApiResponse.success(null);
    }

    /**
     * 需求文档：解析正文写入 {@code content}，不在磁盘保留原件。
     */
    private AssetDtos.AssetItem saveRequirementDocumentExtracted(Long versionId, MultipartFile file, String relationCode, String titleOverride) throws IOException {
        VersionContext ctx = assertVersionExists(versionId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        LocalDateTime now = LocalDateTime.now();
        String originalName = file.getOriginalFilename();
        String safeName = StringUtils.hasText(originalName) ? Paths.get(originalName).getFileName().toString() : "document.bin";
        String extracted = documentTextExtractor.extractAsPlainText(file);
        if (!StringUtils.hasText(extracted)) {
            throw new IllegalArgumentException("未能从文档中提取正文，请检查文件格式或内容是否为空");
        }

        String displayTitle = StringUtils.hasText(titleOverride) ? titleOverride.trim() : safeName;
        if (!StringUtils.hasText(displayTitle)) {
            displayTitle = safeName;
        }

        RequirementAssetEntity entity = new RequirementAssetEntity();
        entity.setProjectId(ctx.project().getId());
        entity.setVersionId(versionId);
        entity.setAssetCode(generateAssetCode(versionId));
        entity.setRelationCode(resolveRelationCode(relationCode, ctx.project(), ctx.version()));
        entity.setAssetType(TYPE_FILE);
        entity.setTitle(displayTitle.length() > 255 ? displayTitle.substring(0, 255) : displayTitle);
        entity.setContent(extracted);
        entity.setFileName(safeName);
        entity.setFilePath(null);
        entity.setFileSize(file.getSize());
        entity.setMimeType(file.getContentType());
        entity.setSource("UPLOAD");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        requirementAssetMapper.insert(entity);
        operationLogService.log("ASSET", entity.getId(), "UPLOAD_" + TYPE_FILE + "_EXTRACTED", null, entity, null);
        log.info("requirement doc extracted, assetId={}, versionId={}, fileName={}, contentLen={}", entity.getId(), versionId, safeName, extracted.length());
        return toItem(entity);
    }

    /**
     * 原型图：二进制保存到 {@code app.storage.prototype-base-path} 下按日期分子目录。
     * {@code file_path} 存相对 prototype-base-path 的路径：{@code yyyyMMdd/uuid_original}。
     */
    private AssetDtos.AssetItem savePrototypeToConfiguredFolder(Long versionId, MultipartFile file, String relationCode) throws IOException {
        VersionContext ctx = assertVersionExists(versionId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        LocalDateTime now = LocalDateTime.now();
        String originalName = file.getOriginalFilename();
        String safeName = StringUtils.hasText(originalName) ? Paths.get(originalName).getFileName().toString() : "unnamed.bin";
        String dateDir = LocalDate.now().toString().replace("-", "");
        Path dirPath = Paths.get(prototypeBasePath, dateDir);
        Files.createDirectories(dirPath);
        String storedFileName = UUID.randomUUID() + "_" + safeName;
        Path savedPath = dirPath.resolve(storedFileName);
        file.transferTo(savedPath);
        String relativePath = Paths.get(dateDir, storedFileName).toString().replace("\\", "/");

        RequirementAssetEntity entity = new RequirementAssetEntity();
        entity.setProjectId(ctx.project().getId());
        entity.setVersionId(versionId);
        entity.setAssetCode(generateAssetCode(versionId));
        entity.setRelationCode(resolveRelationCode(relationCode, ctx.project(), ctx.version()));
        entity.setAssetType(TYPE_PROTOTYPE);
        entity.setTitle(safeName);
        entity.setFileName(safeName);
        entity.setFilePath(relativePath);
        entity.setFileSize(file.getSize());
        entity.setMimeType(file.getContentType());
        entity.setSource("UPLOAD");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        requirementAssetMapper.insert(entity);
        operationLogService.log("ASSET", entity.getId(), "UPLOAD_" + TYPE_PROTOTYPE, null, entity, null);
        log.info("prototype uploaded, assetId={}, versionId={}, fileName={}, path={}", entity.getId(), versionId, safeName, relativePath);
        return toItem(entity);
    }

    private VersionContext assertVersionExists(Long versionId) {
        ProjectVersionEntity version = projectVersionMapper.selectById(versionId);
        if (version == null || version.getIsDeleted() == 1) {
            throw new IllegalArgumentException("version not found");
        }
        ProjectEntity project = projectMapper.selectById(version.getProjectId());
        if (project == null || project.getIsDeleted() == 1) {
            throw new IllegalArgumentException("project not found");
        }
        return new VersionContext(project, version);
    }

    private String generateAssetCode(Long versionId) {
        return "AST-" + versionId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String buildRelationCode(ProjectEntity project, ProjectVersionEntity version) {
        return project.getCode() + ":" + version.getVersionNo();
    }

    private String resolveRelationCode(String relationCode, ProjectEntity project, ProjectVersionEntity version) {
        if (StringUtils.hasText(relationCode)) {
            return relationCode.trim();
        }
        return "RC-" + buildRelationCode(project, version) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private List<AssetDtos.AssetItem> toItems(List<RequirementAssetEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> projectIds = rows.stream()
                .map(RequirementAssetEntity::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> versionIds = rows.stream()
                .map(RequirementAssetEntity::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, ProjectEntity> projectMap = loadProjectsByIds(projectIds);
        Map<Long, ProjectVersionEntity> versionMap = loadVersionsByIds(versionIds);
        List<AssetDtos.AssetItem> out = new ArrayList<>(rows.size());
        for (RequirementAssetEntity item : rows) {
            out.add(toItem(item, projectMap.get(item.getProjectId()), versionMap.get(item.getVersionId())));
        }
        return out;
    }

    private Map<Long, ProjectEntity> loadProjectsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<ProjectEntity> list = projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>().in(ProjectEntity::getId, ids));
        return list.stream().collect(Collectors.toMap(ProjectEntity::getId, p -> p, (a, b) -> a));
    }

    private Map<Long, ProjectVersionEntity> loadVersionsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<ProjectVersionEntity> list = projectVersionMapper.selectList(
                new LambdaQueryWrapper<ProjectVersionEntity>().in(ProjectVersionEntity::getId, ids));
        return list.stream().collect(Collectors.toMap(ProjectVersionEntity::getId, v -> v, (a, b) -> a));
    }

    private AssetDtos.AssetItem toItem(RequirementAssetEntity entity) {
        ProjectEntity project = entity.getProjectId() == null ? null : projectMapper.selectById(entity.getProjectId());
        ProjectVersionEntity version = entity.getVersionId() == null ? null : projectVersionMapper.selectById(entity.getVersionId());
        return toItem(entity, project, version);
    }

    private AssetDtos.AssetItem toItem(RequirementAssetEntity entity, ProjectEntity project, ProjectVersionEntity version) {
        String projectName = project == null ? null : project.getName();
        String projectCode = project == null ? null : project.getCode();
        String versionName = version == null ? null : version.getName();
        String versionNo = version == null ? null : version.getVersionNo();
        return new AssetDtos.AssetItem(
                entity.getId(),
                entity.getProjectId(),
                entity.getVersionId(),
                entity.getAssetCode(),
                entity.getRelationCode(),
                entity.getAssetType(),
                entity.getTitle(),
                entity.getContent(),
                entity.getFilePath(),
                entity.getFileName(),
                entity.getFileSize(),
                entity.getMimeType(),
                entity.getSource(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                projectName,
                projectCode,
                versionName,
                versionNo
        );
    }

    private record VersionContext(ProjectEntity project, ProjectVersionEntity version) {
    }
}
