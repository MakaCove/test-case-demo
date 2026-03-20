package com.testcase.backend.service;

import com.testcase.backend.entity.ModelConfigEntity;

import java.util.List;

public interface ModelClient {
    ModelCallResult chatCompletion(ModelConfigEntity cfg, ModelChatInput input) throws Exception;

    /**
     * @param promptTemplate  system 提示（Prompt 模板全文）
     * @param requirementText user 侧纯文本上下文（可为空；仅图片时由客户端补默认引导语）
     * @param imageDataUrls     data URL，如 {@code data:image/png;base64,...}
     */
    record ModelChatInput(String promptTemplate, String requirementText, List<String> imageDataUrls) {
        public static ModelChatInput textOnly(String promptTemplate, String requirementText) {
            return new ModelChatInput(promptTemplate, requirementText, List.of());
        }
    }

    record ModelCallResult(
            String model,
            String content,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long costMs
    ) {
    }
}
