package com.testcase.backend.service;

import com.testcase.backend.entity.ModelConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * 模型连通性探测：Ollama 走 {@code GET /api/tags}；否则按 OpenAI 兼容先 {@code GET /v1/models}，失败再极小 {@code chat/completions} 探测。
 */
@Service
public class ModelConnectivityService {
    private static final Logger log = LoggerFactory.getLogger(ModelConnectivityService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** @return 人类可读探测结果摘要；失败抛 {@link IllegalStateException} */
    public String testConnection(ModelConfigEntity cfg, String prompt) {
        String provider = (cfg.getProvider() == null ? "" : cfg.getProvider()).trim().toLowerCase(Locale.ROOT);
        String baseUrl = StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl().trim() : "";
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (!StringUtils.hasText(cfg.getApiKeyEncrypted())) {
            throw new IllegalArgumentException("apiKey is required");
        }

        long start = System.currentTimeMillis();
        try {
            if (provider.contains("ollama")) {
                // Ollama: GET /api/tags
                URI uri = URI.create(join(baseUrl, "/api/tags"));
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                long cost = System.currentTimeMillis() - start;
                if (resp.statusCode() / 100 != 2) {
                    throw new IllegalStateException("http " + resp.statusCode() + " from " + uri);
                }
                return "OK (ollama), http=" + resp.statusCode() + ", costMs=" + cost;
            }

            // Default: OpenAI-compatible.
            // Prefer GET /models, but some providers don't expose it; fallback to POST chat/completions with max_tokens=1.
            String modelsPath = baseUrl.contains("/v1") ? "/models" : "/v1/models";
            URI modelsUri = URI.create(join(baseUrl, modelsPath));
            HttpRequest modelsReq = HttpRequest.newBuilder(modelsUri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + cfg.getApiKeyEncrypted())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> modelsResp = httpClient.send(modelsReq, HttpResponse.BodyHandlers.ofString());
            long cost = System.currentTimeMillis() - start;
            if (modelsResp.statusCode() / 100 == 2) {
                String modelKey = cfg.getModelKey() == null ? "" : cfg.getModelKey().trim();
                String extra = StringUtils.hasText(modelKey) ? (", modelKey=" + modelKey) : "";
                String echo = StringUtils.hasText(prompt) ? (", echo=" + prompt) : "";
                return "OK (openai-compatible), http=" + modelsResp.statusCode() + ", costMs=" + cost + extra + echo;
            }

            // Fallback when /models is not available (e.g. 404)
            String chatPath = baseUrl.contains("/v1") ? "/chat/completions" : "/v1/chat/completions";
            URI chatUri = URI.create(join(baseUrl, chatPath));
            String modelKey = cfg.getModelKey() == null ? "" : cfg.getModelKey().trim();
            if (!StringUtils.hasText(modelKey)) {
                throw new IllegalArgumentException("modelKey is required for chat fallback");
            }
            String echo = StringUtils.hasText(prompt) ? prompt : "ping";
            String json = "{\"model\":\"" + escape(modelKey) + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escape(echo) + "\"}],\"max_tokens\":1,\"temperature\":0}";
            HttpRequest chatReq = HttpRequest.newBuilder(chatUri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + cfg.getApiKeyEncrypted())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> chatResp = httpClient.send(chatReq, HttpResponse.BodyHandlers.ofString());
            long cost2 = System.currentTimeMillis() - start;
            if (chatResp.statusCode() / 100 != 2) {
                String body = chatResp.body() == null ? "" : chatResp.body();
                String trimmed = body.length() > 300 ? body.substring(0, 300) + "..." : body;
                throw new IllegalStateException("http " + chatResp.statusCode() + " from " + chatUri + ", body=" + trimmed);
            }
            return "OK (openai-compatible chat fallback), http=" + chatResp.statusCode() + ", costMs=" + cost2 + ", modelKey=" + modelKey;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("model connectivity failed, provider={}, baseUrl={}, costMs={}, err={}",
                    provider, safeUrl(baseUrl), cost, e.getMessage());
            throw new IllegalStateException("connectivity failed: " + e.getMessage());
        }
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

    private String safeUrl(String baseUrl) {
        // Avoid logging anything sensitive; baseUrl is generally safe but still normalize.
        return baseUrl.replaceAll("\\s+", "");
    }

    private String escape(String raw) {
        if (raw == null) return "";
        return raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

