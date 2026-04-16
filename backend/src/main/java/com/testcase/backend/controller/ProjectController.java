package com.testcase.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.domain.Project;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.ProjectDtos;
import com.testcase.backend.entity.ProjectEntity;
import com.testcase.backend.mapper.ProjectMapper;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 项目主数据：创建（code 唯一）、分页列表、详情、更新、软删、批量归档。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);
    private final ProjectMapper projectMapper;
    private final OperationLogService operationLogService;

    public ProjectController(ProjectMapper projectMapper, OperationLogService operationLogService) {
        this.projectMapper = projectMapper;
        this.operationLogService = operationLogService;
    }

    @PostMapping
    public ApiResponse<Project> createProject(@Valid @RequestBody ProjectDtos.CreateProjectRequest request) {
        log.info("create project request, code={}, name={}", request.code(), request.name());
        var count = projectMapper.selectCount(
                new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getIsDeleted, 0)
                        .eq(ProjectEntity::getCode, request.code())
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("project code already exists");
        }

        ProjectEntity entity = new ProjectEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setName(request.name());
        entity.setCode(request.code());
        entity.setDescription(request.description());
        entity.setOwnerUserId(1L);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        projectMapper.insert(entity);
        log.info("project created, projectId={}", entity.getId());
        operationLogService.log("PROJECT", entity.getId(), "CREATE", null, entity, null);

        return ApiResponse.success(toDomain(entity));
    }

    /** 支持按名称、编码筛选及 sortBy/sortOrder（id|name|code|createdAt） */
    @GetMapping
    public ApiResponse<PagedResult<Project>> listProjects(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        log.info("list projects request, pageNo={}, pageSize={}, name={}, code={}, sortBy={}, sortOrder={}",
                pageNo, pageSize, name, code, sortBy, sortOrder);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);

        var wrapper = new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getIsDeleted, 0);

        if (StringUtils.hasText(name)) {
            wrapper.like(ProjectEntity::getName, name.trim());
        }
        if (StringUtils.hasText(code)) {
            wrapper.like(ProjectEntity::getCode, code.trim());
        }

        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy == null ? "" : sortBy) {
            case "name" -> wrapper.orderBy(true, asc, ProjectEntity::getName);
            case "code" -> wrapper.orderBy(true, asc, ProjectEntity::getCode);
            case "createdAt" -> wrapper.orderBy(true, asc, ProjectEntity::getCreatedAt);
            default -> wrapper.orderBy(true, asc, ProjectEntity::getId);
        }

        Page<ProjectEntity> page = projectMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<Project> records = new ArrayList<>();
        for (ProjectEntity item : page.getRecords()) {
            records.add(toDomain(item));
        }
        long total = page.getTotal();
        return ApiResponse.success(new PagedResult<>(records, safePageNo, safePageSize, total));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<Project> getProject(@PathVariable Long projectId) {
        log.info("get project request, projectId={}", projectId);
        var entity = projectMapper.selectOne(
                new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getId, projectId)
                        .eq(ProjectEntity::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new IllegalArgumentException("project not found");
        }
        return ApiResponse.success(toDomain(entity));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<Project> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDtos.UpdateProjectRequest request
    ) {
        log.info("update project request, projectId={}", projectId);
        LocalDateTime now = LocalDateTime.now();
        var entity = projectMapper.selectById(projectId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("project not found");
        }
        var before = toDomain(entity);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setUpdatedBy(1L);
        entity.setUpdatedAt(now);
        projectMapper.updateById(entity);
        log.info("project updated, projectId={}", projectId);
        operationLogService.log("PROJECT", projectId, "UPDATE", before, toDomain(entity), null);
        return ApiResponse.success(toDomain(entity));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> deleteProject(@PathVariable Long projectId) {
        log.info("delete project request, projectId={}", projectId);
        LocalDateTime now = LocalDateTime.now();
        int updated = projectMapper.update(
                null,
                new LambdaUpdateWrapper<ProjectEntity>()
                        .set(ProjectEntity::getIsDeleted, 1)
                        .set(ProjectEntity::getUpdatedBy, 1L)
                        .set(ProjectEntity::getUpdatedAt, now)
                        .eq(ProjectEntity::getId, projectId)
                        .eq(ProjectEntity::getIsDeleted, 0)
        );
        if (updated == 0) {
            throw new IllegalArgumentException("project not found");
        }
        log.info("project deleted, projectId={}", projectId);
        operationLogService.log("PROJECT", projectId, "DELETE", null, null, null);
        return ApiResponse.success(null);
    }

    /**
     * MVP 仅支持 {@code ARCHIVE}：批量软删项目。
     */
    @PostMapping("/batch-update")
    public ApiResponse<Void> batchUpdate(@RequestBody ProjectDtos.BatchUpdateProjectRequest request) {
        log.info("batch update projects request, action={}, size={}", request.action(), request.ids() == null ? 0 : request.ids().size());
        if (request.ids() == null || request.ids().isEmpty()) {
            throw new IllegalArgumentException("ids is required");
        }
        if (!"ARCHIVE".equalsIgnoreCase(request.action())) {
            throw new IllegalArgumentException("only ARCHIVE action is supported in MVP");
        }
        LocalDateTime now = LocalDateTime.now();
        projectMapper.update(
                null,
                new LambdaUpdateWrapper<ProjectEntity>()
                        .set(ProjectEntity::getIsDeleted, 1)
                        .set(ProjectEntity::getUpdatedBy, 1L)
                        .set(ProjectEntity::getUpdatedAt, now)
                        .in(ProjectEntity::getId, request.ids())
                        .eq(ProjectEntity::getIsDeleted, 0)
        );
        log.info("batch archive projects success, ids={}", request.ids());
        request.ids().forEach(id -> operationLogService.log("PROJECT", id, "BATCH_DELETE", null, null, "batch archive"));
        return ApiResponse.success(null);
    }

    /** 领域模型展示用 owner 暂固定为 admin */
    private Project toDomain(ProjectEntity entity) {
        return new Project(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                "admin",
                entity.getIsDeleted() != null && entity.getIsDeleted() == 1,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
