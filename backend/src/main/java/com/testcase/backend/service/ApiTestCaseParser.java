package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 LLM 返回中的接口用例 JSON：支持代码块围栏、根数组或 {@code cases}/{@code apiCases}/{@code testCases}；
 * {@code requestJson}/{@code expectedJson}/{@code assertionsJson} 统一序列化为字符串供入库。
 */
@Component
public class ApiTestCaseParser {
    private static final Logger log = LoggerFactory.getLogger(ApiTestCaseParser.class);
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public ApiTestCaseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @param raw 模型原始输出文本 */
    public List<ApiCaseDraft> parse(String raw) {
        List<ApiCaseDraft> out = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        String json = extractJsonPayload(raw.trim());
        if (!StringUtils.hasText(json)) {
            log.warn("no JSON payload in API model output");
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = resolveArray(root);
            if (array == null || !array.isArray()) {
                log.warn("API JSON root has no cases array");
                return out;
            }
            for (JsonNode el : array) {
                if (el == null || !el.isObject()) {
                    continue;
                }
                ApiCaseDraft d = fromObject(el);
                if (d != null) {
                    out.add(d);
                }
            }
        } catch (Exception e) {
            log.warn("failed to parse API test case JSON: {}", e.getMessage());
        }
        return out;
    }

    private static String extractJsonPayload(String trimmed) {
        Matcher m = FENCE.matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        int b1 = trimmed.indexOf('[');
        int b2 = trimmed.indexOf('{');
        int start;
        if (b1 >= 0 && (b2 < 0 || b1 < b2)) {
            start = b1;
        } else if (b2 >= 0) {
            start = b2;
        } else {
            return null;
        }
        return trimmed.substring(start);
    }

    private JsonNode resolveArray(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        if (!root.isObject()) {
            return null;
        }
        String[] keys = {"cases", "apiCases", "testCases"};
        for (String k : keys) {
            if (root.has(k) && root.get(k).isArray()) {
                return root.get(k);
            }
        }
        return null;
    }

    private ApiCaseDraft fromObject(JsonNode o) {
        String moduleName = firstText(o, "moduleName", "module_name", "module");
        String featureName = firstText(o, "featureName", "feature_name", "feature");
        String title = firstText(o, "title", "name");
        String priority = firstText(o, "priority", "level");
        String remark = firstText(o, "remark", "note");

        String requestJson = serializeJsonField(o, "requestJson", "request_json", "request");
        String expectedJson = serializeJsonField(o, "expectedJson", "expected_json", "expected");
        String assertionsJson = serializeJsonField(o, "assertionsJson", "assertions_json", "assertions");

        if (!StringUtils.hasText(moduleName)) {
            moduleName = "API";
        }
        if (!StringUtils.hasText(featureName)) {
            featureName = "接口";
        }
        if (!StringUtils.hasText(title)) {
            title = "未命名接口用例";
        }
        if (!StringUtils.hasText(requestJson)) {
            requestJson = "{}";
        }
        if (!StringUtils.hasText(expectedJson)) {
            expectedJson = "{}";
        }
        if (!StringUtils.hasText(assertionsJson)) {
            assertionsJson = "[]";
        }
        priority = normalizePriority(priority);

        return new ApiCaseDraft(
                truncate(moduleName, 128),
                truncate(featureName, 128),
                truncate(title, 255),
                requestJson,
                expectedJson,
                assertionsJson,
                priority,
                emptyToNull(remark)
        );
    }

    private String serializeJsonField(JsonNode o, String... keys) {
        for (String k : keys) {
            if (!o.has(k) || o.get(k).isNull()) {
                continue;
            }
            JsonNode n = o.get(k);
            try {
                if (n.isTextual()) {
                    String t = n.asText();
                    if (StringUtils.hasText(t)) {
                        objectMapper.readTree(t);
                        return t.trim();
                    }
                } else {
                    return objectMapper.writeValueAsString(n);
                }
            } catch (Exception e) {
                log.debug("field {} not valid JSON, using as string", k);
                if (n.isTextual()) {
                    return n.asText();
                }
                return n.toString();
            }
        }
        return null;
    }

    private static String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String normalizePriority(String p) {
        if (!StringUtils.hasText(p)) {
            return "P2";
        }
        String u = p.trim().toUpperCase();
        if (u.matches("P[0-3]")) {
            return u;
        }
        return "P2";
    }

    private static String firstText(JsonNode o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isNull()) {
                JsonNode n = o.get(k);
                if (n.isTextual()) {
                    String t = n.asText();
                    if (StringUtils.hasText(t)) {
                        return t.trim();
                    }
                } else if (n.isNumber()) {
                    return n.asText();
                }
            }
        }
        return null;
    }

    /** 单条接口用例草稿，供 {@link ApiTestCaseService} 落库 */
    public record ApiCaseDraft(
            String moduleName,
            String featureName,
            String title,
            String requestJson,
            String expectedJson,
            String assertionsJson,
            String priority,
            String remark
    ) {}
}
