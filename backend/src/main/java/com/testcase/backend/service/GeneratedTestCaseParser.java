package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 LLM 输出的功能用例草稿：支持 Markdown 代码围栏、根数组或 {@code testCases}/{@code cases}/{@code caseList}，
 * 字段兼容 camelCase 与 snake_case。
 */
@Component
public class GeneratedTestCaseParser {
    private static final Logger log = LoggerFactory.getLogger(GeneratedTestCaseParser.class);
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public GeneratedTestCaseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @param raw 模型原始输出 */
    public List<GeneratedCaseDraft> parse(String raw) {
        List<GeneratedCaseDraft> out = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        String json = extractJsonPayload(raw.trim());
        if (!StringUtils.hasText(json)) {
            log.warn("no JSON payload found in model output");
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = resolveArray(root);
            if (array == null || !array.isArray()) {
                log.warn("JSON root is not an array and has no known cases field");
                return out;
            }
            for (JsonNode el : array) {
                if (el == null || !el.isObject()) {
                    continue;
                }
                GeneratedCaseDraft d = fromObject(el);
                if (d != null) {
                    out.add(d);
                }
            }
        } catch (Exception e) {
            log.warn("failed to parse generated test case JSON: {}", e.getMessage());
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
        String[] keys = {"testCases", "cases", "caseList", "items", "data", "list"};
        for (String k : keys) {
            if (root.has(k) && root.get(k).isArray()) {
                return root.get(k);
            }
        }
        return null;
    }

    private GeneratedCaseDraft fromObject(JsonNode o) {
        String moduleName = firstText(o, "moduleName", "module_name", "module");
        String featureName = firstText(o, "featureName", "feature_name", "feature");
        String title = firstText(o, "title", "name", "caseTitle");
        String precondition = firstText(o, "precondition", "pre_condition", "preconditions");
        String steps = stepsToText(o);
        String testData = firstText(o, "testData", "test_data");
        String expected = firstText(o, "expectedResult", "expected_result", "expected");
        String priority = firstText(o, "priority", "level");

        if (!StringUtils.hasText(moduleName)) {
            moduleName = "AI生成";
        }
        if (!StringUtils.hasText(featureName)) {
            featureName = "功能测试";
        }
        if (!StringUtils.hasText(title)) {
            title = "未命名用例";
        }
        if (!StringUtils.hasText(steps)) {
            steps = "1. 按需求执行操作\n2. 观察系统反馈";
        }
        if (!StringUtils.hasText(expected)) {
            expected = "行为与需求描述一致，无错误提示";
        }
        priority = normalizePriority(priority);

        return new GeneratedCaseDraft(
                truncate(moduleName, 128),
                truncate(featureName, 128),
                truncate(title, 255),
                emptyToNull(precondition),
                steps,
                emptyToNull(testData),
                expected,
                priority
        );
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

    private static String stepsToText(JsonNode o) {
        JsonNode direct = null;
        for (String k : new String[]{"steps", "stepList", "step_list", "procedure"}) {
            if (o.has(k)) {
                direct = o.get(k);
                break;
            }
        }
        if (direct == null) {
            return null;
        }
        if (direct.isTextual()) {
            return direct.asText().trim();
        }
        if (direct.isArray()) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (JsonNode step : direct) {
                String line = step.isTextual() ? step.asText().trim() : step.toString();
                if (StringUtils.hasText(line)) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(i).append(". ").append(line);
                    i++;
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return direct.toString();
    }

    /** 单条功能用例草稿，供 {@link TestCaseService} 落库 */
    public record GeneratedCaseDraft(
            String moduleName,
            String featureName,
            String title,
            String precondition,
            String steps,
            String testData,
            String expectedResult,
            String priority
    ) {}
}
