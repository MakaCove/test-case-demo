package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 认证相关请求/响应：登录、注册、改密及用户信息。
 */
public class AuthDtos {
    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password
    ) {
    }

    /** 当前用户基本信息（{@code /me} 等） */
    public record UserInfo(
            Long id,
            String username,
            String displayName
    ) {
    }

    /** 登录/注册成功后返回会话 token 与用户信息 */
    public record LoginResponse(
            String token,
            UserInfo userInfo
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "username is required") String username,
            String displayName,
            @NotBlank(message = "password is required") String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "oldPassword is required") String oldPassword,
            @NotBlank(message = "newPassword is required") String newPassword
    ) {
    }
}
