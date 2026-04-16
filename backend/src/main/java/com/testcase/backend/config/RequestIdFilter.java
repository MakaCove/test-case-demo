package com.testcase.backend.config;

import com.testcase.backend.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 每个 HTTP 请求生成或透传 {@link #HEADER_REQUEST_ID}：
 * <ul>
 *   <li>写入 {@link RequestContext}，供 {@link com.testcase.backend.common.ApiResponse} 等回传</li>
 *   <li>放入 SLF4J {@link MDC}，日志可按 requestId 串联</li>
 *   <li>响应头回写同一 ID，便于前端与网关对账</li>
 * </ul>
 * 在 {@code finally} 中清理 MDC 与 ThreadLocal，避免线程池复用串号。
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {
    /** 与前端/网关约定的请求 ID 头名称 */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        RequestContext.setRequestId(requestId);
        MDC.put("requestId", requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            RequestContext.clear();
        }
    }
}
