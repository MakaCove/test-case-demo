package com.testcase.backend.config;

import com.testcase.backend.common.UnauthorizedException;
import com.testcase.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 除注册/登录等白名单外，对 {@code /api/**} 请求做 Bearer Token 校验。
 * <p>
 * 校验通过后将 {@code loginUserId}、{@code loginUsername} 写入 request attribute，供 Controller 使用；
 * 预检请求 OPTIONS 直接放行（与 CORS 配合）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        String token = extractBearerToken(authorization);
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("missing token");
        }

        var userInfo = authService.currentUser(token);
        request.setAttribute("loginUserId", userInfo.id());
        request.setAttribute("loginUsername", userInfo.username());
        log.debug("auth passed, uri={}, userId={}", request.getRequestURI(), userInfo.id());
        return true;
    }

    /** 解析 {@code Authorization: Bearer &lt;token&gt;}；若无 Bearer 前缀则整段视为 token（兼容部分客户端）。 */
    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
