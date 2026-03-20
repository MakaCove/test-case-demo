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
}
