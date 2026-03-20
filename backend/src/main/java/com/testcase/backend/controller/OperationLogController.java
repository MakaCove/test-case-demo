package com.testcase.backend.controller;

import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.entity.OperationLogEntity;
import com.testcase.backend.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operation-logs")
public class OperationLogController {
    private static final Logger log = LoggerFactory.getLogger(OperationLogController.class);
    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PagedResult<OperationLogEntity>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String action
    ) {
        log.info("list operation logs, pageNo={}, pageSize={}, objectType={}, action={}", pageNo, pageSize, objectType, action);
        var result = operationLogService.search(objectType, action, pageNo, pageSize);
        return ApiResponse.success(result);
    }
}
