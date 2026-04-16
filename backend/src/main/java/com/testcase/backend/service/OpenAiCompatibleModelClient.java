package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;

/**
 * OpenAI 兼容 Chat Completions HTTP 客户端：支持纯文本与多模态（vision 失败时可降级为纯文本重试）。
 */
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
    public ModelCallResult chatCompletion(ModelConfigEntity cfg, ModelChatInput input) throws Exception {
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
        if (input == null || !StringUtils.hasText(input.promptTemplate())) {
            throw new IllegalArgumentException("Prompt template content is empty; configure it in Prompt template management (Prompt 模板)");
        }

        List<String> images = input.imageDataUrls() == null ? List.of() : input.imageDataUrls();
        boolean hasImages = !images.isEmpty();
        boolean hasUserText = StringUtils.hasText(input.requirementText());

        if (hasImages) {
            try {
                return postChatCompletion(cfg, modelKey, baseUrl, input.promptTemplate(), input.requirementText(), images);
            } catch (IllegalStateException e) {
                if (hasUserText && looksLikeVisionUnsupported(e.getMessage())) {
                    log.warn("vision request failed, retry text-only: {}", e.getMessage());
                    return postChatCompletion(cfg, modelKey, baseUrl, input.promptTemplate(), input.requirementText(), List.of());
                }
                throw e;
            }
        }
        return postChatCompletion(cfg, modelKey, baseUrl, input.promptTemplate(), input.requirementText(), List.of());
    }

    private static boolean looksLikeVisionUnsupported(String msg) {
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("image")
                || m.contains("vision")
                || m.contains("multimodal")
                || m.contains("invalid content")
                || m.contains("unsupported")
                || m.contains("not support");
    }

    private ModelCallResult postChatCompletion(
            ModelConfigEntity cfg,
            String modelKey,
            String baseUrl,
            String promptTemplate,
            String requirementText,
            List<String> imageDataUrls
    ) throws Exception {
        URI uri = URI.create(join(baseUrl, baseUrl.contains("/v1") ? "/chat/completions" : "/v1/chat/completions"));
        int cfgMax = cfg.getMaxTokens() == null ? 2048 : cfg.getMaxTokens();
        int maxTokens = Math.max(256, Math.min(8192, cfgMax));
        double temperature = cfg.getTemperature() == null ? 0.3 : cfg.getTemperature().doubleValue();

        String system = promptTemplate.trim();
        String userText = StringUtils.hasText(requirementText)
                ? requirementText.trim()
                : "(无需求文本：请在需求资产中维护描述/文档/原型，或仍按模板约定生成)";
        if (imageDataUrls != null && !imageDataUrls.isEmpty() && !StringUtils.hasText(requirementText)) {
            userText = "请根据附图内容生成测试用例（当前仅有图片，无文字需求描述）。";
        }

        ObjectNode userMessage = objectMapper.createObjectNode().put("role", "user");
        if (imageDataUrls == null || imageDataUrls.isEmpty()) {
            userMessage.put("content", userText);
        } else {
            ArrayNode parts = objectMapper.createArrayNode();
            parts.add(objectMapper.createObjectNode()
                    .put("type", "text")
                    .put("text", userText));
            for (String dataUrl : imageDataUrls) {
                if (!StringUtils.hasText(dataUrl)) {
                    continue;
                }
                ObjectNode imgPart = objectMapper.createObjectNode().put("type", "image_url");
                imgPart.set("image_url", objectMapper.createObjectNode().put("url", dataUrl.trim()));
                parts.add(imgPart);
            }
            userMessage.set("content", parts);
        }

        String body = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("model", modelKey)
                .put("temperature", temperature)
                .put("max_tokens", maxTokens)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("role", "system").put("content", system))
                        .add(userMessage)));

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
