package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.entity.ModelConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class OpenAiCompatibleModelClient implements ModelClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ObjectMapper objectMapper;
    private final int requestTimeoutSeconds;

    public OpenAiCompatibleModelClient(
            ObjectMapper objectMapper,
            @Value("${app.model.request-timeout-seconds:300}") int requestTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.requestTimeoutSeconds = Math.max(30, requestTimeoutSeconds);
    }

    @Override
    public ModelCallResult chatCompletion(ModelConfigEntity cfg, String promptTemplate, String requirementText) throws Exception {
        String baseUrl = StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl().trim() : "";
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (!StringUtils.hasText(cfg.getApiKeyEncrypted())) {
            throw new IllegalArgumentException("apiKey is required");
        }
        String modelKey = StringUtils.hasText(cfg.getModelKey()) ? cfg.getModelKey().trim() : "";
        if (!StringUtils.hasText(modelKey)) {
            throw new IllegalArgumentException("modelKey is required");
        }

        URI uri = URI.create(join(baseUrl, baseUrl.contains("/v1") ? "/chat/completions" : "/v1/chat/completions"));
        int cfgMax = cfg.getMaxTokens() == null ? 2048 : cfg.getMaxTokens();
        int maxTokens = Math.max(256, Math.min(8192, cfgMax));
        double temperature = cfg.getTemperature() == null ? 0.3 : cfg.getTemperature().doubleValue();

        // 全部格式与角色说明由「Prompt 模板管理」中的 system 内容提供，此处不写死业务提示词
        if (!StringUtils.hasText(promptTemplate)) {
            throw new IllegalArgumentException("Prompt template content is empty; configure it in Prompt template management (Prompt 模板)");
        }
        String system = promptTemplate.trim();
        // user 仅承载需求上下文；模板中应说明「用户消息为需求/资产摘要」及输出 JSON 结构
        String user = StringUtils.hasText(requirementText)
                ? requirementText.trim()
                : "(无需求文本：请在需求资产中维护描述/文档/原型，或仍按模板约定生成)";

        String body = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("model", modelKey)
                .put("temperature", temperature)
                .put("max_tokens", maxTokens)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("role", "system").put("content", system))
                        .add(objectMapper.createObjectNode().put("role", "user").put("content", user))));

        long start = System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("Authorization", "Bearer " + cfg.getApiKeyEncrypted())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        long costMs = System.currentTimeMillis() - start;
        if (resp.statusCode() / 100 != 2) {
            String trimmed = resp.body() == null ? "" : (resp.body().length() > 400 ? resp.body().substring(0, 400) + "..." : resp.body());
            throw new IllegalStateException("http " + resp.statusCode() + " from " + uri + ", body=" + trimmed);
        }

        JsonNode root = objectMapper.readTree(resp.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        String model = root.path("model").asText(modelKey);
        int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
        int totalTokens = root.path("usage").path("total_tokens").asInt(0);

        log.info("model call ok, modelConfigId={}, model={}, costMs={}, tokens={}", cfg.getId(), model, costMs, totalTokens);
        return new ModelCallResult(model, content, promptTokens, completionTokens, totalTokens, costMs);
    }

    private String join(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}

