package com.testcase.backend.common;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String requestId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, RequestContext.getRequestId());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, RequestContext.getRequestId());
    }
}
