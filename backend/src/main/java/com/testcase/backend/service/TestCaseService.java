package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.testcase.backend.common.StatusConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.TestCaseDtos;
import com.testcase.backend.entity.*;
import com.testcase.backend.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TestCaseService {
    private static final Logger log = LoggerFactory.getLogger(TestCaseService.class);

    private final TestCaseMapper testCaseMapper;
    private final TestCaseStatusLogMapper statusLogMapper;
    private final TestCaseHistoryMapper historyMapper;
    private final ProjectMapper projectMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final RequirementAssetMapper requirementAssetMapper;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;
    private final GeneratedTestCaseParser generatedTestCaseParser;

    public TestCaseService(
            TestCaseMapper testCaseMapper,
            TestCaseStatusLogMapper statusLogMapper,
            TestCaseHistoryMapper historyMapper,
            ProjectMapper projectMapper,
            ProjectVersionMapper projectVersionMapper,
            GenerationTaskMapper generationTaskMapper,
            RequirementAssetMapper requirementAssetMapper,
            ObjectMapper objectMapper,
            OperationLogService operationLogService,
            GeneratedTestCaseParser generatedTestCaseParser
    ) {
        this.testCaseMapper = testCaseMapper;
        this.statusLogMapper = statusLogMapper;
        this.historyMapper = historyMapper;
        this.projectMapper = projectMapper;
        this.projectVersionMapper = projectVersionMapper;
        this.generationTaskMapper = generationTaskMapper;
        this.requirementAssetMapper = requirementAssetMapper;
        this.objectMapper = objectMapper;
        this.operationLogService = operationLogService;
        this.generatedTestCaseParser = generatedTestCaseParser;
    }

    public PagedResult<TestCaseDtos.CaseItem> search(
            Long projectId,
            Long versionId,
            Long sourceTaskId,
            String moduleName,
            String featureName,
            String priority,
            String executionStatus,
            String reviewStatus,
            String keyword,
            int pageNo,
            int pageSize
    ) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<TestCaseEntity>()
                .eq(TestCaseEntity::getIsDeleted, 0)
                .orderByDesc(TestCaseEntity::getId);
        if (projectId != null) wrapper.eq(TestCaseEntity::getProjectId, projectId);
        if (versionId != null) wrapper.eq(TestCaseEntity::getVersionId, versionId);
        if (sourceTaskId != null) wrapper.eq(TestCaseEntity::getSourceTaskId, sourceTaskId);
        if (StringUtils.hasText(moduleName)) wrapper.eq(TestCaseEntity::getModuleName, moduleName.trim());
        if (StringUtils.hasText(featureName)) wrapper.eq(TestCaseEntity::getFeatureName, featureName.trim());
        if (StringUtils.hasText(priority)) wrapper.eq(TestCaseEntity::getPriority, priority.trim().toUpperCase());
        if (StringUtils.hasText(executionStatus)) wrapper.eq(TestCaseEntity::getExecutionStatus, executionStatus.trim().toUpperCase());
        if (StringUtils.hasText(reviewStatus)) wrapper.eq(TestCaseEntity::getReviewStatus, reviewStatus.trim().toUpperCase());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(TestCaseEntity::getTitle, keyword.trim());
        }
        Page<TestCaseEntity> page = testCaseMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<TestCaseEntity> entities = page.getRecords();
        Set<Long> versionIds = entities.stream()
                .map(TestCaseEntity::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> projectIds = entities.stream()
                .map(TestCaseEntity::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, ProjectEntity> projectMap = loadProjectsByIds(projectIds);
        Map<Long, ProjectVersionEntity> versionMap = loadVersionsByIds(versionIds);
        Map<Long, List<TestCaseDtos.RequirementAssetBrief>> assetsByVersion = loadRequirementBriefsByVersionIds(versionIds);

        Set<Long> sourceTaskIds = entities.stream()
                .map(TestCaseEntity::getSourceTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // taskId -> referenceAssetRelationCodes（为 null 表示使用整版本资产）
        Map<Long, List<String>> relationCodesByTaskId = new HashMap<>();
        if (!sourceTaskIds.isEmpty()) {
            List<GenerationTaskEntity> tasks = generationTaskMapper.selectList(
                    new LambdaQueryWrapper<GenerationTaskEntity>()
                            .in(GenerationTaskEntity::getId, sourceTaskIds));
            for (GenerationTaskEntity t : tasks) {
                relationCodesByTaskId.put(t.getId(), extractReferenceAssetRelationCodes(t.getPayloadJson()));
            }
        }

        // 针对“选定了 relationCodes 的情况”，按 versionId + codesKey 分组，一次性批量拉取对应资产，避免 N+1
        Map<String, List<String>> relationCodesByGroupKey = new HashMap<>();
        for (TestCaseEntity e : entities) {
            Long stId = e.getSourceTaskId();
            List<String> relCodes = stId == null ? null : relationCodesByTaskId.get(stId);
            if (relCodes == null || relCodes.isEmpty()) continue;
            String codesKey = relationCodesKey(relCodes);
            String groupKey = e.getVersionId() + "|" + codesKey;
            relationCodesByGroupKey.putIfAbsent(groupKey, normalizeRelationCodes(relCodes));
        }

        Map<String, List<TestCaseDtos.RequirementAssetBrief>> assetsByGroupKey = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : relationCodesByGroupKey.entrySet()) {
            String groupKey = entry.getKey();
            List<String> relCodes = entry.getValue();
            if (relCodes == null || relCodes.isEmpty()) continue;
            int idx = groupKey.indexOf('|');
            long vId = Long.parseLong(groupKey.substring(0, idx));
            List<RequirementAssetEntity> list = requirementAssetMapper.selectList(
                    new LambdaQueryWrapper<RequirementAssetEntity>()
                            .eq(RequirementAssetEntity::getIsDeleted, 0)
                            .eq(RequirementAssetEntity::getVersionId, vId)
                            .in(RequirementAssetEntity::getRelationCode, relCodes)
                            .orderByAsc(RequirementAssetEntity::getId));
            List<TestCaseDtos.RequirementAssetBrief> briefs = list.stream().map(this::toRequirementAssetBrief).toList();
            assetsByGroupKey.put(groupKey, briefs);
        }

        var items = entities.stream()
                .map(e -> toItem(e,
                        projectMap.get(e.getProjectId()),
                        versionMap.get(e.getVersionId()),
                        resolveRequirementAssetsForEntity(e, assetsByVersion, relationCodesByTaskId, assetsByGroupKey)))
                .toList();
        return new PagedResult<>(items, safePageNo, safePageSize, page.getTotal());
    }

    @Transactional
    public TestCaseDtos.CaseItem create(TestCaseDtos.CreateRequest req, Long operatorId) {
        ensureProjectAndVersion(req.projectId(), req.versionId());
        TestCaseEntity entity = new TestCaseEntity();
        entity.setCaseNo(generateCaseNo(req.versionId()));
        entity.setProjectId(req.projectId());
        entity.setVersionId(req.versionId());
        entity.setSourceTaskId(req.sourceTaskId());
        entity.setModuleName(req.moduleName().trim());
        entity.setFeatureName(req.featureName().trim());
        entity.setTitle(req.title().trim());
        entity.setPrecondition(req.precondition());
        entity.setSteps(req.steps());
        entity.setTestData(req.testData());
        entity.setExpectedResult(req.expectedResult());
        entity.setPriority(StringUtils.hasText(req.priority()) ? req.priority().trim().toUpperCase() : "P2");
        entity.setExecutionStatus(StatusConstants.CaseExecution.NOT_EXECUTED);
        entity.setReviewStatus(StatusConstants.CaseReview.PENDING);
        entity.setRemark(req.remark());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setIsDeleted(0);
        testCaseMapper.insert(entity);

        insertHistory(entity.getId(), "CREATE", operatorId, safeJson(entity));
        operationLogService.log("TEST_CASE", entity.getId(), "CREATE", null, entity, null);
        return toItem(entity);
    }

    public TestCaseDtos.CaseDetail detail(Long caseId) {
        TestCaseEntity entity = testCaseMapper.selectById(caseId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("test case not found");
        }
        var statusLogs = statusLogMapper.selectList(new LambdaQueryWrapper<TestCaseStatusLogEntity>()
                        .eq(TestCaseStatusLogEntity::getCaseId, caseId)
                        .orderByDesc(TestCaseStatusLogEntity::getId)
                        .last("LIMIT 50"))
                .stream().map(this::toStatusLog).toList();
        var histories = historyMapper.selectList(new LambdaQueryWrapper<TestCaseHistoryEntity>()
                        .eq(TestCaseHistoryEntity::getCaseId, caseId)
                        .orderByDesc(TestCaseHistoryEntity::getId)
                        .last("LIMIT 30"))
                .stream().map(this::toHistory).toList();
        return new TestCaseDtos.CaseDetail(toItem(entity), statusLogs, histories);
    }

    @Transactional
    public TestCaseDtos.CaseItem update(Long caseId, TestCaseDtos.UpdateRequest req, Long operatorId) {
        TestCaseEntity entity = testCaseMapper.selectById(caseId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("test case not found");
        }
        TestCaseDtos.CaseItem before = toItem(entity);
        entity.setModuleName(req.moduleName().trim());
        entity.setFeatureName(req.featureName().trim());
        entity.setTitle(req.title().trim());
        entity.setPrecondition(req.precondition());
        entity.setSteps(req.steps());
        entity.setTestData(req.testData());
        entity.setExpectedResult(req.expectedResult());
        if (StringUtils.hasText(req.priority())) {
            entity.setPriority(req.priority().trim().toUpperCase());
        }
        entity.setRemark(req.remark());
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.updateById(entity);
        insertHistory(caseId, "UPDATE", operatorId, safeJson(entity));
        operationLogService.log("TEST_CASE", caseId, "UPDATE", before, toItem(entity), null);
        return toItem(entity);
    }

    @Transactional
    public void delete(Long caseId, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = testCaseMapper.update(null, new LambdaUpdateWrapper<TestCaseEntity>()
                .set(TestCaseEntity::getIsDeleted, 1)
                .set(TestCaseEntity::getUpdatedBy, operatorId)
                .set(TestCaseEntity::getUpdatedAt, now)
                .eq(TestCaseEntity::getId, caseId)
                .eq(TestCaseEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("test case not found");
        }
        operationLogService.log("TEST_CASE", caseId, "DELETE", null, null, null);
    }

    @Transactional
    public void batchDelete(TestCaseDtos.BatchDeleteRequest req, Long operatorId) {
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new IllegalArgumentException("ids is required");
        }
        for (Long id : req.ids()) {
            if (id == null) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = testCaseMapper.update(null, new LambdaUpdateWrapper<TestCaseEntity>()
                    .set(TestCaseEntity::getIsDeleted, 1)
                    .set(TestCaseEntity::getUpdatedBy, operatorId)
                    .set(TestCaseEntity::getUpdatedAt, now)
                    .eq(TestCaseEntity::getId, id)
                    .eq(TestCaseEntity::getIsDeleted, 0));
            if (updated > 0) {
                operationLogService.log("TEST_CASE", id, "DELETE", null, null, "batch-delete");
            }
        }
    }

    @Transactional
    public void batchUpdate(TestCaseDtos.BatchUpdateRequest req, Long operatorId) {
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new IllegalArgumentException("ids is required");
        }
        Map<String, Object> fields = req.fields() == null ? Map.of() : req.fields();
        String nextExecution = fields.get("executionStatus") == null ? null : String.valueOf(fields.get("executionStatus"));
        String nextReview = fields.get("reviewStatus") == null ? null : String.valueOf(fields.get("reviewStatus"));
        String nextPriority = fields.get("priority") == null ? null : String.valueOf(fields.get("priority"));
        String nextRemark = fields.get("remark") == null ? null : String.valueOf(fields.get("remark"));

        if (StatusConstants.CaseReview.REJECTED.equalsIgnoreCase(nextReview) && !StringUtils.hasText(req.reviewComment())) {
            throw new IllegalArgumentException("reviewComment is required when reviewStatus=REJECTED");
        }

        for (Long id : req.ids()) {
            if (id == null) continue;
            TestCaseEntity entity = testCaseMapper.selectById(id);
            if (entity == null || entity.getIsDeleted() == 1) continue;
            TestCaseDtos.CaseItem before = toItem(entity);
            if (StringUtils.hasText(nextPriority)) entity.setPriority(nextPriority.trim().toUpperCase());
            if (nextRemark != null) entity.setRemark(nextRemark);
            if (StringUtils.hasText(nextExecution)) {
                changeStatus(entity, "execution_status", entity.getExecutionStatus(), nextExecution.trim().toUpperCase(), req.reason(), operatorId);
                entity.setExecutionStatus(nextExecution.trim().toUpperCase());
                entity.setLastExecutedBy(operatorId);
                entity.setLastExecutedAt(LocalDateTime.now());
            }
            if (StringUtils.hasText(nextReview)) {
                String normalized = nextReview.trim().toUpperCase();
                changeStatus(entity, "review_status", entity.getReviewStatus(), normalized, req.reason(), operatorId);
                entity.setReviewStatus(normalized);
                if (StatusConstants.CaseReview.REJECTED.equalsIgnoreCase(normalized)) {
                    entity.setReviewComment(req.reviewComment());
                    entity.setReviewedBy(operatorId);
                    entity.setReviewedAt(LocalDateTime.now());
                } else if (StatusConstants.CaseReview.APPROVED.equalsIgnoreCase(normalized)) {
                    entity.setReviewComment(req.reviewComment());
                    entity.setReviewedBy(operatorId);
                    entity.setReviewedAt(LocalDateTime.now());
                }
            }
            entity.setUpdatedBy(operatorId);
            entity.setUpdatedAt(LocalDateTime.now());
            testCaseMapper.updateById(entity);
            insertHistory(id, "BATCH_UPDATE", operatorId, safeJson(entity));
            operationLogService.log("TEST_CASE", id, "BATCH_UPDATE", before, toItem(entity), null);
        }
    }

    @Transactional
    public TestCaseDtos.CaseItem updateStatus(Long caseId, TestCaseDtos.UpdateStatusRequest req, Long operatorId) {
        TestCaseEntity entity = testCaseMapper.selectById(caseId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("test case not found");
        }
        TestCaseDtos.CaseItem before = toItem(entity);
        if (StringUtils.hasText(req.executionStatus())) {
            String normalized = req.executionStatus().trim().toUpperCase();
            changeStatus(entity, "execution_status", entity.getExecutionStatus(), normalized, req.reason(), operatorId);
            entity.setExecutionStatus(normalized);
            entity.setLastExecutedBy(operatorId);
            entity.setLastExecutedAt(LocalDateTime.now());
        }
        if (StringUtils.hasText(req.reviewStatus())) {
            String normalized = req.reviewStatus().trim().toUpperCase();
            if (StatusConstants.CaseReview.REJECTED.equalsIgnoreCase(normalized) && !StringUtils.hasText(req.reviewComment())) {
                throw new IllegalArgumentException("reviewComment is required when reviewStatus=REJECTED");
            }
            changeStatus(entity, "review_status", entity.getReviewStatus(), normalized, req.reason(), operatorId);
            entity.setReviewStatus(normalized);
            entity.setReviewComment(req.reviewComment());
            entity.setReviewedBy(operatorId);
            entity.setReviewedAt(LocalDateTime.now());
        }
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.updateById(entity);
        insertHistory(caseId, "UPDATE", operatorId, safeJson(entity));
        operationLogService.log("TEST_CASE", caseId, "STATUS_UPDATE", before, toItem(entity), req.reason());
        return toItem(entity);
    }

    /**
     * Manual materialization without LLM body (legacy / demo): inserts placeholder cases.
     */
    @Transactional
    public int materializeFromTask(Long taskId, Integer count, Long operatorId) {
        return materializeFromTask(taskId, null, count, operatorId);
    }

    /**
     * Persists test cases for a generation task. When {@code llmContent} is present, parses JSON from the model
     * and inserts real rows; otherwise falls back to placeholder cases.
     */
    @Transactional
    public int materializeFromTask(Long taskId, String llmContent, Integer count, Long operatorId) {
        GenerationTaskEntity task = generationTaskMapper.selectById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }
        int max = (count == null || count <= 0) ? 10 : Math.min(200, count);
        ensureProjectAndVersion(task.getProjectId(), task.getVersionId());

        List<GeneratedTestCaseParser.GeneratedCaseDraft> drafts = StringUtils.hasText(llmContent)
                ? generatedTestCaseParser.parse(llmContent)
                : List.of();

        if (!drafts.isEmpty()) {
            int created = 0;
            int limit = Math.min(max, drafts.size());
            for (int i = 0; i < limit; i++) {
                GeneratedTestCaseParser.GeneratedCaseDraft d = drafts.get(i);
                TestCaseEntity entity = new TestCaseEntity();
                entity.setCaseNo(generateCaseNo(task.getVersionId()));
                entity.setProjectId(task.getProjectId());
                entity.setVersionId(task.getVersionId());
                entity.setSourceTaskId(taskId);
                entity.setModuleName(d.moduleName());
                entity.setFeatureName(d.featureName());
                entity.setTitle(d.title());
                entity.setPrecondition(d.precondition());
                entity.setSteps(d.steps());
                entity.setTestData(d.testData());
                entity.setExpectedResult(d.expectedResult());
                entity.setPriority(d.priority());
                entity.setExecutionStatus(StatusConstants.CaseExecution.NOT_EXECUTED);
                entity.setReviewStatus(StatusConstants.CaseReview.PENDING);
                entity.setRemark("LLM JSON · task " + task.getTaskNo());
                entity.setCreatedBy(operatorId);
                entity.setUpdatedBy(operatorId);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                entity.setIsDeleted(0);
                testCaseMapper.insert(entity);
                insertHistory(entity.getId(), "CREATE", operatorId, safeJson(entity));
                created++;
            }
            operationLogService.log("TASK", taskId, "MATERIALIZE_TEST_CASES", null, null,
                    "source=LLM_JSON,count=" + created);
            log.info("materialize test cases from LLM JSON, taskId={}, created={}", taskId, created);
            return created;
        }

        log.warn("LLM output produced no parseable test cases; using placeholder materialization, taskId={}", taskId);

        RequirementAssetEntity text = requirementAssetMapper.selectOne(new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .eq(RequirementAssetEntity::getVersionId, task.getVersionId())
                .eq(RequirementAssetEntity::getAssetType, "TEXT")
                .orderByDesc(RequirementAssetEntity::getId)
                .last("LIMIT 1"));
        String baseTitle = text == null ? "AI生成用例" : ("基于需求：" + (text.getTitle() == null ? "未命名" : text.getTitle()));

        int created = 0;
        for (int i = 1; i <= max; i++) {
            TestCaseEntity entity = new TestCaseEntity();
            entity.setCaseNo(generateCaseNo(task.getVersionId()));
            entity.setProjectId(task.getProjectId());
            entity.setVersionId(task.getVersionId());
            entity.setSourceTaskId(taskId);
            entity.setModuleName("MVP");
            entity.setFeatureName("AUTO");
            entity.setTitle(baseTitle + " - " + i);
            entity.setPrecondition(null);
            entity.setSteps("1. 打开页面\n2. 执行操作\n3. 校验结果");
            entity.setTestData(null);
            entity.setExpectedResult("符合预期");
            entity.setPriority("P2");
            entity.setExecutionStatus(StatusConstants.CaseExecution.NOT_EXECUTED);
            entity.setReviewStatus(StatusConstants.CaseReview.PENDING);
            entity.setRemark("placeholder · task " + task.getTaskNo() + (StringUtils.hasText(llmContent) ? " (parse failed)" : ""));
            entity.setCreatedBy(operatorId);
            entity.setUpdatedBy(operatorId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setIsDeleted(0);
            testCaseMapper.insert(entity);
            insertHistory(entity.getId(), "CREATE", operatorId, safeJson(entity));
            created++;
        }
        operationLogService.log("TASK", taskId, "MATERIALIZE_TEST_CASES", null, null, "source=PLACEHOLDER,count=" + created);
        log.info("materialize placeholder test cases, taskId={}, created={}", taskId, created);
        return created;
    }

    private void ensureProjectAndVersion(Long projectId, Long versionId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new IllegalArgumentException("project not found");
        }
        ProjectVersionEntity version = projectVersionMapper.selectById(versionId);
        if (version == null || version.getIsDeleted() == 1 || !projectId.equals(version.getProjectId())) {
            throw new IllegalArgumentException("version not found");
        }
    }

    private String generateCaseNo(Long versionId) {
        return "TC-" + versionId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private void changeStatus(TestCaseEntity entity, String field, String oldValue, String newValue, String reason, Long operatorId) {
        // 状态未变且无说明时跳过；若填写了原因/备注（如批量再次「通过」补充说明），仍写入一条日志便于追溯
        if (Objects.equals(oldValue, newValue) && !StringUtils.hasText(reason)) {
            return;
        }
        TestCaseStatusLogEntity logEntity = new TestCaseStatusLogEntity();
        logEntity.setCaseId(entity.getId());
        logEntity.setFieldName(field);
        logEntity.setOldValue(oldValue);
        logEntity.setNewValue(newValue);
        logEntity.setReason(reason);
        logEntity.setChangedBy(operatorId);
        logEntity.setChangedAt(LocalDateTime.now());
        statusLogMapper.insert(logEntity);
    }

    private void insertHistory(Long caseId, String changeType, Long operatorId, String snapshotJson) {
        TestCaseHistoryEntity hist = new TestCaseHistoryEntity();
        hist.setCaseId(caseId);
        hist.setSnapshotJson(snapshotJson);
        hist.setChangedBy(operatorId);
        hist.setChangedAt(LocalDateTime.now());
        hist.setChangeType(changeType);
        historyMapper.insert(hist);
    }

    private TestCaseDtos.CaseItem toItem(TestCaseEntity e) {
        ProjectEntity project = e.getProjectId() == null ? null : projectMapper.selectById(e.getProjectId());
        ProjectVersionEntity version = e.getVersionId() == null ? null : projectVersionMapper.selectById(e.getVersionId());
        List<String> relCodes = null;
        if (e.getSourceTaskId() != null) {
            GenerationTaskEntity task = generationTaskMapper.selectById(e.getSourceTaskId());
            if (task != null) {
                relCodes = extractReferenceAssetRelationCodes(task.getPayloadJson());
            }
        }
        List<TestCaseDtos.RequirementAssetBrief> assets = loadRequirementBriefsForVersionIdAndRelationCodes(e.getVersionId(), relCodes);
        return toItem(e, project, version, assets);
    }

    private TestCaseDtos.CaseItem toItem(
            TestCaseEntity e,
            ProjectEntity project,
            ProjectVersionEntity version,
            List<TestCaseDtos.RequirementAssetBrief> requirementAssets
    ) {
        String projectName = project == null ? null : project.getName();
        String projectCode = project == null ? null : project.getCode();
        String versionName = version == null ? null : version.getName();
        String versionNo = version == null ? null : version.getVersionNo();

        return new TestCaseDtos.CaseItem(
                e.getId(),
                e.getCaseNo(),
                e.getProjectId(),
                e.getVersionId(),
                e.getSourceTaskId(),
                e.getModuleName(),
                e.getFeatureName(),
                e.getTitle(),
                e.getPrecondition(),
                e.getSteps(),
                e.getTestData(),
                e.getExpectedResult(),
                e.getPriority(),
                e.getExecutionStatus(),
                e.getReviewStatus(),
                e.getReviewComment(),
                e.getLastExecutedAt(),
                e.getReviewedAt(),
                e.getRemark(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                projectName,
                projectCode,
                versionName,
                versionNo,
                requirementAssets == null ? List.of() : requirementAssets
        );
    }

    private List<TestCaseDtos.RequirementAssetBrief> loadRequirementBriefsForVersionId(Long versionId) {
        if (versionId == null) return List.of();
        List<RequirementAssetEntity> list = requirementAssetMapper.selectList(
                new LambdaQueryWrapper<RequirementAssetEntity>()
                        .eq(RequirementAssetEntity::getIsDeleted, 0)
                        .eq(RequirementAssetEntity::getVersionId, versionId)
                        .orderByAsc(RequirementAssetEntity::getId)
        );
        return list.stream().map(this::toRequirementAssetBrief).toList();
    }

    private List<TestCaseDtos.RequirementAssetBrief> loadRequirementBriefsForVersionIdAndRelationCodes(
            Long versionId,
            List<String> relationCodes
    ) {
        if (versionId == null) return List.of();
        LambdaQueryWrapper<RequirementAssetEntity> wrapper = new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .eq(RequirementAssetEntity::getVersionId, versionId)
                .orderByAsc(RequirementAssetEntity::getId);
        if (relationCodes != null && !relationCodes.isEmpty()) {
            wrapper.in(RequirementAssetEntity::getRelationCode, normalizeRelationCodes(relationCodes));
        }
        List<RequirementAssetEntity> list = requirementAssetMapper.selectList(wrapper);
        return list.stream().map(this::toRequirementAssetBrief).toList();
    }

    private List<TestCaseDtos.RequirementAssetBrief> resolveRequirementAssetsForEntity(
            TestCaseEntity e,
            Map<Long, List<TestCaseDtos.RequirementAssetBrief>> assetsByVersion,
            Map<Long, List<String>> relationCodesByTaskId,
            Map<String, List<TestCaseDtos.RequirementAssetBrief>> assetsByGroupKey
    ) {
        if (e == null) return List.of();
        Long stId = e.getSourceTaskId();
        List<String> relCodes = stId == null ? null : relationCodesByTaskId.get(stId);
        if (relCodes == null || relCodes.isEmpty()) {
            return assetsByVersion.getOrDefault(e.getVersionId(), List.of());
        }
        String groupKey = e.getVersionId() + "|" + relationCodesKey(relCodes);
        return assetsByGroupKey.getOrDefault(groupKey, List.of());
    }

    private List<String> extractReferenceAssetRelationCodes(String payloadJson) {
        if (!Objects.nonNull(payloadJson) || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            if (node == null) return null;
            JsonNode arr = node.get("referenceAssetRelationCodes");
            if (arr == null || !arr.isArray()) return null;
            List<String> out = new ArrayList<>();
            for (JsonNode item : arr) {
                if (item == null || item.isNull()) continue;
                String s = item.asText();
                if (s == null) continue;
                s = s.trim();
                if (s.isEmpty()) continue;
                out.add(s);
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> normalizeRelationCodes(List<String> relationCodes) {
        if (relationCodes == null || relationCodes.isEmpty()) return List.of();
        return relationCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private String relationCodesKey(List<String> relationCodes) {
        List<String> normalized = normalizeRelationCodes(relationCodes);
        if (normalized.isEmpty()) return "";
        return String.join(",", normalized);
    }

    private Map<Long, List<TestCaseDtos.RequirementAssetBrief>> loadRequirementBriefsByVersionIds(Set<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Map.of();
        }
        List<RequirementAssetEntity> list = requirementAssetMapper.selectList(
                new LambdaQueryWrapper<RequirementAssetEntity>()
                        .eq(RequirementAssetEntity::getIsDeleted, 0)
                        .in(RequirementAssetEntity::getVersionId, versionIds)
                        .orderByAsc(RequirementAssetEntity::getVersionId)
                        .orderByAsc(RequirementAssetEntity::getId)
        );
        return list.stream().collect(Collectors.groupingBy(
                RequirementAssetEntity::getVersionId,
                LinkedHashMap::new,
                Collectors.mapping(this::toRequirementAssetBrief, Collectors.toList())
        ));
    }

    private TestCaseDtos.RequirementAssetBrief toRequirementAssetBrief(RequirementAssetEntity a) {
        String title = a.getTitle();
        if (!StringUtils.hasText(title)) {
            title = a.getFileName();
        }
        return new TestCaseDtos.RequirementAssetBrief(
                a.getAssetCode(),
                title,
                a.getAssetType()
        );
    }

    private Map<Long, ProjectEntity> loadProjectsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<ProjectEntity> list = projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>().in(ProjectEntity::getId, ids));
        return list.stream().collect(Collectors.toMap(ProjectEntity::getId, p -> p, (a, b) -> a));
    }

    private Map<Long, ProjectVersionEntity> loadVersionsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<ProjectVersionEntity> list = projectVersionMapper.selectList(new LambdaQueryWrapper<ProjectVersionEntity>().in(ProjectVersionEntity::getId, ids));
        return list.stream().collect(Collectors.toMap(ProjectVersionEntity::getId, v -> v, (a, b) -> a));
    }

    private TestCaseDtos.StatusLogItem toStatusLog(TestCaseStatusLogEntity e) {
        return new TestCaseDtos.StatusLogItem(
                e.getId(),
                e.getCaseId(),
                e.getFieldName(),
                e.getOldValue(),
                e.getNewValue(),
                e.getReason(),
                e.getChangedBy(),
                e.getChangedAt()
        );
    }

    private TestCaseDtos.HistoryItem toHistory(TestCaseHistoryEntity e) {
        return new TestCaseDtos.HistoryItem(
                e.getId(),
                e.getCaseId(),
                e.getSnapshotJson(),
                e.getChangedBy(),
                e.getChangedAt(),
                e.getChangeType()
        );
    }

    private String safeJson(Object any) {
        try {
            return objectMapper.writeValueAsString(any);
        } catch (Exception e) {
            return null;
        }
    }
}

