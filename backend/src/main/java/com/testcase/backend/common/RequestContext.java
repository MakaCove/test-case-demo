package com.testcase.backend.common;

public final class RequestContext {
    private static final ThreadLocal<String> requestIdHolder = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        requestIdHolder.set(requestId);
    }

    public static String getRequestId() {
        String v = requestIdHolder.get();
        return v == null ? "local-dev" : v;
    }

    public static void clear() {
        requestIdHolder.remove();
    }
}

