package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ModelConfigDtos {
    public record CreateRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "provider is required") String provider,
            @NotBlank(message = "baseUrl is required") String baseUrl,
            @NotBlank(message = "modelKey is required") String modelKey,
            @NotBlank(message = "apiKeyEncrypted is required") String apiKeyEncrypted,
            BigDecimal temperature,
            Integer maxTokens
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "provider is required") String provider,
            @NotBlank(message = "baseUrl is required") String baseUrl,
            @NotBlank(message = "modelKey is required") String modelKey,
            @NotNull(message = "apiKeyEncrypted is required") String apiKeyEncrypted,
            BigDecimal temperature,
            Integer maxTokens
    ) {
    }

    public record TestConnectionRequest(
            String prompt
    ) {
    }
}

