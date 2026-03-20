package com.testcase.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class PromptTemplateDtos {
    public record CreateRequest(
            @NotBlank(message = "name is required") String name,
            String scopeType,
            Long scopeId,
            @NotBlank(message = "content is required") String content,
            String remark
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "content is required") String content,
            String remark
    ) {
    }
}

