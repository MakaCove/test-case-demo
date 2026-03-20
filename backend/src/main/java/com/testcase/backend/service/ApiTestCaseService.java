package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.dto.ApiTestCaseDtos;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.entity.ApiTestCaseEntity;
import com.testcase.backend.entity.GenerationTaskEntity;
import com.testcase.backend.entity.ProjectEntity;
import com.testcase.backend.entity.ProjectVersionEntity;
import com.testcase.backend.entity.RequirementAssetEntity;
import com.testcase.backend.mapper.ApiTestCaseMapper;
import com.testcase.backend.mapper.GenerationTaskMapper;
import com.testcase.backend.mapper.ProjectMapper;
import com.testcase.backend.mapper.ProjectVersionMapper;
import com.testcase.backend.mapper.RequirementAssetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class ApiTestCaseService {
    private static final Logger log = LoggerFactory.getLogger(ApiTestCaseService.class);

    private final ApiTestCaseMapper apiTestCaseMapper;
    private final ProjectMapper projectMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final RequirementAssetMapper requirementAssetMapper;
    private final OperationLogService operationLogService;
    private final ApiTestCaseParser apiTestCaseParser;
    private final ObjectMapper objectMapper;

    public ApiTestCaseService(
            ApiTestCaseMapper apiTestCaseMapper,
            ProjectMapper projectMapper,
            ProjectVersionMapper projectVersionMapper,
            GenerationTaskMapper generationTaskMapper,
            RequirementAssetMapper requirementAssetMapper,
            OperationLogService operationLogService,
            ApiTestCaseParser apiTestCaseParser,
            ObjectMapper objectMapper
    ) {
        this.apiTestCaseMapper = apiTestCaseMapper;
        this.projectMapper = projectMapper;
        this.projectVersionMapper = projectVersionMapper;
        this.generationTaskMapper = generationTaskMapper;
        this.requirementAssetMapper = requirementAssetMapper;
        this.operationLogService = operationLogService;
        this.apiTestCaseParser = apiTestCaseParser;
        this.objectMapper = objectMapper;
    }

    public PagedResult<ApiTestCaseDtos.CaseItem> search(
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
        var wrapper = new LambdaQueryWrapper<ApiTestCaseEntity>()
                .eq(ApiTestCaseEntity::getIsDeleted, 0)
                .orderByDesc(ApiTestCaseEntity::getId);
        if (projectId != null) wrapper.eq(ApiTestCaseEntity::getProjectId, projectId);
        if (versionId != null) wrapper.eq(ApiTestCaseEntity::getVersionId, versionId);
        if (sourceTaskId != null) wrapper.eq(ApiTestCaseEntity::getSourceTaskId, sourceTaskId);
        if (StringUtils.hasText(moduleName)) wrapper.eq(ApiTestCaseEntity::getModuleName, moduleName.trim());
        if (StringUtils.hasText(featureName)) wrapper.eq(ApiTestCaseEntity::getFeatureName, featureName.trim());
        if (StringUtils.hasText(priority)) wrapper.eq(ApiTestCaseEntity::getPriority, priority.trim().toUpperCase());
        if (StringUtils.hasText(executionStatus)) wrapper.eq(ApiTestCaseEntity::getExecutionStatus, executionStatus.trim().toUpperCase());
        if (StringUtils.hasText(reviewStatus)) wrapper.eq(ApiTestCaseEntity::getReviewStatus, reviewStatus.trim().toUpperCase());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ApiTestCaseEntity::getTitle, keyword.trim());
        }
        Page<ApiTestCaseEntity> page = apiTestCaseMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<ApiTestCaseEntity> entities = page.getRecords();
        Set<Long> versionIds = entities.stream()
                .map(ApiTestCaseEntity::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> projectIds = entities.stream()
                .map(ApiTestCaseEntity::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, ProjectEntity> projectMap = loadProjectsByIds(projectIds);
        Map<Long, ProjectVersionEntity> versionMap = loadVersionsByIds(versionIds);
        Map<Long, List<ApiTestCaseDtos.RequirementAssetBrief>> assetsByVersion = loadRequirementBriefsByVersionIds(versionIds);

        Set<Long> sourceTaskIds = entities.stream()
                .map(ApiTestCaseEntity::getSourceTaskId)
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
        for (ApiTestCaseEntity e : entities) {
            Long stId = e.getSourceTaskId();
            List<String> relCodes = stId == null ? null : relationCodesByTaskId.get(stId);
            if (relCodes == null || relCodes.isEmpty()) continue;
            String codesKey = relationCodesKey(relCodes);
            String groupKey = e.getVersionId() + "|" + codesKey;
            relationCodesByGroupKey.putIfAbsent(groupKey, normalizeRelationCodes(relCodes));
        }

        Map<String, List<ApiTestCaseDtos.RequirementAssetBrief>> assetsByGroupKey = new HashMap<>();
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
            List<ApiTestCaseDtos.RequirementAssetBrief> briefs = list.stream().map(this::toRequirementAssetBrief).toList();
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

    public ApiTestCaseDtos.CaseDetail detail(Long caseId) {
        ApiTestCaseEntity entity = apiTestCaseMapper.selectById(caseId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("api test case not found");
        }
        return new ApiTestCaseDtos.CaseDetail(toItem(entity));
    }

    @Transactional
    public ApiTestCaseDtos.CaseItem create(ApiTestCaseDtos.CreateRequest req, Long operatorId) {
        ensureProjectAndVersion(req.projectId(), req.versionId());
        ApiTestCaseEntity entity = new ApiTestCaseEntity();
        entity.setCaseNo(generateCaseNo(req.versionId()));
        entity.setProjectId(req.projectId());
        entity.setVersionId(req.versionId());
        entity.setSourceTaskId(req.sourceTaskId());
        entity.setModuleName(req.moduleName().trim());
        entity.setFeatureName(req.featureName().trim());
        entity.setTitle(req.title().trim());
        entity.setRequestJson(req.requestJson().trim());
        entity.setExpectedJson(req.expectedJson().trim());
        entity.setAssertionsJson(req.assertionsJson().trim());
        entity.setPriority(StringUtils.hasText(req.priority()) ? req.priority().trim().toUpperCase() : "P2");
        entity.setExecutionStatus("NOT_EXECUTED");
        entity.setReviewStatus("PENDING");
        entity.setRemark(req.remark());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setIsDeleted(0);
        apiTestCaseMapper.insert(entity);
        operationLogService.log("API_TEST_CASE", entity.getId(), "CREATE", null, entity, null);
        return toItem(entity);
    }

    @Transactional
    public ApiTestCaseDtos.CaseItem update(Long caseId, ApiTestCaseDtos.UpdateRequest req, Long operatorId) {
        ApiTestCaseEntity entity = apiTestCaseMapper.selectById(caseId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("api test case not found");
        }
        ApiTestCaseDtos.CaseItem before = toItem(entity);
        entity.setModuleName(req.moduleName().trim());
        entity.setFeatureName(req.featureName().trim());
        entity.setTitle(req.title().trim());
        entity.setRequestJson(req.requestJson().trim());
        entity.setExpectedJson(req.expectedJson().trim());
        entity.setAssertionsJson(req.assertionsJson().trim());
        if (StringUtils.hasText(req.priority())) {
            entity.setPriority(req.priority().trim().toUpperCase());
        }
        entity.setRemark(req.remark());
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        apiTestCaseMapper.updateById(entity);
        operationLogService.log("API_TEST_CASE", caseId, "UPDATE", before, toItem(entity), null);
        return toItem(entity);
    }

    @Transactional
    public void delete(Long caseId, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = apiTestCaseMapper.update(null, new LambdaUpdateWrapper<ApiTestCaseEntity>()
                .set(ApiTestCaseEntity::getIsDeleted, 1)
                .set(ApiTestCaseEntity::getUpdatedBy, operatorId)
                .set(ApiTestCaseEntity::getUpdatedAt, now)
                .eq(ApiTestCaseEntity::getId, caseId)
                .eq(ApiTestCaseEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("api test case not found");
        }
        operationLogService.log("API_TEST_CASE", caseId, "DELETE", null, null, null);
    }

    @Transactional
    public void batchDelete(ApiTestCaseDtos.BatchDeleteRequest req, Long operatorId) {
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new IllegalArgumentException("ids is required");
        }
        for (Long id : req.ids()) {
            if (id == null) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = apiTestCaseMapper.update(null, new LambdaUpdateWrapper<ApiTestCaseEntity>()
                    .set(ApiTestCaseEntity::getIsDeleted, 1)
                    .set(ApiTestCaseEntity::getUpdatedBy, operatorId)
                    .set(ApiTestCaseEntity::getUpdatedAt, now)
                    .eq(ApiTestCaseEntity::getId, id)
                    .eq(ApiTestCaseEntity::getIsDeleted, 0));
            if (updated > 0) {
                operationLogService.log("API_TEST_CASE", id, "DELETE", null, null, "batch-delete");
            }
        }
    }

    @Transactional
    public void batchUpdate(ApiTestCaseDtos.BatchUpdateRequest req, Long operatorId) {
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new IllegalArgumentException("ids is required");
        }
        Map<String, Object> fields = req.fields() == null ? Map.of() : req.fields();
        String nextExecution = fields.get("executionStatus") == null ? null : String.valueOf(fields.get("executionStatus"));
        String nextReview = fields.get("reviewStatus") == null ? null : String.valueOf(fields.get("reviewStatus"));
        String nextPriority = fields.get("priority") == null ? null : String.valueOf(fields.get("priority"));
        String nextRemark = fields.get("remark") == null ? null : String.valueOf(fields.get("remark"));

        if ("REJECTED".equalsIgnoreCase(nextReview) && !StringUtils.hasText(req.reviewComment())) {
            throw new IllegalArgumentException("reviewComment is required when reviewStatus=REJECTED");
        }

        for (Long id : req.ids()) {
            if (id == null) continue;
            ApiTestCaseEntity entity = apiTestCaseMapper.selectById(id);
            if (entity == null || entity.getIsDeleted() == 1) continue;
            ApiTestCaseDtos.CaseItem before = toItem(entity);
            if (StringUtils.hasText(nextPriority)) entity.setPriority(nextPriority.trim().toUpperCase());
            if (nextRemark != null) entity.setRemark(nextRemark);
            if (StringUtils.hasText(nextExecution)) {
                entity.setExecutionStatus(nextExecution.trim().toUpperCase());
                entity.setLastExecutedBy(operatorId);
                entity.setLastExecutedAt(LocalDateTime.now());
            }
            if (StringUtils.hasText(nextReview)) {
                String normalized = nextReview.trim().toUpperCase();
                entity.setReviewStatus(normalized);
                if ("REJECTED".equalsIgnoreCase(normalized) || "APPROVED".equalsIgnoreCase(normalized)) {
                    entity.setReviewComment(req.reviewComment());
                    entity.setReviewedBy(operatorId);
                    entity.setReviewedAt(LocalDateTime.now());
                }
            }
            entity.setUpdatedBy(operatorId);
            entity.setUpdatedAt(LocalDateTime.now());
            apiTestCaseMapper.updateById(entity);
            operationLogService.log("API_TEST_CASE", id, "BATCH_UPDATE", before, toItem(entity), null);
        }
    }

    @Transactional
    public ApiTestCaseDtos.CaseItem updateStatus(Long caseId, ApiTestCaseDtos.UpdateStatusRequest req, Long operatorId) {
        ApiTestCaseEntity entity = apiTestCaseMapper.selectById(caseId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("api test case not found");
        }
        ApiTestCaseDtos.CaseItem before = toItem(entity);
        if (StringUtils.hasText(req.executionStatus())) {
            entity.setExecutionStatus(req.executionStatus().trim().toUpperCase());
            entity.setLastExecutedBy(operatorId);
            entity.setLastExecutedAt(LocalDateTime.now());
        }
        if (StringUtils.hasText(req.reviewStatus())) {
            String normalized = req.reviewStatus().trim().toUpperCase();
            if ("REJECTED".equalsIgnoreCase(normalized) && !StringUtils.hasText(req.reviewComment())) {
                throw new IllegalArgumentException("reviewComment is required when reviewStatus=REJECTED");
            }
            entity.setReviewStatus(normalized);
            entity.setReviewComment(req.reviewComment());
            entity.setReviewedBy(operatorId);
            entity.setReviewedAt(LocalDateTime.now());
        }
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        apiTestCaseMapper.updateById(entity);
        operationLogService.log("API_TEST_CASE", caseId, "STATUS_UPDATE", before, toItem(entity), req.reason());
        return toItem(entity);
    }

    /**
     * Materialize API test cases from LLM JSON; optional placeholder when content empty.
     */
    @Transactional
    public int materializeFromTask(Long taskId, String llmContent, Integer count, Long operatorId) {
        GenerationTaskEntity task = generationTaskMapper.selectById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }
        int max = (count == null || count <= 0) ? 10 : Math.min(200, count);
        ensureProjectAndVersion(task.getProjectId(), task.getVersionId());

        List<ApiTestCaseParser.ApiCaseDraft> drafts = StringUtils.hasText(llmContent)
                ? apiTestCaseParser.parse(llmContent)
                : List.of();

        if (!drafts.isEmpty()) {
            int created = 0;
            int limit = Math.min(max, drafts.size());
            for (int i = 0; i < limit; i++) {
                ApiTestCaseParser.ApiCaseDraft d = drafts.get(i);
                ApiTestCaseEntity entity = new ApiTestCaseEntity();
                entity.setCaseNo(generateCaseNo(task.getVersionId()));
                entity.setProjectId(task.getProjectId());
                entity.setVersionId(task.getVersionId());
                entity.setSourceTaskId(taskId);
                entity.setModuleName(d.moduleName());
                entity.setFeatureName(d.featureName());
                entity.setTitle(d.title());
                entity.setRequestJson(d.requestJson());
                entity.setExpectedJson(d.expectedJson());
                entity.setAssertionsJson(d.assertionsJson());
                entity.setPriority(d.priority());
                entity.setExecutionStatus("NOT_EXECUTED");
                entity.setReviewStatus("PENDING");
                String caseRemark = StringUtils.hasText(d.remark())
                        ? d.remark()
                        : ("LLM API · task " + task.getTaskNo());
                entity.setRemark(truncateRemark(caseRemark, 500));
                entity.setCreatedBy(operatorId);
                entity.setUpdatedBy(operatorId);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                entity.setIsDeleted(0);
                apiTestCaseMapper.insert(entity);
                created++;
            }
            operationLogService.log("TASK", taskId, "MATERIALIZE_API_TEST_CASES", null, null, "source=LLM_JSON,count=" + created);
            log.info("materialize API test cases from LLM, taskId={}, created={}", taskId, created);
            return created;
        }

        log.warn("LLM output produced no parseable API cases; using placeholder, taskId={}", taskId);
        RequirementAssetEntity text = requirementAssetMapper.selectOne(new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .eq(RequirementAssetEntity::getVersionId, task.getVersionId())
                .eq(RequirementAssetEntity::getAssetType, "TEXT")
                .orderByDesc(RequirementAssetEntity::getId)
                .last("LIMIT 1"));
        String baseTitle = text == null ? "AI接口用例" : ("基于需求：" + (text.getTitle() == null ? "未命名" : text.getTitle()));

        String placeholderReq = "{\"method\":\"GET\",\"path\":\"/api/example\",\"headers\":{},\"body\":{}}";
        String placeholderExp = "{\"status\":200,\"body\":{}}";
        String placeholderAssert = "[]";

        int created = 0;
        for (int i = 1; i <= max; i++) {
            ApiTestCaseEntity entity = new ApiTestCaseEntity();
            entity.setCaseNo(generateCaseNo(task.getVersionId()));
            entity.setProjectId(task.getProjectId());
            entity.setVersionId(task.getVersionId());
            entity.setSourceTaskId(taskId);
            entity.setModuleName("MVP");
            entity.setFeatureName("AUTO");
            entity.setTitle(baseTitle + " - " + i);
            entity.setRequestJson(placeholderReq);
            entity.setExpectedJson(placeholderExp);
            entity.setAssertionsJson(placeholderAssert);
            entity.setPriority("P2");
            entity.setExecutionStatus("NOT_EXECUTED");
            entity.setReviewStatus("PENDING");
            entity.setRemark("placeholder · task " + task.getTaskNo() + (StringUtils.hasText(llmContent) ? " (parse failed)" : ""));
            entity.setCreatedBy(operatorId);
            entity.setUpdatedBy(operatorId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setIsDeleted(0);
            apiTestCaseMapper.insert(entity);
            created++;
        }
        operationLogService.log("TASK", taskId, "MATERIALIZE_API_TEST_CASES", null, null, "source=PLACEHOLDER,count=" + created);
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
        return "ATC-" + versionId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static String truncateRemark(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private ApiTestCaseDtos.CaseItem toItem(ApiTestCaseEntity e) {
        ProjectEntity project = e.getProjectId() == null ? null : projectMapper.selectById(e.getProjectId());
        ProjectVersionEntity version = e.getVersionId() == null ? null : projectVersionMapper.selectById(e.getVersionId());
        List<String> relCodes = null;
        if (e.getSourceTaskId() != null) {
            GenerationTaskEntity task = generationTaskMapper.selectById(e.getSourceTaskId());
            if (task != null) {
                relCodes = extractReferenceAssetRelationCodes(task.getPayloadJson());
            }
        }
        List<ApiTestCaseDtos.RequirementAssetBrief> assets = loadRequirementBriefsForVersionIdAndRelationCodes(e.getVersionId(), relCodes);
        return toItem(e, project, version, assets);
    }

    private ApiTestCaseDtos.CaseItem toItem(
            ApiTestCaseEntity e,
            ProjectEntity project,
            ProjectVersionEntity version,
            List<ApiTestCaseDtos.RequirementAssetBrief> requirementAssets
    ) {
        String projectName = project == null ? null : project.getName();
        String projectCode = project == null ? null : project.getCode();
        String versionName = version == null ? null : version.getName();
        String versionNo = version == null ? null : version.getVersionNo();

        return new ApiTestCaseDtos.CaseItem(
                e.getId(),
                e.getCaseNo(),
                e.getProjectId(),
                e.getVersionId(),
                e.getSourceTaskId(),
                e.getModuleName(),
                e.getFeatureName(),
                e.getTitle(),
                e.getRequestJson(),
                e.getExpectedJson(),
                e.getAssertionsJson(),
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

    private List<ApiTestCaseDtos.RequirementAssetBrief> loadRequirementBriefsForVersionId(Long versionId) {
        if (versionId == null) return List.of();
        List<RequirementAssetEntity> list = requirementAssetMapper.selectList(
                new LambdaQueryWrapper<RequirementAssetEntity>()
                        .eq(RequirementAssetEntity::getIsDeleted, 0)
                        .eq(RequirementAssetEntity::getVersionId, versionId)
                        .orderByAsc(RequirementAssetEntity::getId)
        );
        return list.stream().map(this::toRequirementAssetBrief).toList();
    }

    private List<ApiTestCaseDtos.RequirementAssetBrief> loadRequirementBriefsForVersionIdAndRelationCodes(
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

    private List<ApiTestCaseDtos.RequirementAssetBrief> resolveRequirementAssetsForEntity(
            ApiTestCaseEntity e,
            Map<Long, List<ApiTestCaseDtos.RequirementAssetBrief>> assetsByVersion,
            Map<Long, List<String>> relationCodesByTaskId,
            Map<String, List<ApiTestCaseDtos.RequirementAssetBrief>> assetsByGroupKey
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
        } catch (Exception ex) {
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

    private Map<Long, List<ApiTestCaseDtos.RequirementAssetBrief>> loadRequirementBriefsByVersionIds(Set<Long> versionIds) {
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

    private ApiTestCaseDtos.RequirementAssetBrief toRequirementAssetBrief(RequirementAssetEntity a) {
        String title = a.getTitle();
        if (!StringUtils.hasText(title)) {
            title = a.getFileName();
        }
        return new ApiTestCaseDtos.RequirementAssetBrief(
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
}
