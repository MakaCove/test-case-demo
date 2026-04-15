package com.testcase.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.entity.UiNlCaseEntity;
import com.testcase.backend.entity.UiNlReportEntity;
import com.testcase.backend.entity.UiNlTaskEntity;
import com.testcase.backend.entity.UiNlTaskExecStepEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class UiNlHtmlReportService {
    private static final Logger log = LoggerFactory.getLogger(UiNlHtmlReportService.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String baseStoragePath;
    private final ObjectMapper objectMapper;

    public UiNlHtmlReportService(
            @Value("${app.storage.base-path:uploads}") String baseStoragePath,
            ObjectMapper objectMapper
    ) {
        this.baseStoragePath = StringUtils.hasText(baseStoragePath) ? baseStoragePath.trim() : "uploads";
        this.objectMapper = objectMapper;
    }

    public String generateReport(
            UiNlTaskEntity task,
            UiNlCaseEntity uiCase,
            UiNlReportEntity report,
            List<UiNlTaskExecStepEntity> execSteps
    ) {
        try {
            Path dir = Paths.get(baseStoragePath, "ui-nl-reports").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileName = safeName(report.getReportNo()) + ".html";
            Path output = dir.resolve(fileName);
            String html = renderHtml(task, uiCase, report, execSteps, resolveRunnerRunDir(report.getArtifactsJson()));
            Files.writeString(output, html, StandardCharsets.UTF_8);
            return output.toString().replace('\\', '/');
        } catch (Exception e) {
            log.warn("generate ui nl html report failed, taskId={}, err={}", task.getId(), e.getMessage());
            return null;
        }
    }

    private String renderHtml(
            UiNlTaskEntity task,
            UiNlCaseEntity uiCase,
            UiNlReportEntity report,
            List<UiNlTaskExecStepEntity> execSteps,
            Path runnerRunDir
    ) {
        long durationMs = 0L;
        if (task.getExecStartedAt() != null && report.getFinishedAt() != null) {
            durationMs = Math.max(0L, Duration.between(task.getExecStartedAt(), report.getFinishedAt()).toMillis());
        }
        String verdict = "SUCCESS".equalsIgnoreCase(report.getStatus()) ? "通过" : "失败";
        StringBuilder rows = new StringBuilder();
        int idx = 1;
        for (UiNlTaskExecStepEntity s : execSteps) {
            rows.append("<tr>")
                    .append("<td>").append(idx++).append("</td>")
                    .append("<td>").append(esc(s.getStepTitle())).append("</td>")
                    .append("<td>").append(esc(s.getActionType())).append("</td>")
                    .append("<td>").append(esc(s.getStatus())).append("</td>")
                    .append("<td>").append(esc(s.getInputValue())).append("</td>")
                    .append("<td>").append(esc(s.getExpectJson())).append("</td>")
                    .append("<td>").append(s.getDurationMs() == null ? "-" : s.getDurationMs()).append("</td>")
                    .append("<td>").append(renderScreenshot(s.getScreenshotPath(), runnerRunDir)).append("</td>")
                    .append("<td>").append(esc(s.getErrorMessage())).append("</td>")
                    .append("</tr>");
        }

        return "<!doctype html>\n"
                + "<html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                + "<title>UI-NL 测试报告 " + esc(report.getReportNo()) + "</title>"
                + "<style>"
                + "body{font-family:Segoe UI,Arial,sans-serif;background:#f6f8fb;color:#1f2937;margin:0;padding:20px;}"
                + ".card{background:#fff;border-radius:10px;padding:16px;margin-bottom:14px;box-shadow:0 1px 3px rgba(0,0,0,.06);}"
                + ".title{font-size:22px;font-weight:700;margin-bottom:8px;}.meta{display:grid;grid-template-columns:repeat(4,minmax(160px,1fr));gap:8px;}"
                + ".k{color:#6b7280;font-size:12px}.v{font-size:14px;font-weight:600}"
                + "table{width:100%;border-collapse:collapse;}th,td{border:1px solid #e5e7eb;padding:8px;vertical-align:top;font-size:12px;}"
                + "th{background:#f3f4f6;text-align:left}.ok{color:#166534}.bad{color:#991b1b}"
                + "img{max-width:260px;max-height:160px;border:1px solid #ddd;border-radius:4px}"
                + "pre{white-space:pre-wrap;word-break:break-word;margin:0;}"
                + "</style></head><body>"
                + "<div class=\"card\"><div class=\"title\">UI-NL 测试报告</div>"
                + "<div class=\"meta\">"
                + kv("报告编号", report.getReportNo())
                + kv("任务号", task.getTaskNo())
                + kv("结论", "<span class=\"" + ("SUCCESS".equalsIgnoreCase(report.getStatus()) ? "ok" : "bad") + "\">" + verdict + "</span>")
                + kv("状态", report.getStatus())
                + kv("开始时间", fmt(task.getExecStartedAt()))
                + kv("结束时间", fmt(report.getFinishedAt()))
                + kv("耗时", durationMs <= 0 ? "-" : durationMs + " ms")
                + kv("用例标题", uiCase == null ? "-" : uiCase.getTitle())
                + "</div></div>"
                + "<div class=\"card\"><h3>执行概览</h3><div class=\"meta\">"
                + kv("总步骤", String.valueOf(report.getTotalSteps()))
                + kv("成功", String.valueOf(report.getPassedSteps()))
                + kv("失败", String.valueOf(report.getFailedSteps()))
                + kv("摘要", esc(report.getSummary()))
                + "</div></div>"
                + "<div class=\"card\"><h3>执行步骤详情</h3><table><thead><tr>"
                + "<th>#</th><th>标题</th><th>动作</th><th>状态</th><th>操作内容</th><th>预期</th><th>耗时(ms)</th><th>截图</th><th>错误</th>"
                + "</tr></thead><tbody>"
                + rows
                + "</tbody></table></div>"
                + "<div class=\"card\"><h3>原始需求</h3><pre>"
                + esc(uiCase == null ? "" : uiCase.getNlText())
                + "</pre></div>"
                + "<div class=\"card\"><small>生成时间：" + fmt(LocalDateTime.now()) + "</small></div>"
                + "</body></html>";
    }

    private String kv(String k, String v) {
        return "<div><div class=\"k\">" + esc(k) + "</div><div class=\"v\">" + v + "</div></div>";
    }

    /**
     * ui-runner 写入的截图多为相对 run 目录的路径（如 {@code shots/001_xxx.png}），须用 artifacts_json.runDir 解析。
     */
    private Path resolveRunnerRunDir(String artifactsJson) {
        if (!StringUtils.hasText(artifactsJson)) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(artifactsJson.trim());
            JsonNode rd = n.get("runDir");
            if (rd == null || rd.isNull()) {
                return null;
            }
            String s = rd.asText(null);
            if (!StringUtils.hasText(s)) {
                return null;
            }
            return Paths.get(s.trim()).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.debug("parse artifactsJson.runDir failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将数据库中的截图路径解析为本地文件（相对路径需配合 artifacts_json.runDir）。
     *
     * @return存在则返回绝对路径，否则 null
     */
    public Path resolveExecScreenshotPath(String screenshotPath, String artifactsJson) {
        return resolveScreenshotPath(screenshotPath, resolveRunnerRunDir(artifactsJson));
    }

    private Path resolveScreenshotPath(String screenshotPath, Path runnerRunDir) {
        if (!StringUtils.hasText(screenshotPath)) {
            return null;
        }
        try {
            String rel = screenshotPath.trim().replace("\\", "/");
            Path p = Paths.get(rel);
            if (!p.isAbsolute()) {
                if (runnerRunDir != null) {
                    p = runnerRunDir.resolve(rel).normalize();
                } else {
                    p = Paths.get(baseStoragePath).toAbsolutePath().normalize().resolve(rel).normalize();
                }
            } else {
                p = p.normalize();
            }
            return Files.exists(p) ? p : null;
        } catch (Exception e) {
            log.debug("resolve screenshot path failed: {}", e.getMessage());
            return null;
        }
    }

    private String renderScreenshot(String screenshotPath, Path runnerRunDir) {
        if (!StringUtils.hasText(screenshotPath)) {
            return "-";
        }
        try {
            Path p = resolveScreenshotPath(screenshotPath, runnerRunDir);
            if (p == null) {
                return esc(screenshotPath);
            }
            String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
            String mime = lower.endsWith(".png") ? "image/png"
                    : (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ? "image/jpeg"
                    : lower.endsWith(".webp") ? "image/webp"
                    : "image/png";
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(p));
            return "<img src=\"data:" + mime + ";base64," + base64 + "\" alt=\"screenshot\"/>";
        } catch (Exception e) {
            return esc(screenshotPath);
        }
    }

    private String fmt(LocalDateTime dt) {
        return dt == null ? "-" : DT.format(dt);
    }

    private String safeName(String raw) {
        String t = StringUtils.hasText(raw) ? raw.trim() : "ui-nl-report";
        return t.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String esc(String raw) {
        if (raw == null) return "-";
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
