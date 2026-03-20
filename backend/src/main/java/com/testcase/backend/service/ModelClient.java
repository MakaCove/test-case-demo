package com.testcase.backend.service;

import com.testcase.backend.entity.ModelConfigEntity;

public interface ModelClient {
    ModelCallResult chatCompletion(ModelConfigEntity cfg, String promptTemplate, String requirementText) throws Exception;

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

