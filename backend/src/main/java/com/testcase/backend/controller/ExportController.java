package com.testcase.backend.controller;

import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.ExportDtos;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {
    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    public ApiResponse<PagedResult<ExportDtos.ExportItem>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long versionId,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(exportService.search(projectId, versionId, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<ExportDtos.ExportItem> create(HttpServletRequest request, @Valid @RequestBody ExportDtos.CreateRequest body) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        return ApiResponse.success(exportService.createAndRun(body, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExportDtos.ExportItem> detail(@PathVariable Long id) {
        return ApiResponse.success(exportService.detail(id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<ExportDtos.ExportItem> retry(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("loginUserId");
        if (userId == null) userId = 1L;
        return ApiResponse.success(exportService.retry(id, userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource r = exportService.download(id);
        String filename = "export-" + id + ".md";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(r);
    }
}

