package com.testcase.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.common.StatusConstants;
import com.testcase.backend.domain.ProjectVersion;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.VersionDtos;
import com.testcase.backend.entity.ProjectEntity;
import com.testcase.backend.entity.ProjectVersionEntity;
import com.testcase.backend.mapper.ProjectMapper;
import com.testcase.backend.mapper.ProjectVersionMapper;
import com.testcase.backend.service.OperationLogService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class VersionController {
    private static final Logger log = LoggerFactory.getLogger(VersionController.class);
    private final ProjectMapper projectMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final OperationLogService operationLogService;

    public VersionController(ProjectMapper projectMapper, ProjectVersionMapper projectVersionMapper, OperationLogService operationLogService) {
        this.projectMapper = projectMapper;
        this.projectVersionMapper = projectVersionMapper;
        this.operationLogService = operationLogService;
    }

    @PostMapping("/projects/{projectId}/versions")
    public ApiResponse<ProjectVersion> createVersion(
            @PathVariable Long projectId,
            @Valid @RequestBody VersionDtos.CreateVersionRequest request
    ) {
        log.info("create version request, projectId={}, versionNo={}", projectId, request.versionNo());
        ensureProjectExists(projectId);
        var count = projectVersionMapper.selectCount(
                new LambdaQueryWrapper<ProjectVersionEntity>()
                        .eq(ProjectVersionEntity::getProjectId, projectId)
                        .eq(ProjectVersionEntity::getVersionNo, request.versionNo())
                        .eq(ProjectVersionEntity::getIsDeleted, 0)
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("version_no already exists in this project");
        }

        ProjectVersionEntity entity = new ProjectVersionEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setProjectId(projectId);
        entity.setVersionNo(request.versionNo());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(StatusConstants.Version.DRAFT);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        projectVersionMapper.insert(entity);
        log.info("version created, versionId={}, projectId={}", entity.getId(), projectId);
        operationLogService.log("VERSION", entity.getId(), "CREATE", null, entity, null);

        return ApiResponse.success(toDomain(entity));
    }

    @GetMapping("/projects/{projectId}/versions")
    public ApiResponse<PagedResult<ProjectVersion>> listVersions(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        log.info("list versions request, projectId={}, pageNo={}, pageSize={}, keyword={}, status={}, sortBy={}, sortOrder={}",
                projectId, pageNo, pageSize, keyword, status, sortBy, sortOrder);
        ensureProjectExists(projectId);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);

        var wrapper = new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getProjectId, projectId)
                .eq(ProjectVersionEntity::getIsDeleted, 0);

        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim();
            wrapper.and(w -> w.like(ProjectVersionEntity::getVersionNo, normalized)
                    .or()
                    .like(ProjectVersionEntity::getName, normalized));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ProjectVersionEntity::getStatus, status.trim());
        }

        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy == null ? "" : sortBy) {
            case "versionNo" -> wrapper.orderBy(true, asc, ProjectVersionEntity::getVersionNo);
            case "status" -> wrapper.orderBy(true, asc, ProjectVersionEntity::getStatus);
            case "createdAt" -> wrapper.orderBy(true, asc, ProjectVersionEntity::getCreatedAt);
            default -> wrapper.orderBy(true, asc, ProjectVersionEntity::getId);
        }

        Page<ProjectVersionEntity> page = projectVersionMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<ProjectVersion> records = page.getRecords().stream().map(this::toDomain).toList();
        return ApiResponse.success(new PagedResult<>(records, safePageNo, safePageSize, page.getTotal()));
    }

    @GetMapping("/versions")
    public ApiResponse<PagedResult<ProjectVersion>> listAllVersions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        log.info("list all versions request, pageNo={}, pageSize={}, projectId={}, keyword={}, status={}, sortBy={}, sortOrder={}",
                pageNo, pageSize, projectId, keyword, status, sortBy, sortOrder);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);

        var wrapper = new LambdaQueryWrapper<ProjectVersionEntity>()
                .eq(ProjectVersionEntity::getIsDeleted, 0);

        if (projectId != null && projectId > 0) {
            wrapper.eq(ProjectVersionEntity::getProjectId, projectId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim();
            wrapper.and(w -> w.like(ProjectVersionEntity::getVersionNo, normalized)
                    .or()
                    .like(ProjectVersionEntity::getName, normalized));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ProjectVersionEntity::getStatus, status.trim());
        }

        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy == null ? "" : sortBy) {
            case "versionNo" -> wrapper.orderBy(true, asc, ProjectVersionEntity::getVersionNo);
            case "status" -> wrapper.orderBy(true, asc, ProjectVersionEntity::getStatus);
            case "createdAt" -> wrapper.orderBy(true, asc, ProjectVersionEntity::getCreatedAt);
            default -> wrapper.orderBy(true, asc, ProjectVersionEntity::getId);
        }

        Page<ProjectVersionEntity> page = projectVersionMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<ProjectVersion> records = page.getRecords().stream().map(this::toDomain).toList();
        return ApiResponse.success(new PagedResult<>(records, safePageNo, safePageSize, page.getTotal()));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<ProjectVersion> getVersion(@PathVariable Long versionId) {
        log.info("get version request, versionId={}", versionId);
        var entity = projectVersionMapper.selectOne(
                new LambdaQueryWrapper<ProjectVersionEntity>()
                        .eq(ProjectVersionEntity::getId, versionId)
                        .eq(ProjectVersionEntity::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new IllegalArgumentException("version not found");
        }
        return ApiResponse.success(toDomain(entity));
    }

    @PutMapping("/versions/{versionId}")
    public ApiResponse<ProjectVersion> updateVersion(
            @PathVariable Long versionId,
            @Valid @RequestBody VersionDtos.UpdateVersionRequest request
    ) {
        log.info("update version request, versionId={}", versionId);
        LocalDateTime now = LocalDateTime.now();
        var entity = projectVersionMapper.selectById(versionId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("version not found");
        }
        var before = toDomain(entity);
        entity.setVersionNo(request.versionNo());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(request.status());
        entity.setUpdatedBy(1L);
        entity.setUpdatedAt(now);
        projectVersionMapper.updateById(entity);
        log.info("version updated, versionId={}", versionId);
        operationLogService.log("VERSION", versionId, "UPDATE", before, toDomain(entity), null);
        return ApiResponse.success(toDomain(entity));
    }

    @DeleteMapping("/versions/{versionId}")
    public ApiResponse<Void> deleteVersion(@PathVariable Long versionId) {
        log.info("delete version request, versionId={}", versionId);
        LocalDateTime now = LocalDateTime.now();
        int updated = projectVersionMapper.update(
                null,
                new LambdaUpdateWrapper<ProjectVersionEntity>()
                        .set(ProjectVersionEntity::getIsDeleted, 1)
                        .set(ProjectVersionEntity::getUpdatedBy, 1L)
                        .set(ProjectVersionEntity::getUpdatedAt, now)
                        .eq(ProjectVersionEntity::getId, versionId)
                        .eq(ProjectVersionEntity::getIsDeleted, 0)
        );
        if (updated == 0) {
            throw new IllegalArgumentException("version not found");
        }
        log.info("version deleted, versionId={}", versionId);
        operationLogService.log("VERSION", versionId, "DELETE", null, null, null);
        return ApiResponse.success(null);
    }

    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<ProjectVersion> publishVersion(@PathVariable Long versionId) {
        log.info("publish version request, versionId={}", versionId);
        LocalDateTime now = LocalDateTime.now();
        var entity = projectVersionMapper.selectById(versionId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("version not found");
        }
        entity.setStatus(StatusConstants.Version.PUBLISHED);
        entity.setUpdatedBy(1L);
        entity.setUpdatedAt(now);
        projectVersionMapper.updateById(entity);
        log.info("version published, versionId={}", versionId);
        operationLogService.log("VERSION", versionId, "PUBLISH", null, toDomain(entity), null);
        return ApiResponse.success(toDomain(entity));
    }

    @GetMapping("/versions/compare")
    public ApiResponse<String> compareVersions(
            @RequestParam Long leftVersionId,
            @RequestParam Long rightVersionId
    ) {
        log.info("compare versions request, leftVersionId={}, rightVersionId={}", leftVersionId, rightVersionId);
        var left = projectVersionMapper.selectOne(
                new LambdaQueryWrapper<ProjectVersionEntity>()
                        .eq(ProjectVersionEntity::getId, leftVersionId)
                        .eq(ProjectVersionEntity::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        var right = projectVersionMapper.selectOne(
                new LambdaQueryWrapper<ProjectVersionEntity>()
                        .eq(ProjectVersionEntity::getId, rightVersionId)
                        .eq(ProjectVersionEntity::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        if (left == null || right == null) {
            throw new IllegalArgumentException("version not found");
        }
        var summary = "compare " + left.getVersionNo() + " vs " + right.getVersionNo() + " (MVP placeholder)";
        return ApiResponse.success(summary);
    }

    private void ensureProjectExists(Long projectId) {
        var project = projectMapper.selectOne(
                new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getId, projectId)
                        .eq(ProjectEntity::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        if (project == null) {
            throw new IllegalArgumentException("project not found");
        }
    }

    private ProjectVersion toDomain(ProjectVersionEntity entity) {
        return new ProjectVersion(
                entity.getId(),
                entity.getProjectId(),
                entity.getVersionNo(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getIsDeleted() != null && entity.getIsDeleted() == 1,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
