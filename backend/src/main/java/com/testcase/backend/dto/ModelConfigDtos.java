package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 大模型连接配置：创建/更新体及连通性测试可选 prompt。
 * {@code apiKeyEncrypted} 为前端或网关加密后的密文，服务端按约定存储。
 */
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

    /** POST test-connection 可选自定义探测文案 */
    public record TestConnectionRequest(
            String prompt
    ) {
    }
}
