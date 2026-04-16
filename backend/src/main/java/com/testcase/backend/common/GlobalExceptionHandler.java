package com.testcase.backend.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将常见异常转为 {@link ApiResponse}，并设置合适的 HTTP 状态码。
 * <p>
 * 业务错误码与 HTTP 状态分开：前端可同时依据 status 与 body.code 判断。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务参数/前置校验不通过 → HTTP 400，业务码 4001 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("business validation failed: {}", e.getMessage());
        return ApiResponse.fail(4001, e.getMessage());
    }

    /** {@code @Valid} 实体字段校验失败 → HTTP 400，业务码 4002 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        var firstError = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("validation failed");
        log.warn("request validation failed: {}", firstError);
        return ApiResponse.fail(4002, firstError);
    }

    /** Bean 校验（如路径/单参数 {@code @Min}）→ HTTP 400，业务码 4003 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException e) {
        log.warn("constraint validation failed: {}", e.getMessage());
        return ApiResponse.fail(4003, e.getMessage());
    }

    /** 未登录或 token 无效 → HTTP 401，业务码 4010 */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException e) {
        log.warn("unauthorized request: {}", e.getMessage());
        return ApiResponse.fail(4010, e.getMessage());
    }

    /** 未捕获异常 → HTTP 500，业务码 5000（不向外暴露内部细节） */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("unexpected server error", e);
        return ApiResponse.fail(5000, "internal server error");
    }
}
