package com.testcase.backend.common;

/**
 * 请求级上下文：在当前处理线程中保存 requestId，供业务层与 {@link ApiResponse} 回写。
 * <p>
 * 须在请求入口 {@link #setRequestId}、结束时 {@link #clear}（通常在 Filter 中），避免线程池复用导致串号。
 */
public final class RequestContext {
    private static final ThreadLocal<String> requestIdHolder = new ThreadLocal<>();

    private RequestContext() {
    }

    /** 由过滤器在请求开始时写入；勿在异步线程中假设自动传递，需自行传递 ID。 */
    public static void setRequestId(String requestId) {
        requestIdHolder.set(requestId);
    }

    /**
     * 当前线程关联的 requestId；若未设置（如单元测试）则返回 {@code local-dev}。
     */
    public static String getRequestId() {
        String v = requestIdHolder.get();
        return v == null ? "local-dev" : v;
    }

    /** 请求结束时移除，防止 ThreadLocal 泄漏或污染后续复用的线程。 */
    public static void clear() {
        requestIdHolder.remove();
    }
}
