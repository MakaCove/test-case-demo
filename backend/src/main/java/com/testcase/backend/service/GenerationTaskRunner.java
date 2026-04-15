package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.testcase.backend.entity.GenerationTaskEntity;
import com.testcase.backend.entity.ModelConfigEntity;
import com.testcase.backend.entity.PromptTemplateEntity;
import com.testcase.backend.entity.RequirementAssetEntity;
import com.testcase.backend.mapper.GenerationTaskMapper;
import com.testcase.backend.mapper.ModelConfigMapper;
import com.testcase.backend.mapper.PromptTemplateMapper;
import com.testcase.backend.mapper.RequirementAssetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GenerationTaskRunner {
    private static final Logger log = LoggerFactory.getLogger(GenerationTaskRunner.class);
    /**
     * 手动触发“队列推进”开关。
     * - 当为 false 时，不会把 QUEUED 自动提升为 RUNNING。
     * - 当为 true 时，会持续按队列顺序把 QUEUED 提升为 RUNNING，直到队列为空（无 QUEUED）。
     */
    public static final AtomicBoolean manualQueueEnabled = new AtomicBoolean(false);
    public static volatile Long manualQueueCutoffQueueNo = null;

    private final GenerationTaskMapper generationTaskMapper;
    private final OperationLogService operationLogService;
    private final ModelConfigMapper modelConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final RequirementAssetMapper requirementAssetMapper;
    private final ModelClient modelClient;
    private final TestCaseService testCaseService;
    private final ApiTestCaseService apiTestCaseService;
    private final ObjectMapper objectMapper;
    private final GenerationTaskService generationTaskService;
    private final RequirementAssetPathResolver requirementAssetPathResolver;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;

    public GenerationTaskRunner(
            GenerationTaskMapper generationTaskMapper,
            OperationLogService operationLogService,
            ModelConfigMapper modelConfigMapper,
            PromptTemplateMapper promptTemplateMapper,
            RequirementAssetMapper requirementAssetMapper,
            ModelClient modelClient,
            TestCaseService testCaseService,
            ApiTestCaseService apiTestCaseService,
            ObjectMapper objectMapper,
            GenerationTaskService generationTaskService,
            RequirementAssetPathResolver requirementAssetPathResolver
    ) {
        this.generationTaskMapper = generationTaskMapper;
        this.operationLogService = operationLogService;
        this.modelConfigMapper = modelConfigMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.requirementAssetMapper = requirementAssetMapper;
        this.modelClient = modelClient;
        this.testCaseService = testCaseService;
        this.apiTestCaseService = apiTestCaseService;
        this.objectMapper = objectMapper;
        this.generationTaskService = generationTaskService;
        this.requirementAssetPathResolver = requirementAssetPathResolver;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            GenerationTaskEntity next = generationTaskMapper.selectOne(
                    new LambdaQueryWrapper<GenerationTaskEntity>()
                            .eq(GenerationTaskEntity::getIsDeleted, 0)
                            .eq(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_RUNNING)
                            .isNull(GenerationTaskEntity::getFinishedAt)
                            .orderByAsc(GenerationTaskEntity::getStartedAt)
                            .last("LIMIT 1")
            );
            if (next == null) {
                return;
            }
            log.info("task executing (manual start), taskId={}, taskNo={}", next.getId(), next.getTaskNo());

            try {
                GenerationTaskEntity latest = generationTaskMapper.selectById(next.getId());
                if (latest == null || latest.getIsDeleted() == 1) {
                    return;
                }
                if (GenerationTaskService.STATUS_CANCELLED.equals(latest.getStatus())) {
                    log.info("task skipped due to status={}, taskId={}", latest.getStatus(), next.getId());
                    return;
                }
                if (!GenerationTaskService.STATUS_RUNNING.equals(latest.getStatus())) {
                    return;
                }

                ModelConfigEntity cfg = modelConfigMapper.selectById(latest.getModelConfigId());
                if (cfg == null || cfg.getIsDeleted() == 1) {
                    throw new IllegalArgumentException("model config not found");
                }
                PromptTemplateEntity tpl = promptTemplateMapper.selectById(latest.getPromptTemplateId());
                if (tpl == null || tpl.getIsDeleted() == 1) {
                    throw new IllegalArgumentException("prompt template not found");
                }

                List<String> refAssetRelationCodes = extractReferenceAssetRelationCodes(latest.getPayloadJson());
                // 生成上下文来源：当前版本 + payload.referenceVersionIds（若有）
                List<Long> contextVersionIds = resolveContextVersionIds(latest);
                String requirementText = buildRequirementTextForModel(contextVersionIds, refAssetRelationCodes);
                List<String> imageDataUrls = loadPrototypeImageDataUrls(contextVersionIds, refAssetRelationCodes);

                String prompt = tpl.getContent();
                ModelClient.ModelChatInput chatInput = new ModelClient.ModelChatInput(prompt, requirementText, imageDataUrls);
                ModelClient.ModelCallResult result = modelClient.chatCompletion(cfg, chatInput);

                String caseCategory = StringUtils.hasText(latest.getCaseCategory()) ? latest.getCaseCategory() : "FUNCTIONAL";
                int createdCases;
                if ("API".equalsIgnoreCase(caseCategory)) {
                    createdCases = apiTestCaseService.materializeFromTask(
                            latest.getId(), result.content(), 50, latest.getSubmittedBy());
                } else {
                    createdCases = testCaseService.materializeFromTask(
                            latest.getId(), result.content(), 50, latest.getSubmittedBy());
                }
                int rawLen = StringUtils.hasText(result.content()) ? result.content().length() : 0;
                String summary = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                        .put("model", result.model())
                        .put("caseCategory", caseCategory)
                        .put("createdCases", createdCases)
                        .put("tokens", result.totalTokens())
                        .put("costMs", result.costMs())
                        .put("rawLength", rawLen)
                        .put("preview", StringUtils.hasText(result.content()) ? (result.content().length() > 400 ? result.content().substring(0, 400) : result.content()) : ""));

                generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                        .set(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_COMPLETED)
                        .set(GenerationTaskEntity::getFinishedAt, LocalDateTime.now())
                        .set(GenerationTaskEntity::getErrorMessage, null)
                        .set(GenerationTaskEntity::getResultSummary, summary)
                        .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerationTaskEntity::getId, next.getId())
                        .eq(GenerationTaskEntity::getIsDeleted, 0));
                operationLogService.log("TASK", next.getId(), GenerationTaskService.STATUS_COMPLETED, null, null, null);
                log.info("task finished, taskId={}, status=COMPLETED, createdCases={}", next.getId(), createdCases);

                // 如果此刻没有任何 QUEUED 任务了，自动关闭队列推进，避免上一个队列结束后自动处理新任务。
                long queuedLeft = generationTaskMapper.selectCount(new LambdaQueryWrapper<GenerationTaskEntity>()
                        .eq(GenerationTaskEntity::getIsDeleted, 0)
                        .eq(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_QUEUED));
                if (queuedLeft == 0) {
                    manualQueueEnabled.set(false);
                    manualQueueCutoffQueueNo = null;
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "unknown error" : e.getMessage();
                generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                        .set(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_FAILED)
                        .set(GenerationTaskEntity::getFinishedAt, LocalDateTime.now())
                        .set(GenerationTaskEntity::getErrorMessage, msg)
                        .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerationTaskEntity::getId, next.getId())
                        .eq(GenerationTaskEntity::getIsDeleted, 0));
                operationLogService.log("TASK", next.getId(), GenerationTaskService.STATUS_FAILED, null, null, msg);
                log.warn("task failed, taskId={}, err={}", next.getId(), msg);

                // 如果此刻没有任何 QUEUED 任务了，自动关闭队列推进，避免上一个队列结束后自动处理新任务。
                long queuedLeft = generationTaskMapper.selectCount(new LambdaQueryWrapper<GenerationTaskEntity>()
                        .eq(GenerationTaskEntity::getIsDeleted, 0)
                        .eq(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_QUEUED));
                if (queuedLeft == 0) {
                    manualQueueEnabled.set(false);
                    manualQueueCutoffQueueNo = null;
                }
            }
        } catch (Exception e) {
            log.error("generation task runner tick failed", e);
        } finally {
            running.set(false);
        }
    }

    /** 兜底：防止漏调 tryPromoteNextQueued 时队列卡住 */
    @Scheduled(fixedDelay = 1000)
    public void promoteQueuedIfIdle() {
        if (!manualQueueEnabled.get()) {
            return;
        }

        Long runningCount = generationTaskMapper.selectCount(new LambdaQueryWrapper<GenerationTaskEntity>()
                .eq(GenerationTaskEntity::getIsDeleted, 0)
                .eq(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_RUNNING));
        if (runningCount != null && runningCount > 0) {
            return;
        }

        GenerationTaskEntity head = generationTaskMapper.selectOne(
                new LambdaQueryWrapper<GenerationTaskEntity>()
                        .eq(GenerationTaskEntity::getIsDeleted, 0)
                        .eq(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_QUEUED)
                        .orderByAsc(GenerationTaskEntity::getQueueNo)
                        .orderByAsc(GenerationTaskEntity::getId)
                        .last("LIMIT 1"));
        if (head == null) {
            manualQueueEnabled.set(false);
            manualQueueCutoffQueueNo = null;
            return;
        }

        int u = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                .set(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_RUNNING)
                .set(GenerationTaskEntity::getStartedAt, LocalDateTime.now())
                .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(GenerationTaskEntity::getId, head.getId())
                .eq(GenerationTaskEntity::getStatus, GenerationTaskService.STATUS_QUEUED)
                .eq(GenerationTaskEntity::getIsDeleted, 0));

        if (u > 0) {
            operationLogService.log("TASK", head.getId(), "START", null, null, null);
            log.info("promoted queued task to RUNNING, taskId={}, queueNo={}", head.getId(), head.getQueueNo());
        }
    }

    private List<String> extractReferenceAssetRelationCodes(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            JsonNode arr = node == null ? null : node.get("referenceAssetRelationCodes");
            if (arr == null || !arr.isArray()) {
                return null;
            }
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

    private List<Long> extractReferenceVersionIds(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            JsonNode arr = node == null ? null : node.get("referenceVersionIds");
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<Long> out = new ArrayList<>();
            for (JsonNode item : arr) {
                if (item == null || item.isNull()) continue;
                long v = item.asLong(0L);
                if (v > 0L) {
                    out.add(v);
                }
            }
            return out;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private List<Long> resolveContextVersionIds(GenerationTaskEntity task) {
        Set<Long> merged = new LinkedHashSet<>();
        if (task.getVersionId() != null && task.getVersionId() > 0) {
            merged.add(task.getVersionId());
        }
        for (Long refId : extractReferenceVersionIds(task.getPayloadJson())) {
            if (refId != null && refId > 0) {
                merged.add(refId);
            }
        }
        return new ArrayList<>(merged);
    }

    /**
     * 组装给模型的文字上下文：手工需求描述 + 需求文档已提取正文；若存在原型图则文字中提示已附图。
     */
    private String buildRequirementTextForModel(List<Long> versionIds, List<String> assetRelationCodes) {
        StringBuilder sb = new StringBuilder();
        if (versionIds == null || versionIds.isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<RequirementAssetEntity> textWrapper = new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .in(RequirementAssetEntity::getVersionId, versionIds)
                .eq(RequirementAssetEntity::getAssetType, "TEXT");
        if (assetRelationCodes != null && !assetRelationCodes.isEmpty()) {
            textWrapper.in(RequirementAssetEntity::getRelationCode, assetRelationCodes);
        }
        RequirementAssetEntity text = requirementAssetMapper.selectOne(textWrapper
                .orderByDesc(RequirementAssetEntity::getId)
                .last("LIMIT 1"));
        if (text != null && StringUtils.hasText(text.getContent())) {
            sb.append("【需求描述】\n").append(text.getTitle() == null ? "" : ("标题：" + text.getTitle() + "\n")).append(text.getContent()).append("\n\n");
        }

        LambdaQueryWrapper<RequirementAssetEntity> docsWrapper = new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .in(RequirementAssetEntity::getVersionId, versionIds)
                .eq(RequirementAssetEntity::getAssetType, "FILE");
        if (assetRelationCodes != null && !assetRelationCodes.isEmpty()) {
            docsWrapper.in(RequirementAssetEntity::getRelationCode, assetRelationCodes);
        }
        var docs = requirementAssetMapper.selectList(docsWrapper.orderByAsc(RequirementAssetEntity::getId));
        if (!docs.isEmpty()) {
            sb.append("【需求文档（已提取正文）】\n");
            for (RequirementAssetEntity a : docs) {
                if (!StringUtils.hasText(a.getContent())) {
                    continue;
                }
                sb.append("--- ").append(a.getTitle() != null ? a.getTitle() : a.getFileName()).append(" ---\n");
                sb.append(a.getContent()).append("\n\n");
            }
        }

        LambdaQueryWrapper<RequirementAssetEntity> protoCountWrapper = new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .in(RequirementAssetEntity::getVersionId, versionIds)
                .eq(RequirementAssetEntity::getAssetType, "PROTOTYPE");
        if (assetRelationCodes != null && !assetRelationCodes.isEmpty()) {
            protoCountWrapper.in(RequirementAssetEntity::getRelationCode, assetRelationCodes);
        }
        long protoCount = requirementAssetMapper.selectCount(protoCountWrapper);
        if (protoCount > 0) {
            sb.append("【说明】已附原型图 ").append(protoCount).append(" 张，请结合图片与上文需求生成测试用例。\n");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private List<String> loadPrototypeImageDataUrls(List<Long> versionIds, List<String> assetRelationCodes) throws IOException {
        if (versionIds == null || versionIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<RequirementAssetEntity> protoWrapper = new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .in(RequirementAssetEntity::getVersionId, versionIds)
                .eq(RequirementAssetEntity::getAssetType, "PROTOTYPE");
        if (assetRelationCodes != null && !assetRelationCodes.isEmpty()) {
            protoWrapper.in(RequirementAssetEntity::getRelationCode, assetRelationCodes);
        }
        var protos = requirementAssetMapper.selectList(protoWrapper.orderByAsc(RequirementAssetEntity::getId));
        List<String> out = new ArrayList<>();
        for (RequirementAssetEntity a : protos) {
            Path path = requirementAssetPathResolver.resolve(a);
            if (path == null || !Files.exists(path)) {
                log.warn("prototype file missing for model, assetId={}, filePath={}", a.getId(), a.getFilePath());
                continue;
            }
            long size = Files.size(path);
            if (size > MAX_IMAGE_BYTES) {
                log.warn("prototype file too large, skip, assetId={}, size={}", a.getId(), size);
                continue;
            }
            byte[] bytes = Files.readAllBytes(path);
            String mime = StringUtils.hasText(a.getMimeType()) ? a.getMimeType().trim() : Files.probeContentType(path);
            if (!StringUtils.hasText(mime)) {
                mime = "image/png";
            }
            String b64 = Base64.getEncoder().encodeToString(bytes);
            out.add("data:" + mime + ";base64," + b64);
        }
        return out;
    }
}

