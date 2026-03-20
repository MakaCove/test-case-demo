package com.testcase.backend.controller;

import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.TestCaseDtos;
import com.testcase.backend.service.TestCaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test-cases")
public class TestCaseController {
    private static final Logger log = LoggerFactory.getLogger(TestCaseController.class);
    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @GetMapping
    public ApiResponse<PagedResult<TestCaseDtos.CaseItem>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) Long sourceTaskId,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String featureName,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(testCaseService.search(
                projectId, versionId, sourceTaskId, moduleName, featureName, priority, executionStatus, reviewStatus, keyword, pageNo, pageSize
        ));
    }

    @PostMapping
    public ApiResponse<TestCaseDtos.CaseItem> create(HttpServletRequest request, @Valid @RequestBody TestCaseDtos.CreateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        log.info("create test case, projectId={}, versionId={}, userId={}", body.projectId(), body.versionId(), userId);
        return ApiResponse.success(testCaseService.create(body, userId));
    }

    @GetMapping("/{caseId}")
    public ApiResponse<TestCaseDtos.CaseDetail> detail(@PathVariable Long caseId) {
        return ApiResponse.success(testCaseService.detail(caseId));
    }

    @PutMapping("/{caseId}")
    public ApiResponse<TestCaseDtos.CaseItem> update(HttpServletRequest request, @PathVariable Long caseId, @Valid @RequestBody TestCaseDtos.UpdateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        return ApiResponse.success(testCaseService.update(caseId, body, userId));
    }

    @DeleteMapping("/{caseId}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long caseId) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        testCaseService.delete(caseId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(HttpServletRequest request, @Valid @RequestBody TestCaseDtos.BatchDeleteRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        testCaseService.batchDelete(body, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch-update")
    public ApiResponse<Void> batchUpdate(HttpServletRequest request, @RequestBody TestCaseDtos.BatchUpdateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        testCaseService.batchUpdate(body, userId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{caseId}/status")
    public ApiResponse<TestCaseDtos.CaseItem> updateStatus(
            HttpServletRequest request,
            @PathVariable Long caseId,
            @RequestBody(required = false) TestCaseDtos.UpdateStatusRequest body
    ) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        return ApiResponse.success(testCaseService.updateStatus(caseId, body == null ? new TestCaseDtos.UpdateStatusRequest(null, null, null, null) : body, userId));
    }

    @PostMapping("/materialize-from-task/{taskId}")
    public ApiResponse<Integer> materializeFromTask(
            HttpServletRequest request,
            @PathVariable Long taskId,
            @RequestBody(required = false) TestCaseDtos.MaterializeFromTaskRequest body
    ) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        int created = testCaseService.materializeFromTask(taskId, body == null ? null : body.count(), userId);
        return ApiResponse.success(created);
    }
}

