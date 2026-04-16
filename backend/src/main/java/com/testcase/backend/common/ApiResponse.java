package com.testcase.backend.common;

/**
 * 统一 HTTP 接口响应体（JSON）。
 * <p>
 * {@code code == 0} 表示业务成功；非 0 为业务/校验错误码，与 {@link GlobalExceptionHandler} 中约定一致。
 * {@link #requestId} 来自 {@link RequestContext}，便于日志与前端排错串联同一次请求。
 *
 * @param <T> 成功时 {@link #data} 的载荷类型；失败时通常为 {@code null}
 */
public record ApiResponse<T>(
        /** 业务码：0 成功，否则为错误码 */
        int code,
        /** 提示文案：成功多为 {@code success}，失败为可读错误信息 */
        String message,
        /** 成功时的数据体；失败时为 {@code null} */
        T data,
        /** 请求追踪 ID（过滤器注入，缺失时占位为 local-dev） */
        String requestId
) {
    /**
     * 构造成功响应，自动附带当前线程的 requestId。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, RequestContext.getRequestId());
    }

    /**
     * 构造失败响应：{@code data} 为 {@code null}，附带当前线程的 requestId。
     *
     * @param code 业务错误码（如 4001、4010、5000）
     * @param message 面向调用方的错误说明
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, RequestContext.getRequestId());
    }
}
