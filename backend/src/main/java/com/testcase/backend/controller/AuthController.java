package com.testcase.backend.controller;

import com.testcase.backend.common.ApiResponse;
import com.testcase.backend.dto.AuthDtos;
import com.testcase.backend.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与账号：登录、注册、登出、当前用户、改密。
 * {@code /login}、{@code /register}、{@code /logout} 在 {@link com.testcase.backend.config.WebCorsConfig} 中排除鉴权；
 * {@code /me} 与 {@code /change-password} 需携带 Bearer Token。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        log.info("login request, username={}", request.username());
        var response = authService.login(request.username(), request.password());
        return ApiResponse.success(response);
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.LoginResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        log.info("register request, username={}", request.username());
        var response = authService.register(request.username(), request.displayName(), request.password());
        return ApiResponse.success(response);
    }

    /** 吊销会话：Authorization 可选，无则仅返回成功 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        authService.logout(token);
        return ApiResponse.success(null);
    }

    /** 当前登录用户信息，依赖 Bearer Token */
    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserInfo> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        return ApiResponse.success(authService.currentUser(token));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request
    ) {
        String token = extractBearerToken(authorization);
        authService.changePassword(token, request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }

    /** 同 {@link com.testcase.backend.config.AuthInterceptor}：支持 {@code Bearer } 前缀或整段 token */
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
