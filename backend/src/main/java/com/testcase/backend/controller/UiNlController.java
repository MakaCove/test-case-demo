package com.testcase.backend.controller;

import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.UiNlDtos;
import com.testcase.backend.service.UiNlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class UiNlController {
    private final UiNlService uiNlService;

    public UiNlController(UiNlService uiNlService) {
        this.uiNlService = uiNlService;
    }

    @PostMapping("/ui-nl-cases")
    public ApiResponse<UiNlDtos.CaseItem> createCase(HttpServletRequest request, @Valid @RequestBody UiNlDtos.CreateCaseRequest body) {
        return ApiResponse.success(uiNlService.createCase(body, loginUserId(request)));
    }

    @GetMapping("/ui-nl-cases")
    public ApiResponse<PagedResult<UiNlDtos.CaseItem>> listCases(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(uiNlService.listCases(projectId, versionId, keyword, status, pageNo, pageSize));
    }

    @GetMapping("/ui-nl-cases/{id}")
    public ApiResponse<UiNlDtos.CaseItem> getCase(@PathVariable Long id) {
        return ApiResponse.success(uiNlService.detailCase(id));
    }

    @PutMapping("/ui-nl-cases/{id}")
    public ApiResponse<UiNlDtos.CaseItem> updateCase(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody UiNlDtos.UpdateCaseRequest body
    ) {
        return ApiResponse.success(uiNlService.updateCase(id, body, loginUserId(request)));
    }

    @DeleteMapping("/ui-nl-cases/{id}")
    public ApiResponse<Void> deleteCase(HttpServletRequest request, @PathVariable Long id) {
        uiNlService.deleteCase(id, loginUserId(request));
        return ApiResponse.success(null);
    }

    @PostMapping("/ui-nl-tasks")
    public ApiResponse<UiNlDtos.TaskItem> createTask(HttpServletRequest request, @Valid @RequestBody UiNlDtos.CreateTaskRequest body) {
        return ApiResponse.success(uiNlService.createTask(body, loginUserId(request)));
    }

    @GetMapping("/ui-nl-tasks")
    public ApiResponse<PagedResult<UiNlDtos.TaskItem>> listTasks(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String caseTitle,
            @RequestParam(required = false) String lastExecStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(uiNlService.listTasks(projectId, versionId, status, caseTitle, lastExecStatus, pageNo, pageSize));
    }

    @GetMapping("/ui-nl-tasks/{id}")
    public ApiResponse<UiNlDtos.TaskItem> getTask(@PathVariable Long id) {
        return ApiResponse.success(uiNlService.detailTask(id));
    }

    @PutMapping("/ui-nl-tasks/{id}")
    public ApiResponse<UiNlDtos.TaskItem> updateTask(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody UiNlDtos.UpdateTaskRequest body
    ) {
        return ApiResponse.success(uiNlService.updateTask(id, body, loginUserId(request)));
    }

    @DeleteMapping("/ui-nl-tasks/{id}")
    public ApiResponse<Void> deleteTask(HttpServletRequest request, @PathVariable Long id) {
        uiNlService.deleteTask(id, loginUserId(request));
        return ApiResponse.success(null);
    }

    @PostMapping("/ui-nl-tasks/{id}/execute")
    public ApiResponse<UiNlDtos.TaskItem> executeTask(HttpServletRequest request, @PathVariable Long id) {
        // 语义：生成步骤（入队等待 planner 生成并入库），不提交 runner 执行
        return ApiResponse.success(uiNlService.executeTask(id, loginUserId(request)));
    }

    @PostMapping("/ui-nl-tasks/{id}/interrupt")
    public ApiResponse<UiNlDtos.TaskItem> interruptTask(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) UiNlDtos.InterruptTaskRequest body
    ) {
        return ApiResponse.success(uiNlService.interruptTask(id, body == null ? null : body.reason(), loginUserId(request)));
    }

    @PostMapping("/ui-nl-tasks/{id}/run")
    public ApiResponse<UiNlDtos.TaskItem> runTask(HttpServletRequest request, @PathVariable Long id) {
        // 语义：提交 runner 真执行（依赖已生成/确认的步骤）
        return ApiResponse.success(uiNlService.runTask(id, loginUserId(request)));
    }

    @PostMapping("/ui-nl-tasks/{id}/cancel")
    public ApiResponse<Void> cancelTask(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) UiNlDtos.CancelTaskRequest body
    ) {
        uiNlService.cancelTask(id, body == null ? null : body.reason(), loginUserId(request));
        return ApiResponse.success(null);
    }

    @PostMapping("/ui-nl-tasks/{id}/retry")
    public ApiResponse<UiNlDtos.TaskItem> retryTask(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.success(uiNlService.retryTask(id, loginUserId(request)));
    }

    @GetMapping("/ui-nl-tasks/{id}/steps")
    public ApiResponse<List<UiNlDtos.StepItem>> listSteps(
            @PathVariable Long id,
            @RequestParam(required = false) String phase
    ) {
        return ApiResponse.success(uiNlService.listTaskSteps(id, phase));
    }

    @GetMapping("/ui-nl-steps/{stepId}")
    public ApiResponse<UiNlDtos.StepItem> detailStep(
            @PathVariable Long stepId,
            @RequestParam(required = false) String phase
    ) {
        return ApiResponse.success(uiNlService.detailStep(stepId, phase));
    }

    @PutMapping("/ui-nl-plan-steps/{stepId}")
    public ApiResponse<UiNlDtos.StepItem> updatePlanStep(
            @PathVariable Long stepId,
            @Valid @RequestBody UiNlDtos.UpdatePlanStepRequest body
    ) {
        return ApiResponse.success(uiNlService.updatePlanStep(stepId, body));
    }

    @GetMapping("/ui-nl-exec-steps/{stepId}/screenshot")
    public ResponseEntity<byte[]> execStepScreenshot(@PathVariable Long stepId) {
        return uiNlService.execStepScreenshot(stepId);
    }

    @GetMapping("/ui-nl-reports")
    public ApiResponse<PagedResult<UiNlDtos.ReportItem>> listReports(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(uiNlService.listReports(projectId, versionId, status, pageNo, pageSize));
    }

    @GetMapping("/ui-nl-reports/{id}")
    public ApiResponse<UiNlDtos.ReportItem> detailReport(@PathVariable Long id) {
        return ApiResponse.success(uiNlService.detailReport(id));
    }

    @GetMapping("/ui-nl-reports/{id}/html")
    public ResponseEntity<Resource> downloadReportHtml(@PathVariable Long id) {
        UiNlService.ReportHtmlPayload payload = uiNlService.reportHtml(id);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(payload.fileName()).build().toString())
                .body(payload.resource());
    }

    private Long loginUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("loginUserId");
        return userId == null ? 1L : userId;
    }
}
