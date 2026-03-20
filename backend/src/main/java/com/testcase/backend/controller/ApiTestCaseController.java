package com.testcase.backend.controller;

import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.ApiTestCaseDtos;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.service.ApiTestCaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/api-test-cases")
public class ApiTestCaseController {

    private final ApiTestCaseService apiTestCaseService;

    public ApiTestCaseController(ApiTestCaseService apiTestCaseService) {
        this.apiTestCaseService = apiTestCaseService;
    }

    @GetMapping
    public ApiResponse<PagedResult<ApiTestCaseDtos.CaseItem>> list(
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
        return ApiResponse.success(apiTestCaseService.search(
                projectId, versionId, sourceTaskId, moduleName, featureName, priority, executionStatus, reviewStatus, keyword, pageNo, pageSize
        ));
    }

    @PostMapping
    public ApiResponse<ApiTestCaseDtos.CaseItem> create(HttpServletRequest request, @Valid @RequestBody ApiTestCaseDtos.CreateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        return ApiResponse.success(apiTestCaseService.create(body, userId));
    }

    @GetMapping("/{caseId}")
    public ApiResponse<ApiTestCaseDtos.CaseDetail> detail(@PathVariable Long caseId) {
        return ApiResponse.success(apiTestCaseService.detail(caseId));
    }

    @PutMapping("/{caseId}")
    public ApiResponse<ApiTestCaseDtos.CaseItem> update(HttpServletRequest request, @PathVariable Long caseId, @Valid @RequestBody ApiTestCaseDtos.UpdateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        return ApiResponse.success(apiTestCaseService.update(caseId, body, userId));
    }

    @DeleteMapping("/{caseId}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long caseId) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        apiTestCaseService.delete(caseId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(HttpServletRequest request, @Valid @RequestBody ApiTestCaseDtos.BatchDeleteRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        apiTestCaseService.batchDelete(body, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch-update")
    public ApiResponse<Void> batchUpdate(HttpServletRequest request, @RequestBody ApiTestCaseDtos.BatchUpdateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        apiTestCaseService.batchUpdate(body, userId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{caseId}/status")
    public ApiResponse<ApiTestCaseDtos.CaseItem> updateStatus(
            HttpServletRequest request,
            @PathVariable Long caseId,
            @RequestBody(required = false) ApiTestCaseDtos.UpdateStatusRequest body
    ) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        return ApiResponse.success(apiTestCaseService.updateStatus(caseId, body == null ? new ApiTestCaseDtos.UpdateStatusRequest(null, null, null, null) : body, userId));
    }
}
