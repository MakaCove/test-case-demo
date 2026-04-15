package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password
    ) {
    }

    public record UserInfo(
            Long id,
            String username,
            String displayName
    ) {
    }

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
