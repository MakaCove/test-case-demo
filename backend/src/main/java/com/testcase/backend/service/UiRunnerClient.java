package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Python ui-runner（FastAPI）HTTP 客户端：{@code /run}、{@code /runs/{id}}、取消等；强制 HTTP/1.1 以规避部分环境下 h2 兼容问题。
 */
@Component
public class UiRunnerClient {
    private static final Logger log = LoggerFactory.getLogger(UiRunnerClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.ui-runner.base-url:http://127.0.0.1:18081}")
    private String baseUrl;
    @Value("${app.ui-runner.token:}")
    private String token;
    @Value("${app.ui-runner.connect-timeout-seconds:5}")
    private int connectTimeoutSeconds;
    @Value("${app.ui-runner.read-timeout-seconds:30}")
    private int readTimeoutSeconds;

    public UiRunnerClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // FastAPI/Uvicorn 在部分环境下对 h2c upgrade 兼容性较弱，强制 HTTP/1.1 避免 body 丢失/422。
        this.httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .build();
    }

    public RunResponse run(
            String runId,
            String taskText,
            String baseUrl,
            boolean headless,
            String model,
            Integer timeoutSeconds,
            ArrayNode plannedSteps
    ) {
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("runId", runId);
            payload.put("taskText", taskText == null ? "" : taskText);
            if (StringUtils.hasText(baseUrl)) payload.put("baseUrl", baseUrl.trim());
            payload.put("headless", headless);
            if (StringUtils.hasText(model)) payload.put("model", model.trim());
            if (timeoutSeconds != null && timeoutSeconds > 0) payload.put("timeoutSeconds", timeoutSeconds);
            if (plannedSteps != null && plannedSteps.size() > 0) {
                payload.set("plannedSteps", plannedSteps);
            }
            JsonNode node = postJson("/run", payload.toString());
            return new RunResponse(
                    node.path("accepted").asBoolean(false),
                    textOrNull(node, "runnerRunId"),
                    textOrNull(node, "message")
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("runner run failed: " + exceptionSummary(e));
        }
    }

    public void ensureHealthy() {
        try {
            JsonNode node = getJson("/health");
            boolean ok = node.path("ok").asBoolean(false);
            if (!ok) {
                throw new IllegalArgumentException("runner health check failed: /health returned ok=false");
            }
        } catch (Exception e) {
            String endpoint = join("/health");
            throw new IllegalArgumentException("runner not available at " + endpoint + ", " + exceptionSummary(e));
        }
    }

    public StatusResponse status(String runId) {
        try {
            JsonNode node = getJson("/runs/" + runId);
            List<StepResult> steps = new ArrayList<>();
            JsonNode stepArr = node.path("steps");
            if (stepArr.isArray()) {
                for (JsonNode s : stepArr) {
                    steps.add(new StepResult(
                            s.path("stepNo").asInt(0),
                            textOrNull(s, "title"),
                            textOrNull(s, "actionType"),
                            textOrNull(s, "status"),
                            textOrNull(s, "errorMessage"),
                            textOrNull(s, "screenshotPath"),
                            s.path("durationMs").isNumber() ? s.path("durationMs").asLong() : null,
                            textOrNull(s, "targetJson"),
                            textOrNull(s, "inputValue"),
                            textOrNull(s, "expectJson"),
                            textOrNull(s, "rawLog")
                    ));
                }
            }
            return new StatusResponse(
                    textOrNull(node, "status"),
                    textOrNull(node, "summary"),
                    textOrNull(node, "errorMessage"),
                    textOrNull(node, "artifactsJson"),
                    steps
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("runner status failed: " + exceptionSummary(e));
        }
    }

    public void cancel(String runId) {
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("requestId", UUID.randomUUID().toString());
            postJson("/runs/" + runId + "/cancel", payload.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("runner cancel failed: " + exceptionSummary(e));
        }
    }

    private JsonNode getJson(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(join(path)))
                .timeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds)))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .GET();
        addAuth(builder);
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IllegalArgumentException("http " + resp.statusCode() + ": " + resp.body());
        }
        return objectMapper.readTree(resp.body());
    }

    private JsonNode postJson(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(join(path)))
                .timeout(Duration.ofSeconds(Math.max(1, Math.max(connectTimeoutSeconds, readTimeoutSeconds))))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        addAuth(builder);
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IllegalArgumentException("http " + resp.statusCode() + ": " + resp.body());
        }
        return objectMapper.readTree(resp.body());
    }

    private String join(String path) {
        String b = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "http://127.0.0.1:18081";
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return b + path;
    }

    private void addAuth(HttpRequest.Builder builder) {
        if (StringUtils.hasText(token)) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText(null);
    }

    private String exceptionSummary(Exception e) {
        if (e == null) {
            return "unknown";
        }
        String msg = e.getMessage();
        if (StringUtils.hasText(msg)) {
            return msg;
        }
        Throwable cause = e.getCause();
        if (cause != null && StringUtils.hasText(cause.getMessage())) {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    public record RunResponse(boolean accepted, String runnerRunId, String message) {
    }

    public record StepResult(
            Integer stepNo,
            String title,
            String actionType,
            String status,
            String errorMessage,
            String screenshotPath,
            Long durationMs,
            String targetJson,
            String inputValue,
            String expectJson,
            String rawLog
    ) {
    }

    public record StatusResponse(
            String status,
            String summary,
            String errorMessage,
            String artifactsJson,
            List<StepResult> steps
    ) {
    }
}
