package com.testcase.backend.controller;

import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.GenerationTaskDtos;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.service.GenerationTaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/generation-tasks")
public class GenerationTaskController {
    private static final Logger log = LoggerFactory.getLogger(GenerationTaskController.class);
    private final GenerationTaskService generationTaskService;

    public GenerationTaskController(GenerationTaskService generationTaskService) {
        this.generationTaskService = generationTaskService;
    }

    @PostMapping
    public ApiResponse<GenerationTaskDtos.TaskItem> submit(
            HttpServletRequest request,
            @Valid @RequestBody GenerationTaskDtos.SubmitTaskRequest body
    ) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) {
            userId = 1L;
        }
        log.info("submit generation task, projectId={}, versionId={}, userId={}", body.projectId(), body.versionId(), userId);
        return ApiResponse.success(generationTaskService.submit(body, userId));
    }

    @GetMapping
    public ApiResponse<PagedResult<GenerationTaskDtos.TaskItem>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(generationTaskService.search(projectId, versionId, status, pageNo, pageSize));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<GenerationTaskDtos.TaskDetail> detail(@PathVariable Long taskId) {
        return ApiResponse.success(generationTaskService.detail(taskId));
    }

    @PutMapping("/{taskId}")
    public ApiResponse<GenerationTaskDtos.TaskItem> update(
            HttpServletRequest request,
            @PathVariable Long taskId,
            @Valid @RequestBody GenerationTaskDtos.UpdateTaskRequest body
    ) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) {
            userId = 1L;
        }
        return ApiResponse.success(generationTaskService.update(taskId, body, userId));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<Void> cancel(HttpServletRequest request, @PathVariable Long taskId) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) {
            userId = 1L;
        }
        generationTaskService.cancel(taskId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/interrupt")
    public ApiResponse<Void> interrupt(
            HttpServletRequest request,
            @PathVariable Long taskId,
            @RequestBody(required = false) GenerationTaskDtos.InterruptRequest body
    ) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) {
            userId = 1L;
        }
        String reason = body == null ? null : body.reason();
        generationTaskService.interrupt(taskId, reason, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<GenerationTaskDtos.TaskItem> retry(HttpServletRequest request, @PathVariable Long taskId) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) {
            userId = 1L;
        }
        return ApiResponse.success(generationTaskService.retry(taskId, userId));
    }

    @PostMapping("/{taskId}/start")
    public ApiResponse<Void> start(HttpServletRequest request, @PathVariable Long taskId) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) {
            userId = 1L;
        }
        generationTaskService.start(taskId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(
            HttpServletRequest request,
            @RequestBody(required = false) GenerationTaskDtos.BatchDeleteRequest body
    ) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) {
            userId = 1L;
        }
        generationTaskService.batchDelete(body == null ? null : body.taskIds(), userId);
        return ApiResponse.success(null);
    }
}

