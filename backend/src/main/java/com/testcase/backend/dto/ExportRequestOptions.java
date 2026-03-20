package com.testcase.backend.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 导出请求扩展参数，存于 {@code export_records.request_json}。
 * 示例：<code>{"targets":["FUNCTIONAL","API"],"scope":"all"}</code>
 */
public class ExportRequestOptions {
    public static final String TARGET_FUNCTIONAL = "FUNCTIONAL";
    public static final String TARGET_API = "API";

    private List<String> targets;
    /** 目前仅支持 all（全量） */
    private String scope;

    public List<String> getTargets() {
        return targets;
    }

    public void setTargets(List<String> targets) {
        this.targets = targets;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public static ExportRequestOptions defaults() {
        ExportRequestOptions o = new ExportRequestOptions();
        o.targets = new ArrayList<>(List.of(TARGET_FUNCTIONAL));
        o.scope = "all";
        return o;
    }

    /**
     * 老数据 request_json 为空时：仅导出功能用例（与历史行为一致）。
     */
    public static ExportRequestOptions parseOrDefault(String json, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(json)) {
            return defaults();
        }
        try {
            ExportRequestOptions o = objectMapper.readValue(json.trim(), ExportRequestOptions.class);
            o.normalizeAndValidate();
            return o;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid requestJson: " + e.getOriginalMessage());
        }
    }

    public void normalizeAndValidate() {
        if (scope == null || scope.isBlank()) {
            scope = "all";
        }
        if (targets == null) {
            targets = new ArrayList<>();
        }
        Set<String> upper = new LinkedHashSet<>();
        for (String t : targets) {
            if (t == null) {
                continue;
            }
            String u = t.trim().toUpperCase();
            if (TARGET_FUNCTIONAL.equals(u) || TARGET_API.equals(u)) {
                upper.add(u);
            }
        }
        targets = new ArrayList<>(upper);
        if (targets.isEmpty()) {
            targets.add(TARGET_FUNCTIONAL);
        }
    }

    public boolean includeFunctional() {
        return targets != null && targets.contains(TARGET_FUNCTIONAL);
    }

    public boolean includeApi() {
        return targets != null && targets.contains(TARGET_API);
    }

    public String toDisplayLabel() {
        List<String> parts = new ArrayList<>();
        if (includeFunctional()) {
            parts.add("功能用例");
        }
        if (includeApi()) {
            parts.add("接口用例");
        }
        return parts.isEmpty() ? "功能用例" : String.join("、", parts);
    }

    public static String toJson(ExportRequestOptions o, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize export options");
        }
    }
}
