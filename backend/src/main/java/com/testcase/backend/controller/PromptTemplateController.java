package com.testcase.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.common.StatusConstants;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.PromptTemplateDtos;
import com.testcase.backend.entity.PromptTemplateEntity;
import com.testcase.backend.mapper.PromptTemplateMapper;
import com.testcase.backend.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

/**
 * 提示词模板：按名称/状态/作用域分页；创建时默认 GLOBAL 作用域；更新递增 versionNo；软删；启用/禁用。
 */
@RestController
@RequestMapping("/api/v1/prompt-templates")
public class PromptTemplateController {
    private final PromptTemplateMapper promptTemplateMapper;
    private final OperationLogService operationLogService;

    public PromptTemplateController(PromptTemplateMapper promptTemplateMapper, OperationLogService operationLogService) {
        this.promptTemplateMapper = promptTemplateMapper;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PagedResult<PromptTemplateEntity>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId
    ) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getIsDeleted, 0)
                .orderByDesc(PromptTemplateEntity::getId);
        if (name != null && !name.isBlank()) {
            wrapper.like(PromptTemplateEntity::getName, name.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PromptTemplateEntity::getStatus, status.trim());
        }
        if (scopeType != null && !scopeType.isBlank()) {
            wrapper.eq(PromptTemplateEntity::getScopeType, scopeType.trim());
        }
        if (scopeId != null) {
            wrapper.eq(PromptTemplateEntity::getScopeId, scopeId);
        }
        Page<PromptTemplateEntity> page = promptTemplateMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        return ApiResponse.success(new PagedResult<>(page.getRecords(), safePageNo, safePageSize, page.getTotal()));
    }

    @PostMapping
    public ApiResponse<PromptTemplateEntity> create(HttpServletRequest request, @Valid @RequestBody PromptTemplateDtos.CreateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setName(body.name().trim());
        entity.setScopeType(body.scopeType() == null || body.scopeType().isBlank() ? "GLOBAL" : body.scopeType().trim());
        entity.setScopeId(body.scopeId());
        entity.setVersionNo(1);
        entity.setContent(body.content());
        entity.setStatus(StatusConstants.Switch.ENABLED);
        entity.setRemark(body.remark());
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        promptTemplateMapper.insert(entity);
        operationLogService.log("PROMPT_TEMPLATE", entity.getId(), "CREATE", null, entity, null);
        return ApiResponse.success(entity);
    }

    @PutMapping("/{id}")
    public ApiResponse<PromptTemplateEntity> update(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody PromptTemplateDtos.UpdateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        PromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("prompt template not found");
        }
        PromptTemplateEntity before = entity;
        entity.setName(body.name().trim());
        entity.setContent(body.content());
        entity.setRemark(body.remark());
        entity.setVersionNo((entity.getVersionNo() == null ? 1 : entity.getVersionNo()) + 1);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(now);
        promptTemplateMapper.updateById(entity);
        operationLogService.log("PROMPT_TEMPLATE", id, "UPDATE", before, entity, null);
        return ApiResponse.success(entity);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        int updated = promptTemplateMapper.update(null, new LambdaUpdateWrapper<PromptTemplateEntity>()
                .set(PromptTemplateEntity::getIsDeleted, 1)
                .set(PromptTemplateEntity::getUpdatedBy, userId)
                .set(PromptTemplateEntity::getUpdatedAt, now)
                .eq(PromptTemplateEntity::getId, id)
                .eq(PromptTemplateEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("prompt template not found");
        }
        operationLogService.log("PROMPT_TEMPLATE", id, "DELETE", null, null, null);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(HttpServletRequest request, @PathVariable Long id) {
        return setStatus(request, id, StatusConstants.Switch.ENABLED);
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(HttpServletRequest request, @PathVariable Long id) {
        return setStatus(request, id, StatusConstants.Switch.DISABLED);
    }

    private ApiResponse<Void> setStatus(HttpServletRequest request, Long id, String status) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        int updated = promptTemplateMapper.update(null, new LambdaUpdateWrapper<PromptTemplateEntity>()
                .set(PromptTemplateEntity::getStatus, status)
                .set(PromptTemplateEntity::getUpdatedBy, userId)
                .set(PromptTemplateEntity::getUpdatedAt, now)
                .eq(PromptTemplateEntity::getId, id)
                .eq(PromptTemplateEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("prompt template not found");
        }
        operationLogService.log("PROMPT_TEMPLATE", id, status, null, null, null);
        return ApiResponse.success(null);
    }
}
