package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.common.StatusConstants;
import com.testcase.backend.dto.GenerationTaskDtos;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.entity.GenerationTaskEntity;
import com.testcase.backend.entity.GenerationTaskRefEntity;
import com.testcase.backend.entity.ProjectEntity;
import com.testcase.backend.entity.ProjectVersionEntity;
import com.testcase.backend.entity.RequirementAssetEntity;
import com.testcase.backend.mapper.GenerationTaskMapper;
import com.testcase.backend.mapper.GenerationTaskRefMapper;
import com.testcase.backend.mapper.ProjectMapper;
import com.testcase.backend.mapper.ProjectVersionMapper;
import com.testcase.backend.mapper.RequirementAssetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GenerationTaskService {
    private static final Logger log = LoggerFactory.getLogger(GenerationTaskService.class);

    public static final String STATUS_QUEUED = StatusConstants.GenerationTask.QUEUED;
    /** 新建后不进入自动队列；需要点击「启动」后才进入 QUEUED。 */
    public static final String STATUS_PENDING = StatusConstants.GenerationTask.PENDING;
    public static final String STATUS_RUNNING = StatusConstants.GenerationTask.RUNNING;
    public static final String STATUS_COMPLETED = StatusConstants.GenerationTask.COMPLETED;
    public static final String STATUS_FAILED = StatusConstants.GenerationTask.FAILED;
    public static final String STATUS_CANCELLED = StatusConstants.GenerationTask.CANCELLED;

    private final GenerationTaskMapper generationTaskMapper;
    private final GenerationTaskRefMapper generationTaskRefMapper;
    private final ProjectMapper projectMapper;
    private final ProjectVersionMapper projectVersionMapper;
    private final RequirementAssetMapper requirementAssetMapper;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;

    public GenerationTaskService(
            GenerationTaskMapper generationTaskMapper,
            GenerationTaskRefMapper generationTaskRefMapper,
            ProjectMapper projectMapper,
            ProjectVersionMapper projectVersionMapper,
            RequirementAssetMapper requirementAssetMapper,
            ObjectMapper objectMapper,
            OperationLogService operationLogService
    ) {
        this.generationTaskMapper = generationTaskMapper;
        this.generationTaskRefMapper = generationTaskRefMapper;
        this.projectMapper = projectMapper;
        this.projectVersionMapper = projectVersionMapper;
        this.requirementAssetMapper = requirementAssetMapper;
        this.objectMapper = objectMapper;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public GenerationTaskDtos.TaskItem submit(GenerationTaskDtos.SubmitTaskRequest request, Long submitterId) {
        ensureProjectAndVersion(request.projectId(), request.versionId());
        String taskNo = "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        GenerationTaskEntity entity = new GenerationTaskEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setProjectId(request.projectId());
        entity.setVersionId(request.versionId());
        entity.setTaskNo(taskNo);
        // 新建任务先不进入队列推进，需点击「启动」后才会切到 QUEUED 并分配 queueNo
        entity.setStatus(STATUS_PENDING);
        entity.setQueueNo(null);
        entity.setSubmittedBy(submitterId);
        entity.setSubmittedAt(LocalDateTime.now());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setModelConfigId(request.modelConfigId());
        entity.setPromptTemplateId(request.promptTemplateId());
        entity.setCaseCategory(normalizeCaseCategory(request.caseCategory()));
        entity.setIsDeleted(0);
        entity.setPayloadJson(safeJson(request));
        generationTaskMapper.insert(entity);

        if (request.referenceVersionIds() != null) {
            for (Long refVersionId : request.referenceVersionIds()) {
                if (refVersionId == null) {
                    continue;
                }
                GenerationTaskRefEntity ref = new GenerationTaskRefEntity();
                ref.setTaskId(entity.getId());
                ref.setRefVersionId(refVersionId);
                generationTaskRefMapper.insert(ref);
            }
        }

        operationLogService.log("TASK", entity.getId(), "SUBMIT", null, entity, null);
        log.info("task submitted, taskId={}, taskNo={}, projectId={}, versionId={}", entity.getId(), taskNo, request.projectId(), request.versionId());
        return toItem(generationTaskMapper.selectById(entity.getId()));
    }

    @Transactional
    public GenerationTaskDtos.TaskItem update(Long taskId, GenerationTaskDtos.UpdateTaskRequest request, Long operatorId) {
        GenerationTaskEntity entity = generationTaskMapper.selectById(taskId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }
        String st = entity.getStatus();
        if (STATUS_RUNNING.equals(st)) {
            throw new IllegalArgumentException("cannot update a running task");
        }
        if (STATUS_QUEUED.equals(st)) {
            throw new IllegalArgumentException("cannot update a queued task");
        }
        if (!STATUS_PENDING.equals(st) && !STATUS_FAILED.equals(st)
                && !STATUS_COMPLETED.equals(st) && !STATUS_CANCELLED.equals(st)) {
            throw new IllegalArgumentException("task status does not allow update");
        }
        ensureProjectAndVersion(entity.getProjectId(), entity.getVersionId());

        List<String> refAssetRelationCodes =
                request.referenceAssetRelationCodes() != null
                        ? request.referenceAssetRelationCodes()
                        : extractReferenceAssetRelationCodes(entity.getPayloadJson());

        GenerationTaskDtos.SubmitTaskRequest payloadSource = new GenerationTaskDtos.SubmitTaskRequest(
                entity.getProjectId(),
                entity.getVersionId(),
                request.modelConfigId(),
                request.promptTemplateId(),
                request.referenceVersionIds(),
                refAssetRelationCodes,
                request.strategy(),
                request.caseLimit(),
                request.caseCategory()
        );
        String newPayload = safeJson(payloadSource);

        int updated = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                .set(GenerationTaskEntity::getModelConfigId, request.modelConfigId())
                .set(GenerationTaskEntity::getPromptTemplateId, request.promptTemplateId())
                .set(GenerationTaskEntity::getCaseCategory, normalizeCaseCategory(request.caseCategory()))
                .set(GenerationTaskEntity::getPayloadJson, newPayload)
                .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(GenerationTaskEntity::getId, taskId)
                .eq(GenerationTaskEntity::getIsDeleted, 0));
        if (updated <= 0) {
            throw new IllegalArgumentException("task update failed");
        }

        if (request.referenceVersionIds() != null) {
            generationTaskRefMapper.delete(new LambdaQueryWrapper<GenerationTaskRefEntity>()
                    .eq(GenerationTaskRefEntity::getTaskId, taskId));
            for (Long refVersionId : request.referenceVersionIds()) {
                if (refVersionId == null) {
                    continue;
                }
                GenerationTaskRefEntity ref = new GenerationTaskRefEntity();
                ref.setTaskId(taskId);
                ref.setRefVersionId(refVersionId);
                generationTaskRefMapper.insert(ref);
            }
        }

        GenerationTaskEntity fresh = generationTaskMapper.selectById(taskId);
        operationLogService.log("TASK", taskId, "UPDATE", null, fresh, null);
        log.info("task updated, taskId={}, operatorId={}", taskId, operatorId);
        return toItem(fresh);
    }

    public PagedResult<GenerationTaskDtos.TaskItem> search(Long projectId, Long versionId, String status, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<GenerationTaskEntity>()
                .eq(GenerationTaskEntity::getIsDeleted, 0)
                .orderByAsc(GenerationTaskEntity::getQueueNo)
                .orderByAsc(GenerationTaskEntity::getId);
        if (projectId != null) {
            wrapper.eq(GenerationTaskEntity::getProjectId, projectId);
        }
        if (versionId != null) {
            wrapper.eq(GenerationTaskEntity::getVersionId, versionId);
        }
        if (status != null && !status.isBlank()) {
            String st = status.trim().toUpperCase(Locale.ROOT);
            if ("FAIL".equals(st)) {
                wrapper.eq(GenerationTaskEntity::getStatus, STATUS_FAILED);
            } else {
                wrapper.eq(GenerationTaskEntity::getStatus, st);
            }
        }
        Page<GenerationTaskEntity> page = generationTaskMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        List<GenerationTaskEntity> records = page.getRecords();
        var versionIds = records.stream()
                .map(GenerationTaskEntity::getVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<GenerationTaskDtos.RequirementAssetBrief>> assetsByVersion = loadBriefsByVersionIds(versionIds);
        var items = records.stream().map(e -> {
            List<String> relCodes = extractReferenceAssetRelationCodes(e.getPayloadJson());
            if (relCodes != null && !relCodes.isEmpty()) {
                return toItem(e, fetchBriefsForVersionAndRelationCodes(e.getVersionId(), relCodes));
            }
            return toItem(e, assetsByVersion.getOrDefault(e.getVersionId(), List.of()));
        }).toList();
        return new PagedResult<>(items, safePageNo, safePageSize, page.getTotal());
    }

    public GenerationTaskDtos.TaskDetail detail(Long taskId) {
        GenerationTaskEntity entity = generationTaskMapper.selectById(taskId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }
        List<Long> refs = generationTaskRefMapper.selectList(new LambdaQueryWrapper<GenerationTaskRefEntity>()
                        .eq(GenerationTaskRefEntity::getTaskId, taskId))
                .stream().map(GenerationTaskRefEntity::getRefVersionId).toList();
        return new GenerationTaskDtos.TaskDetail(toItem(entity), refs, entity.getPayloadJson(), entity.getResultSummary());
    }

    @Transactional
    public void cancel(Long taskId, Long operatorId) {
        GenerationTaskEntity entity = generationTaskMapper.selectById(taskId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }
        if (!STATUS_QUEUED.equals(entity.getStatus())) {
            throw new IllegalArgumentException("only QUEUED task can be cancelled");
        }
        int updated = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                .set(GenerationTaskEntity::getStatus, STATUS_CANCELLED)
                .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(GenerationTaskEntity::getId, taskId)
                .eq(GenerationTaskEntity::getStatus, STATUS_QUEUED)
                .eq(GenerationTaskEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("task status changed, retry");
        }
        operationLogService.log("TASK", taskId, "CANCEL", null, null, null);
        log.info("task cancelled, taskId={}, operatorId={}", taskId, operatorId);
    }

    @Transactional
    public void interrupt(Long taskId, String reason, Long operatorId) {
        GenerationTaskEntity entity = generationTaskMapper.selectById(taskId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }

        String msg = (reason == null || reason.isBlank()) ? "用户中断" : ("用户中断: " + reason.trim());
        String st = entity.getStatus();
        int updated;
        if (STATUS_RUNNING.equals(st)) {
            // 运行中：按 FAILED 结束
            updated = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                    .set(GenerationTaskEntity::getStatus, STATUS_FAILED)
                    .set(GenerationTaskEntity::getInterruptBy, operatorId)
                    .set(GenerationTaskEntity::getInterruptReason, reason)
                    .set(GenerationTaskEntity::getErrorMessage, msg)
                    .set(GenerationTaskEntity::getFinishedAt, LocalDateTime.now())
                    .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                    .eq(GenerationTaskEntity::getId, taskId)
                    .eq(GenerationTaskEntity::getStatus, STATUS_RUNNING)
                    .eq(GenerationTaskEntity::getIsDeleted, 0));
            if (updated == 0) {
                throw new IllegalArgumentException("task status changed, retry");
            }
        } else if (STATUS_QUEUED.equals(st)) {
            // 排队中：退出队列，置为 CANCELLED（这样可以再点击“重试/再次生成”）
            updated = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                    .set(GenerationTaskEntity::getStatus, STATUS_CANCELLED)
                    .set(GenerationTaskEntity::getInterruptBy, operatorId)
                    .set(GenerationTaskEntity::getInterruptReason, reason)
                    .set(GenerationTaskEntity::getErrorMessage, null)
                    .set(GenerationTaskEntity::getFinishedAt, null)
                    .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                    .eq(GenerationTaskEntity::getId, taskId)
                    .eq(GenerationTaskEntity::getStatus, STATUS_QUEUED)
                    .eq(GenerationTaskEntity::getIsDeleted, 0));
            if (updated == 0) {
                throw new IllegalArgumentException("task status changed, retry");
            }
        } else {
            throw new IllegalArgumentException("only QUEUED/RUNNING task can be interrupted");
        }

        operationLogService.log("TASK", taskId, "INTERRUPT", null, null, reason);
        log.info("task interrupted, taskId={}, operatorId={}, priorStatus={}", taskId, operatorId, st);
    }

    @Transactional
    public void start(Long taskId, Long operatorId) {
        GenerationTaskEntity entity = generationTaskMapper.selectById(taskId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }
        String st = entity.getStatus();
        if (!STATUS_PENDING.equals(st) && !STATUS_QUEUED.equals(st)) {
            throw new IllegalArgumentException("only PENDING/QUEUED task can be started");
        }

        // PENDING -> QUEUED：点击启动后才分配队列位置并参与排队推进
        if (STATUS_PENDING.equals(st)) {
            long queueNo = allocateQueueNo();
            int updated = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                    .set(GenerationTaskEntity::getStatus, STATUS_QUEUED)
                    .set(GenerationTaskEntity::getQueueNo, queueNo)
                    .set(GenerationTaskEntity::getStartedAt, null)
                    .set(GenerationTaskEntity::getFinishedAt, null)
                    .set(GenerationTaskEntity::getErrorMessage, null)
                    .set(GenerationTaskEntity::getResultSummary, null)
                    .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                    .eq(GenerationTaskEntity::getId, taskId)
                    .eq(GenerationTaskEntity::getStatus, STATUS_PENDING)
                    .eq(GenerationTaskEntity::getIsDeleted, 0));
            if (updated == 0) {
                throw new IllegalArgumentException("task status changed, start");
            }
        }

        // 手动触发队列推进：RUNNER 会持续把 QUEUED 提升为 RUNNING，直到队列为空。
        GenerationTaskRunner.manualQueueEnabled.set(true);
        GenerationTaskRunner.manualQueueCutoffQueueNo = null;
        operationLogService.log("TASK", taskId, "START", null, null, null);
        log.info("manual start requested, taskId={}, operatorId={}", taskId, operatorId);
    }

    @Transactional
    public GenerationTaskDtos.TaskItem retry(Long taskId, Long operatorId) {
        GenerationTaskEntity entity = generationTaskMapper.selectById(taskId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("task not found");
        }
        String st = entity.getStatus();
        if (!(STATUS_FAILED.equals(st) || STATUS_COMPLETED.equals(st) || STATUS_CANCELLED.equals(st))) {
            throw new IllegalArgumentException("only terminal tasks (FAILED/COMPLETED/CANCELLED) can be retried");
        }

        long newQueueNo = allocateQueueNo();
        int updated = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                .set(GenerationTaskEntity::getStatus, STATUS_QUEUED)
                .set(GenerationTaskEntity::getQueueNo, newQueueNo)
                .set(GenerationTaskEntity::getSubmittedBy, operatorId)
                .set(GenerationTaskEntity::getSubmittedAt, LocalDateTime.now())
                .set(GenerationTaskEntity::getStartedAt, null)
                .set(GenerationTaskEntity::getFinishedAt, null)
                .set(GenerationTaskEntity::getInterruptBy, null)
                .set(GenerationTaskEntity::getInterruptReason, null)
                .set(GenerationTaskEntity::getErrorMessage, null)
                .set(GenerationTaskEntity::getResultSummary, null)
                .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(GenerationTaskEntity::getId, taskId)
                .in(GenerationTaskEntity::getStatus, STATUS_FAILED, STATUS_COMPLETED, STATUS_CANCELLED)
                .eq(GenerationTaskEntity::getIsDeleted, 0));
        if (updated == 0) {
            throw new IllegalArgumentException("task status changed, retry");
        }
        operationLogService.log("TASK", taskId, "RETRY", null, null, null);
        log.info("task retried (same task), taskId={}, operatorId={}", taskId, operatorId);

        // 重试后同样开启“队列推进”，RUNNER 会持续推进直到队列为空。
        GenerationTaskRunner.manualQueueEnabled.set(true);
        GenerationTaskRunner.manualQueueCutoffQueueNo = null;

        return toItem(generationTaskMapper.selectById(taskId));
    }

    /**
     * 若当前无 RUNNING，将全局队首 QUEUED（最小 queue_no）置为 RUNNING。全局串行队列的自动衔接入口。
     */
    @Transactional
    public void tryPromoteNextQueued() {
        Long runningCount = generationTaskMapper.selectCount(new LambdaQueryWrapper<GenerationTaskEntity>()
                .eq(GenerationTaskEntity::getIsDeleted, 0)
                .eq(GenerationTaskEntity::getStatus, STATUS_RUNNING));
        if (runningCount != null && runningCount > 0) {
            return;
        }
        GenerationTaskEntity head = generationTaskMapper.selectOne(
                new LambdaQueryWrapper<GenerationTaskEntity>()
                        .eq(GenerationTaskEntity::getIsDeleted, 0)
                        .eq(GenerationTaskEntity::getStatus, STATUS_QUEUED)
                        .orderByAsc(GenerationTaskEntity::getQueueNo)
                        .orderByAsc(GenerationTaskEntity::getId)
                        .last("LIMIT 1"));
        if (head == null) {
            return;
        }
        int u = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                .set(GenerationTaskEntity::getStatus, STATUS_RUNNING)
                .set(GenerationTaskEntity::getStartedAt, LocalDateTime.now())
                .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(GenerationTaskEntity::getId, head.getId())
                .eq(GenerationTaskEntity::getStatus, STATUS_QUEUED)
                .eq(GenerationTaskEntity::getIsDeleted, 0));
        if (u > 0) {
            operationLogService.log("TASK", head.getId(), "START", null, null, null);
            log.info("promoted queued task to RUNNING, taskId={}", head.getId());
        }
    }

    private long allocateQueueNo() {
        GenerationTaskEntity maxRow = generationTaskMapper.selectOne(
                new LambdaQueryWrapper<GenerationTaskEntity>()
                        .select(GenerationTaskEntity::getQueueNo)
                        .eq(GenerationTaskEntity::getIsDeleted, 0)
                        .orderByDesc(GenerationTaskEntity::getQueueNo)
                        .last("LIMIT 1"));
        long max = (maxRow == null || maxRow.getQueueNo() == null) ? 0L : maxRow.getQueueNo();
        return max + 1;
    }

    @Transactional
    public void batchDelete(List<Long> taskIds, Long operatorId) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new IllegalArgumentException("taskIds is required");
        }
        for (Long taskId : taskIds) {
            if (taskId == null) {
                continue;
            }
            GenerationTaskEntity entity = generationTaskMapper.selectById(taskId);
            if (entity == null || entity.getIsDeleted() == 1) {
                continue;
            }
            String st = entity.getStatus();
            if (STATUS_QUEUED.equals(st) || STATUS_RUNNING.equals(st)) {
                // 排队/运行中的任务不允许删除
                throw new IllegalArgumentException("cannot delete a queued/running task");
            }
            int updated = generationTaskMapper.update(null, new LambdaUpdateWrapper<GenerationTaskEntity>()
                    .set(GenerationTaskEntity::getIsDeleted, 1)
                    .set(GenerationTaskEntity::getUpdatedAt, LocalDateTime.now())
                    .eq(GenerationTaskEntity::getId, taskId)
                    .eq(GenerationTaskEntity::getIsDeleted, 0));
            if (updated > 0) {
                operationLogService.log("TASK", taskId, "DELETE", null, null, "batch-delete by " + operatorId);
            }
        }
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

    private GenerationTaskDtos.TaskItem toItem(GenerationTaskEntity e) {
        List<String> relCodes = extractReferenceAssetRelationCodes(e.getPayloadJson());
        if (relCodes != null && !relCodes.isEmpty()) {
            return toItem(e, fetchBriefsForVersionAndRelationCodes(e.getVersionId(), relCodes));
        }
        return toItem(e, fetchBriefsForVersion(e.getVersionId()));
    }

    private GenerationTaskDtos.TaskItem toItem(GenerationTaskEntity e, List<GenerationTaskDtos.RequirementAssetBrief> requirementAssets) {
        return new GenerationTaskDtos.TaskItem(
                e.getId(),
                e.getProjectId(),
                e.getVersionId(),
                e.getTaskNo(),
                e.getStatus(),
                GenerationTaskRunner.manualQueueEnabled.get(),
                e.getQueueNo(),
                e.getSubmittedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getSubmittedAt(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getInterruptBy(),
                e.getInterruptReason(),
                e.getErrorMessage(),
                e.getModelConfigId(),
                e.getPromptTemplateId(),
                e.getCaseCategory() != null ? e.getCaseCategory() : "FUNCTIONAL",
                requirementAssets != null ? requirementAssets : List.of()
        );
    }

    private List<GenerationTaskDtos.RequirementAssetBrief> fetchBriefsForVersion(Long versionId) {
        if (versionId == null) {
            return List.of();
        }
        List<RequirementAssetEntity> list = requirementAssetMapper.selectList(new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .eq(RequirementAssetEntity::getVersionId, versionId)
                .orderByAsc(RequirementAssetEntity::getId));
        return list.stream().map(this::toBrief).toList();
    }

    private List<GenerationTaskDtos.RequirementAssetBrief> fetchBriefsForVersionAndRelationCodes(Long versionId, List<String> relationCodes) {
        if (versionId == null) return List.of();
        if (relationCodes == null || relationCodes.isEmpty()) {
            return fetchBriefsForVersion(versionId);
        }
        List<RequirementAssetEntity> list = requirementAssetMapper.selectList(new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .eq(RequirementAssetEntity::getVersionId, versionId)
                .in(RequirementAssetEntity::getRelationCode, relationCodes)
                .orderByAsc(RequirementAssetEntity::getId));
        return list.stream().map(this::toBrief).toList();
    }

    private Map<Long, List<GenerationTaskDtos.RequirementAssetBrief>> loadBriefsByVersionIds(Collection<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Map.of();
        }
        List<RequirementAssetEntity> all = requirementAssetMapper.selectList(new LambdaQueryWrapper<RequirementAssetEntity>()
                .eq(RequirementAssetEntity::getIsDeleted, 0)
                .in(RequirementAssetEntity::getVersionId, versionIds)
                .orderByAsc(RequirementAssetEntity::getVersionId)
                .orderByAsc(RequirementAssetEntity::getId));
        return all.stream().collect(Collectors.groupingBy(
                RequirementAssetEntity::getVersionId,
                LinkedHashMap::new,
                Collectors.mapping(this::toBrief, Collectors.toList())));
    }

    private GenerationTaskDtos.RequirementAssetBrief toBrief(RequirementAssetEntity a) {
        String title = a.getTitle();
        if (title == null || title.isBlank()) {
            title = a.getFileName();
        }
        return new GenerationTaskDtos.RequirementAssetBrief(
                a.getAssetCode(),
                title,
                a.getAssetType());
    }

    private List<String> extractReferenceAssetRelationCodes(String payloadJson) {
        if (!Objects.nonNull(payloadJson) || payloadJson.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(payloadJson);
            if (node == null) {
                return null;
            }
            var arr = node.get("referenceAssetRelationCodes");
            if (arr == null || !arr.isArray()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (var item : arr) {
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

    private static String normalizeCaseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "FUNCTIONAL";
        }
        String u = raw.trim().toUpperCase();
        return "API".equals(u) ? "API" : "FUNCTIONAL";
    }

    private String safeJson(Object any) {
        try {
            return objectMapper.writeValueAsString(any);
        } catch (Exception e) {
            return null;
        }
    }
}

