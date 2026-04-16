package com.testcase.backend.common;

/**
 * 认证失败：未携带凭证、token 过期或无权访问资源时抛出，
 * 由 {@link GlobalExceptionHandler} 转为 HTTP 401 与业务码 4010。
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
