package com.testcase.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.common.StatusConstants;
import com.testcase.backend.dto.ModelConfigDtos;
import com.testcase.backend.entity.ModelConfigEntity;
import com.testcase.backend.mapper.ModelConfigMapper;
import com.testcase.backend.service.ModelConnectivityService;
import com.testcase.backend.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/model-configs")
public class ModelConfigController {
    private static final Logger log = LoggerFactory.getLogger(ModelConfigController.class);
    private final ModelConfigMapper modelConfigMapper;
    private final OperationLogService operationLogService;
    private final ModelConnectivityService modelConnectivityService;

    public ModelConfigController(ModelConfigMapper modelConfigMapper, OperationLogService operationLogService, ModelConnectivityService modelConnectivityService) {
        this.modelConfigMapper = modelConfigMapper;
        this.operationLogService = operationLogService;
        this.modelConnectivityService = modelConnectivityService;
    }

    @GetMapping
    public ApiResponse<List<ModelConfigEntity>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status
    ) {
        var wrapper = new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getIsDeleted, 0)
                .orderByDesc(ModelConfigEntity::getId);
        if (name != null && !name.isBlank()) {
            wrapper.like(ModelConfigEntity::getName, name.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ModelConfigEntity::getStatus, status.trim());
        }
        return ApiResponse.success(modelConfigMapper.selectList(wrapper));
    }

    @PostMapping
    public ApiResponse<ModelConfigEntity> create(HttpServletRequest request, @Valid @RequestBody ModelConfigDtos.CreateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setName(body.name().trim());
        entity.setProvider(body.provider().trim());
        entity.setBaseUrl(body.baseUrl().trim());
        entity.setModelKey(body.modelKey().trim());
        entity.setApiKeyEncrypted(body.apiKeyEncrypted());
        entity.setTemperature(body.temperature());
        entity.setMaxTokens(body.maxTokens());
        entity.setStatus(StatusConstants.Switch.ENABLED);
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        modelConfigMapper.insert(entity);
        operationLogService.log("MODEL_CONFIG", entity.getId(), "CREATE", null, entity, null);
        return ApiResponse.success(entity);
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigEntity> update(HttpServletRequest request, @PathVariable Long id, @Valid @RequestBody ModelConfigDtos.UpdateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        ModelConfigEntity entity = modelConfigMapper.selectById(id);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("model config not found");
        }
        ModelConfigEntity before = entity;
        entity.setName(body.name().trim());
        entity.setProvider(body.provider().trim());
        entity.setBaseUrl(body.baseUrl().trim());
        entity.setModelKey(body.modelKey().trim());
        entity.setApiKeyEncrypted(body.apiKeyEncrypted());
        entity.setTemperature(body.temperature());
        entity.setMaxTokens(body.maxTokens());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(now);
        modelConfigMapper.updateById(entity);
        operationLogService.log("MODEL_CONFIG", id, "UPDATE", before, entity, null);
        return ApiResponse.success(entity);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        int updated = modelConfigMapper.update(null, new LambdaUpdateWrapper<ModelConfigEntity>()
                .set(ModelConfigEntity::getIsDeleted, 1)
                .set(ModelConfigEntity::getUpdatedBy, userId)
                .set(ModelConfigEntity::getUpdatedAt, now)
                .eq(ModelConfigEntity::getId, id)
                .eq(ModelConfigEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("model config not found");
        }
        operationLogService.log("MODEL_CONFIG", id, "DELETE", null, null, null);
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

    @PostMapping("/{id}/test-connection")
    public ApiResponse<String> testConnection(@PathVariable Long id, @RequestBody(required = false) ModelConfigDtos.TestConnectionRequest body) {
        ModelConfigEntity entity = modelConfigMapper.selectById(id);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("model config not found");
        }
        String prompt = body == null ? null : body.prompt();
        String result = modelConnectivityService.testConnection(entity, prompt);
        log.info("test connection ok, modelConfigId={}, provider={}, baseUrl={}", id, entity.getProvider(), entity.getBaseUrl());
        return ApiResponse.success(result);
    }

    private ApiResponse<Void> setStatus(HttpServletRequest request, Long id, String status) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        int updated = modelConfigMapper.update(null, new LambdaUpdateWrapper<ModelConfigEntity>()
                .set(ModelConfigEntity::getStatus, status)
                .set(ModelConfigEntity::getUpdatedBy, userId)
                .set(ModelConfigEntity::getUpdatedAt, now)
                .eq(ModelConfigEntity::getId, id)
                .eq(ModelConfigEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("model config not found");
        }
        operationLogService.log("MODEL_CONFIG", id, status, null, null, null);
        return ApiResponse.success(null);
    }
}

