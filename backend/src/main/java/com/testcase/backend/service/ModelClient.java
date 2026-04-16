package com.testcase.backend.service;

import com.testcase.backend.entity.ModelConfigEntity;

import java.util.List;

/**
 * 大模型统一调用门面：由 {@link OpenAiCompatibleModelClient} 等实现，供 {@link GenerationTaskRunner}、{@link UiNlService} 使用。
 */
public interface ModelClient {
    ModelCallResult chatCompletion(ModelConfigEntity cfg, ModelChatInput input) throws Exception;

    /**
     * @param promptTemplate  system 侧提示（模板全文）
     * @param requirementText user 侧纯文本上下文（可空；仅图片时由调用方补默认说明）
     * @param imageDataUrls data URL 列表，如 {@code data:image/png;base64,...}
     */
    record ModelChatInput(String promptTemplate, String requirementText, List<String> imageDataUrls) {
        public static ModelChatInput textOnly(String promptTemplate, String requirementText) {
            return new ModelChatInput(promptTemplate, requirementText, List.of());
        }
    }

    /** 单次补全结果：文本与 token 用量、耗时 */
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
