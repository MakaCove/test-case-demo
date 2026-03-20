package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.dto.ExportDtos;
import com.testcase.backend.dto.ExportRequestOptions;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.entity.ApiTestCaseEntity;
import com.testcase.backend.entity.ExportRecordEntity;
import com.testcase.backend.entity.TestCaseEntity;
import com.testcase.backend.mapper.ApiTestCaseMapper;
import com.testcase.backend.mapper.ExportRecordMapper;
import com.testcase.backend.mapper.TestCaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ExportRecordMapper exportRecordMapper;
    private final TestCaseMapper testCaseMapper;
    private final ApiTestCaseMapper apiTestCaseMapper;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;

    @Value("${app.storage.base-path:uploads}")
    private String storageBasePath;

    public ExportService(
            ExportRecordMapper exportRecordMapper,
            TestCaseMapper testCaseMapper,
            ApiTestCaseMapper apiTestCaseMapper,
            ObjectMapper objectMapper,
            OperationLogService operationLogService
    ) {
        this.exportRecordMapper = exportRecordMapper;
        this.testCaseMapper = testCaseMapper;
        this.apiTestCaseMapper = apiTestCaseMapper;
        this.objectMapper = objectMapper;
        this.operationLogService = operationLogService;
    }

    public PagedResult<ExportDtos.ExportItem> search(Long projectId, Long versionId, String status, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<ExportRecordEntity>()
                .eq(ExportRecordEntity::getIsDeleted, 0)
                .orderByDesc(ExportRecordEntity::getId);
        if (projectId != null) wrapper.eq(ExportRecordEntity::getProjectId, projectId);
        if (versionId != null) wrapper.eq(ExportRecordEntity::getVersionId, versionId);
        if (StringUtils.hasText(status)) wrapper.eq(ExportRecordEntity::getStatus, status.trim().toUpperCase());
        Page<ExportRecordEntity> page = exportRecordMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        var items = page.getRecords().stream().map(this::toItem).toList();
        return new PagedResult<>(items, safePageNo, safePageSize, page.getTotal());
    }

    public ExportDtos.ExportItem detail(Long id) {
        ExportRecordEntity e = exportRecordMapper.selectById(id);
        if (e == null || e.getIsDeleted() == 1) {
            throw new IllegalArgumentException("export record not found");
        }
        return toItem(e);
    }

    @Transactional
    public ExportDtos.ExportItem createAndRun(ExportDtos.CreateRequest req, Long operatorId) {
        String format = req.format().trim().toLowerCase();
        if (!"md".equals(format)) {
            throw new IllegalArgumentException("only md is supported in MVP");
        }
        ExportRequestOptions opts = resolveOptionsForCreate(req);
        String normalizedJson = ExportRequestOptions.toJson(opts, objectMapper);

        ExportRecordEntity record = new ExportRecordEntity();
        record.setExportNo(generateExportNo());
        record.setProjectId(req.projectId());
        record.setVersionId(req.versionId());
        record.setFormat(format);
        record.setScope(req.scope().trim());
        record.setStatus("RUNNING");
        record.setRequestJson(normalizedJson);
        record.setCreatedBy(operatorId);
        record.setIsDeleted(0);
        exportRecordMapper.insert(record);
        operationLogService.log("EXPORT", record.getId(), "RUNNING", null, record, null);

        runMarkdownExport(record);
        return detail(record.getId());
    }

    /**
     * 新建导出：未传 requestJson 时默认「功能 + 接口」；老记录解析仍用 {@link ExportRequestOptions#parseOrDefault}。
     */
    private ExportRequestOptions resolveOptionsForCreate(ExportDtos.CreateRequest req) {
        if (!StringUtils.hasText(req.requestJson())) {
            ExportRequestOptions o = new ExportRequestOptions();
            o.setTargets(new ArrayList<>(List.of(ExportRequestOptions.TARGET_FUNCTIONAL, ExportRequestOptions.TARGET_API)));
            o.setScope("all");
            return o;
        }
        return ExportRequestOptions.parseOrDefault(req.requestJson(), objectMapper);
    }

    /**
     * 同步生成 Markdown 并更新同一条记录（成功 / 失败）。
     */
    private void runMarkdownExport(ExportRecordEntity record) {
        try {
            Path file = writeMarkdown(record);
            long size = Files.size(file);
            exportRecordMapper.update(null, new LambdaUpdateWrapper<ExportRecordEntity>()
                    .set(ExportRecordEntity::getStatus, "SUCCESS")
                    .set(ExportRecordEntity::getFilePath, file.toString().replace("\\", "/"))
                    .set(ExportRecordEntity::getFileSize, size)
                    .set(ExportRecordEntity::getErrorMessage, null)
                    .eq(ExportRecordEntity::getId, record.getId())
                    .eq(ExportRecordEntity::getIsDeleted, 0));
            operationLogService.log("EXPORT", record.getId(), "SUCCESS", null, null, null);
        } catch (Exception e) {
            exportRecordMapper.update(null, new LambdaUpdateWrapper<ExportRecordEntity>()
                    .set(ExportRecordEntity::getStatus, "FAILED")
                    .set(ExportRecordEntity::getErrorMessage, e.getMessage())
                    .eq(ExportRecordEntity::getId, record.getId())
                    .eq(ExportRecordEntity::getIsDeleted, 0));
            operationLogService.log("EXPORT", record.getId(), "FAILED", null, null, e.getMessage());
            log.error("export md failed, exportId={}", record.getId(), e);
        }
    }

    /**
     * 重试：不新建记录，按原记录的 request_json 重新生成文件。
     */
    @Transactional
    public ExportDtos.ExportItem retry(Long id, Long operatorId) {
        ExportRecordEntity record = exportRecordMapper.selectById(id);
        if (record == null || record.getIsDeleted() == 1) {
            throw new IllegalArgumentException("export record not found");
        }
        exportRecordMapper.update(null, new LambdaUpdateWrapper<ExportRecordEntity>()
                .set(ExportRecordEntity::getStatus, "RUNNING")
                .set(ExportRecordEntity::getErrorMessage, null)
                .eq(ExportRecordEntity::getId, id)
                .eq(ExportRecordEntity::getIsDeleted, 0));
        operationLogService.log("EXPORT", id, "RETRY", null, null, null);
        runMarkdownExport(record);
        return detail(id);
    }

    public Resource download(Long id) {
        ExportRecordEntity record = exportRecordMapper.selectById(id);
        if (record == null || record.getIsDeleted() == 1) {
            throw new IllegalArgumentException("export record not found");
        }
        if (!"SUCCESS".equalsIgnoreCase(record.getStatus()) || !StringUtils.hasText(record.getFilePath())) {
            throw new IllegalArgumentException("export file not ready");
        }
        Path p = Paths.get(record.getFilePath());
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("export file missing");
        }
        return new FileSystemResource(p);
    }

    private Path writeMarkdown(ExportRecordEntity record) throws Exception {
        ExportRequestOptions opts = ExportRequestOptions.parseOrDefault(record.getRequestJson(), objectMapper);

        StringBuilder md = new StringBuilder();
        md.append("# 测试用例导出\n\n");
        md.append("- ExportNo: ").append(record.getExportNo()).append("\n");
        md.append("- ProjectId: ").append(record.getProjectId()).append("\n");
        md.append("- VersionId: ").append(record.getVersionId()).append("\n");
        md.append("- 导出内容：").append(opts.toDisplayLabel()).append("\n");
        md.append("- Scope: ").append(opts.getScope()).append("\n");
        md.append("- ExportAt: ").append(LocalDateTime.now()).append("\n\n");

        if (opts.includeFunctional()) {
            appendFunctionalSection(md, record.getVersionId());
        }
        if (opts.includeApi()) {
            appendApiSection(md, record.getVersionId());
        }

        Path dir = Paths.get(storageBasePath, "exports", LocalDate.now().toString());
        Files.createDirectories(dir);
        Path file = dir.resolve(record.getExportNo() + ".md");
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        log.info("export md written, exportId={}, file={}", record.getId(), file);
        return file;
    }

    private void appendFunctionalSection(StringBuilder md, Long versionId) {
        md.append("## 功能用例\n\n");
        List<TestCaseEntity> cases = testCaseMapper.selectList(new LambdaQueryWrapper<TestCaseEntity>()
                .eq(TestCaseEntity::getIsDeleted, 0)
                .eq(TestCaseEntity::getVersionId, versionId)
                .orderByAsc(TestCaseEntity::getModuleName)
                .orderByAsc(TestCaseEntity::getFeatureName)
                .orderByAsc(TestCaseEntity::getId));
        if (cases.isEmpty()) {
            md.append("_（当前版本无功能用例）_\n\n");
            return;
        }
        for (TestCaseEntity c : cases) {
            md.append("### ").append(nvl(c.getCaseNo())).append(" ").append(nvl(c.getTitle())).append("\n\n");
            md.append("- 模块：").append(nvl(c.getModuleName())).append("\n");
            md.append("- 功能：").append(nvl(c.getFeatureName())).append("\n");
            md.append("- 优先级：").append(nvl(c.getPriority())).append("\n");
            md.append("- 执行状态：").append(nvl(c.getExecutionStatus())).append("\n");
            md.append("- 评审状态：").append(nvl(c.getReviewStatus())).append("\n");
            if (StringUtils.hasText(c.getPrecondition())) {
                md.append("\n**前置条件**\n\n").append(c.getPrecondition()).append("\n");
            }
            md.append("\n**步骤**\n\n").append(nvl(c.getSteps())).append("\n");
            if (StringUtils.hasText(c.getTestData())) {
                md.append("\n**测试数据**\n\n").append(c.getTestData()).append("\n");
            }
            md.append("\n**预期结果**\n\n").append(nvl(c.getExpectedResult())).append("\n\n");
            md.append("---\n\n");
        }
    }

    private void appendApiSection(StringBuilder md, Long versionId) {
        md.append("## 接口测试用例\n\n");
        List<ApiTestCaseEntity> rows = apiTestCaseMapper.selectList(new LambdaQueryWrapper<ApiTestCaseEntity>()
                .eq(ApiTestCaseEntity::getIsDeleted, 0)
                .eq(ApiTestCaseEntity::getVersionId, versionId)
                .orderByAsc(ApiTestCaseEntity::getModuleName)
                .orderByAsc(ApiTestCaseEntity::getFeatureName)
                .orderByAsc(ApiTestCaseEntity::getId));
        if (rows.isEmpty()) {
            md.append("_（当前版本无接口用例）_\n\n");
            return;
        }
        for (ApiTestCaseEntity c : rows) {
            md.append("### ").append(nvl(c.getCaseNo())).append(" ").append(nvl(c.getTitle())).append("\n\n");
            md.append("- 模块：").append(nvl(c.getModuleName())).append("\n");
            md.append("- 功能：").append(nvl(c.getFeatureName())).append("\n");
            md.append("- 优先级：").append(nvl(c.getPriority())).append("\n");
            md.append("- 执行状态：").append(nvl(c.getExecutionStatus())).append("\n");
            md.append("- 评审状态：").append(nvl(c.getReviewStatus())).append("\n");
            md.append("\n**请求数据（request_json）**\n\n");
            md.append("```json\n").append(prettyJson(c.getRequestJson())).append("\n```\n\n");
            md.append("**预期结果（expected_json）**\n\n");
            md.append("```json\n").append(prettyJson(c.getExpectedJson())).append("\n```\n\n");
            md.append("**断言（assertions_json）**\n\n");
            md.append("```json\n").append(prettyJson(c.getAssertionsJson())).append("\n```\n\n");
            md.append("---\n\n");
        }
    }

    private String prettyJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(raw));
        } catch (Exception e) {
            return raw;
        }
    }

    private String generateExportNo() {
        return "EXP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    private ExportDtos.ExportItem toItem(ExportRecordEntity e) {
        ExportRequestOptions opts = ExportRequestOptions.parseOrDefault(e.getRequestJson(), objectMapper);
        return new ExportDtos.ExportItem(
                e.getId(),
                e.getExportNo(),
                e.getProjectId(),
                e.getVersionId(),
                e.getFormat(),
                e.getScope(),
                e.getStatus(),
                e.getRequestJson(),
                opts.toDisplayLabel(),
                e.getFilePath(),
                e.getFileSize(),
                e.getErrorMessage(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
