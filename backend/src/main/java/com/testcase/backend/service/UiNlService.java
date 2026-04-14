package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.UiNlDtos;
import com.testcase.backend.entity.UiNlCaseEntity;
import com.testcase.backend.entity.ModelConfigEntity;
import com.testcase.backend.entity.PromptTemplateEntity;
import com.testcase.backend.entity.UiNlReportEntity;
import com.testcase.backend.entity.UiNlTaskEntity;
import com.testcase.backend.entity.UiNlTaskStepEntity;
import com.testcase.backend.mapper.ModelConfigMapper;
import com.testcase.backend.mapper.PromptTemplateMapper;
import com.testcase.backend.mapper.UiNlCaseMapper;
import com.testcase.backend.mapper.UiNlReportMapper;
import com.testcase.backend.mapper.UiNlTaskMapper;
import com.testcase.backend.mapper.UiNlTaskStepMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UiNlService {
    private static final Logger log = LoggerFactory.getLogger(UiNlService.class);

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PLANNING = "PLANNING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_INTERRUPTED = "INTERRUPTED";

    private final UiNlCaseMapper caseMapper;
    private final UiNlTaskMapper taskMapper;
    private final UiNlTaskStepMapper stepMapper;
    private final UiNlReportMapper reportMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final UiRunnerClient uiRunnerClient;
    private final OperationLogService operationLogService;

    public UiNlService(
            UiNlCaseMapper caseMapper,
            UiNlTaskMapper taskMapper,
            UiNlTaskStepMapper stepMapper,
            UiNlReportMapper reportMapper,
            ModelConfigMapper modelConfigMapper,
            PromptTemplateMapper promptTemplateMapper,
            ModelClient modelClient,
            ObjectMapper objectMapper,
            UiRunnerClient uiRunnerClient,
            OperationLogService operationLogService
    ) {
        this.caseMapper = caseMapper;
        this.taskMapper = taskMapper;
        this.stepMapper = stepMapper;
        this.reportMapper = reportMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.uiRunnerClient = uiRunnerClient;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public UiNlDtos.CaseItem createCase(UiNlDtos.CreateCaseRequest body, Long userId) {
        if (body.projectId() == null || body.versionId() == null) {
            throw new IllegalArgumentException("projectId/versionId is required");
        }
        if (!StringUtils.hasText(body.title()) || !StringUtils.hasText(body.nlText())) {
            throw new IllegalArgumentException("title/nlText is required");
        }
        LocalDateTime now = LocalDateTime.now();
        UiNlCaseEntity e = new UiNlCaseEntity();
        e.setCaseNo("NLC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        e.setProjectId(body.projectId());
        e.setVersionId(body.versionId());
        e.setTitle(body.title().trim());
        e.setNlText(body.nlText().trim());
        e.setPrecondition(trimToNull(body.precondition()));
        e.setTargetEnv(trimToNull(body.targetEnv()));
        e.setBaseUrl(trimToNull(body.baseUrl()));
        e.setCredentialRef(trimToNull(body.credentialRef()));
        e.setStatus(StringUtils.hasText(body.status()) ? body.status().trim().toUpperCase(Locale.ROOT) : "ENABLED");
        e.setTagsJson(trimToNull(body.tagsJson()));
        e.setCreatedBy(userId);
        e.setUpdatedBy(userId);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setIsDeleted(0);
        caseMapper.insert(e);
        operationLogService.log("UI_NL_CASE", e.getId(), "CREATE", null, e, null);
        return toCaseItem(e);
    }

    public PagedResult<UiNlDtos.CaseItem> listCases(Long projectId, Long versionId, String keyword, String status, int pageNo, int pageSize) {
        var wrapper = new LambdaQueryWrapper<UiNlCaseEntity>()
                .eq(UiNlCaseEntity::getIsDeleted, 0)
                .orderByDesc(UiNlCaseEntity::getId);
        if (projectId != null && projectId > 0) wrapper.eq(UiNlCaseEntity::getProjectId, projectId);
        if (versionId != null && versionId > 0) wrapper.eq(UiNlCaseEntity::getVersionId, versionId);
        if (StringUtils.hasText(status)) wrapper.eq(UiNlCaseEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(UiNlCaseEntity::getTitle, kw).or().like(UiNlCaseEntity::getNlText, kw));
        }
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.max(1, pageSize);
        Page<UiNlCaseEntity> page = caseMapper.selectPage(new Page<>(safeNo, safeSize), wrapper);
        List<UiNlDtos.CaseItem> records = page.getRecords().stream().map(this::toCaseItem).toList();
        return new PagedResult<>(records, safeNo, safeSize, page.getTotal());
    }

    public UiNlDtos.CaseItem detailCase(Long id) {
        UiNlCaseEntity e = assertCaseExists(id);
        return toCaseItem(e);
    }

    @Transactional
    public UiNlDtos.CaseItem updateCase(Long id, UiNlDtos.UpdateCaseRequest body, Long userId) {
        UiNlCaseEntity e = assertCaseExists(id);
        e.setTitle(body.title().trim());
        e.setNlText(body.nlText().trim());
        e.setPrecondition(trimToNull(body.precondition()));
        e.setTargetEnv(trimToNull(body.targetEnv()));
        e.setBaseUrl(trimToNull(body.baseUrl()));
        e.setCredentialRef(trimToNull(body.credentialRef()));
        e.setStatus(StringUtils.hasText(body.status()) ? body.status().trim().toUpperCase(Locale.ROOT) : e.getStatus());
        e.setTagsJson(trimToNull(body.tagsJson()));
        e.setUpdatedBy(userId);
        e.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(e);
        operationLogService.log("UI_NL_CASE", e.getId(), "UPDATE", null, e, null);
        return toCaseItem(e);
    }

    @Transactional
    public void deleteCase(Long id, Long userId) {
        UiNlCaseEntity e = assertCaseExists(id);
        e.setIsDeleted(1);
        e.setUpdatedBy(userId);
        e.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(e);
        operationLogService.log("UI_NL_CASE", e.getId(), "DELETE", null, null, null);
    }

    @Transactional
    public UiNlDtos.TaskItem createTask(UiNlDtos.CreateTaskRequest body, Long userId) {
        UiNlCaseEntity c = assertCaseExists(body.uiNlCaseId());
        if (!c.getProjectId().equals(body.projectId()) || !c.getVersionId().equals(body.versionId())) {
            throw new IllegalArgumentException("uiNlCase does not belong to selected project/version");
        }
        ModelConfigEntity model = assertModelConfigEnabled(body.modelConfigId());
        PromptTemplateEntity prompt = assertPromptTemplateEnabled(body.promptTemplateId());
        LocalDateTime now = LocalDateTime.now();
        UiNlTaskEntity t = new UiNlTaskEntity();
        t.setProjectId(body.projectId());
        t.setVersionId(body.versionId());
        t.setUiNlCaseId(body.uiNlCaseId());
        t.setTaskNo("UIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        t.setStatus(STATUS_PENDING);
        t.setSubmittedBy(userId);
        t.setSubmittedAt(now);
        t.setModelConfigId(model.getId());
        t.setPromptTemplateId(prompt.getId());
        t.setHeadless(Boolean.TRUE.equals(body.headless()) ? 1 : 0);
        t.setBrowserName(StringUtils.hasText(body.browserName()) ? body.browserName().trim() : "chromium");
        t.setModelKey(StringUtils.hasText(body.modelKey()) ? body.modelKey().trim() : trimToNull(model.getModelKey()));
        t.setTimeoutSeconds(body.timeoutSeconds() != null && body.timeoutSeconds() > 0 ? body.timeoutSeconds() : 600);
        t.setPayloadJson(trimToNull(body.payloadJson()));
        t.setCreatedBy(userId);
        t.setUpdatedBy(userId);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t.setIsDeleted(0);
        taskMapper.insert(t);
        operationLogService.log("UI_NL_TASK", t.getId(), "CREATE", null, t, null);
        return toTaskItem(t);
    }

    public PagedResult<UiNlDtos.TaskItem> listTasks(Long projectId, Long versionId, String status, String caseTitle, int pageNo, int pageSize) {
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<UiNlTaskEntity>()
                .eq(UiNlTaskEntity::getIsDeleted, 0)
                .orderByDesc(UiNlTaskEntity::getId);
        if (projectId != null && projectId > 0) wrapper.eq(UiNlTaskEntity::getProjectId, projectId);
        if (versionId != null && versionId > 0) wrapper.eq(UiNlTaskEntity::getVersionId, versionId);
        if (StringUtils.hasText(status)) wrapper.eq(UiNlTaskEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        if (StringUtils.hasText(caseTitle)) {
            String kw = caseTitle.trim();
            List<Long> caseIds = caseMapper.selectList(new LambdaQueryWrapper<UiNlCaseEntity>()
                            .eq(UiNlCaseEntity::getIsDeleted, 0)
                            .like(UiNlCaseEntity::getTitle, kw))
                    .stream()
                    .map(UiNlCaseEntity::getId)
                    .toList();
            if (caseIds.isEmpty()) {
                return new PagedResult<>(List.of(), safeNo, safeSize, 0);
            }
            wrapper.in(UiNlTaskEntity::getUiNlCaseId, caseIds);
        }
        Page<UiNlTaskEntity> page = taskMapper.selectPage(new Page<>(safeNo, safeSize), wrapper);
        List<UiNlDtos.TaskItem> records = page.getRecords().stream().map(this::toTaskItem).toList();
        return new PagedResult<>(records, safeNo, safeSize, page.getTotal());
    }

    public UiNlDtos.TaskItem detailTask(Long id) {
        return toTaskItem(assertTaskExists(id));
    }

    @Transactional
    public UiNlDtos.TaskItem startTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (!List.of(STATUS_PENDING, STATUS_CANCELLED, STATUS_FAILED).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow start: " + t.getStatus());
        }
        t.setStatus(STATUS_QUEUED);
        t.setErrorMessage(null);
        t.setInterruptReason(null);
        t.setUpdatedBy(userId);
        t.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        operationLogService.log("UI_NL_TASK", t.getId(), "START", null, null, null);
        return toTaskItem(t);
    }

    @Transactional
    public UiNlDtos.TaskItem executeTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (List.of(STATUS_RUNNING, STATUS_COMPLETED).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow generate steps: " + t.getStatus());
        }
        if (!List.of(STATUS_PENDING, STATUS_FAILED, STATUS_CANCELLED, STATUS_INTERRUPTED, STATUS_READY).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow generate steps: " + t.getStatus());
        }

        // 入队：只把任务推进到 QUEUED，真正生成由后台 planner 执行（可中断）
        LocalDateTime now = LocalDateTime.now();
        // 物理删除：避免 uk(task_id, step_no) 与逻辑删除冲突
        stepMapper.delete(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId));

        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_QUEUED)
                .set(UiNlTaskEntity::getErrorMessage, null)
                .set(UiNlTaskEntity::getInterruptReason, null)
                .set(UiNlTaskEntity::getInterruptBy, null)
                .set(UiNlTaskEntity::getFinishedAt, null)
                .set(UiNlTaskEntity::getUpdatedBy, userId)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, taskId)
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        operationLogService.log("UI_NL_TASK", taskId, "ENQUEUE_PLAN", null, null, null);
        return toTaskItem(assertTaskExists(taskId));
    }

    @Transactional
    public void planNextQueuedTask() {
        UiNlTaskEntity head = taskMapper.selectOne(new LambdaQueryWrapper<UiNlTaskEntity>()
                .eq(UiNlTaskEntity::getIsDeleted, 0)
                .eq(UiNlTaskEntity::getStatus, STATUS_QUEUED)
                .orderByAsc(UiNlTaskEntity::getUpdatedAt)
                .orderByAsc(UiNlTaskEntity::getId)
                .last("LIMIT 1"));
        if (head == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int u = taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_PLANNING)
                .set(UiNlTaskEntity::getStartedAt, now)
                .set(UiNlTaskEntity::getErrorMessage, null)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, head.getId())
                .eq(UiNlTaskEntity::getStatus, STATUS_QUEUED)
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        if (u <= 0) {
            return;
        }

        UiNlTaskEntity task = assertTaskExists(head.getId());
        try {
            UiNlCaseEntity c = assertCaseExists(task.getUiNlCaseId());
            ModelConfigEntity model = assertModelConfigEnabled(task.getModelConfigId());
            PromptTemplateEntity prompt = assertPromptTemplateEnabled(task.getPromptTemplateId());

            // 生成步骤（可能耗时）。生成完毕后再次确认未被中断。
            List<UiRunnerClient.StepResult> planned = generateStepsByLlm(model, prompt, c);
            UiNlTaskEntity latest = assertTaskExists(task.getId());
            if (STATUS_INTERRUPTED.equals(latest.getStatus())) {
                return;
            }
            if (planned == null || planned.isEmpty()) {
                throw new IllegalStateException("step generation produced empty result");
            }
            replaceSteps(task.getId(), planned, now);

            taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                    .set(UiNlTaskEntity::getStatus, STATUS_READY)
                    .set(UiNlTaskEntity::getUpdatedAt, LocalDateTime.now())
                    .eq(UiNlTaskEntity::getId, task.getId())
                    .eq(UiNlTaskEntity::getIsDeleted, 0));
            operationLogService.log("UI_NL_TASK", task.getId(), STATUS_READY, null, null, "steps planned");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "step generation failed" : e.getMessage();
            taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                    .set(UiNlTaskEntity::getStatus, STATUS_FAILED)
                    .set(UiNlTaskEntity::getFinishedAt, LocalDateTime.now())
                    .set(UiNlTaskEntity::getErrorMessage, msg)
                    .set(UiNlTaskEntity::getUpdatedAt, LocalDateTime.now())
                    .eq(UiNlTaskEntity::getId, task.getId())
                    .eq(UiNlTaskEntity::getIsDeleted, 0));
            operationLogService.log("UI_NL_TASK", task.getId(), STATUS_FAILED, null, null, msg);
        }
    }

    @Transactional
    public UiNlDtos.TaskItem interruptTask(Long taskId, String reason, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (!List.of(STATUS_QUEUED, STATUS_PLANNING).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow interrupt: " + t.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_INTERRUPTED)
                .set(UiNlTaskEntity::getInterruptBy, userId)
                .set(UiNlTaskEntity::getInterruptReason, StringUtils.hasText(reason) ? reason.trim() : "manual interrupt")
                .set(UiNlTaskEntity::getFinishedAt, now)
                .set(UiNlTaskEntity::getUpdatedBy, userId)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, taskId)
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        operationLogService.log("UI_NL_TASK", taskId, "INTERRUPT", null, null, null);
        return toTaskItem(assertTaskExists(taskId));
    }

    @Transactional
    public UiNlDtos.TaskItem updateTask(Long taskId, UiNlDtos.UpdateTaskRequest body, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (List.of(STATUS_RUNNING, STATUS_PLANNING, STATUS_QUEUED).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow update: " + t.getStatus());
        }
        UiNlCaseEntity c = assertCaseExists(body.uiNlCaseId());
        if (!c.getProjectId().equals(t.getProjectId()) || !c.getVersionId().equals(t.getVersionId())) {
            throw new IllegalArgumentException("uiNlCase does not belong to selected project/version");
        }
        ModelConfigEntity model = assertModelConfigEnabled(body.modelConfigId());
        PromptTemplateEntity prompt = assertPromptTemplateEnabled(body.promptTemplateId());

        LocalDateTime now = LocalDateTime.now();
        t.setUiNlCaseId(body.uiNlCaseId());
        t.setModelConfigId(model.getId());
        t.setPromptTemplateId(prompt.getId());
        t.setHeadless(Boolean.TRUE.equals(body.headless()) ? 1 : 0);
        t.setBrowserName(StringUtils.hasText(body.browserName()) ? body.browserName().trim() : "chromium");
        t.setModelKey(StringUtils.hasText(body.modelKey()) ? body.modelKey().trim() : model.getModelKey());
        t.setTimeoutSeconds(body.timeoutSeconds() != null && body.timeoutSeconds() > 0 ? body.timeoutSeconds() : 600);
        t.setPayloadJson(trimToNull(body.payloadJson()));
        t.setUpdatedBy(userId);
        t.setUpdatedAt(now);
        taskMapper.updateById(t);
        operationLogService.log("UI_NL_TASK", t.getId(), "UPDATE", null, t, null);
        return toTaskItem(t);
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (List.of(STATUS_RUNNING, STATUS_PLANNING).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow delete: " + t.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getIsDeleted, 1)
                .set(UiNlTaskEntity::getUpdatedBy, userId)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, taskId)
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        stepMapper.delete(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId));
        operationLogService.log("UI_NL_TASK", taskId, "DELETE", null, null, null);
    }

    @Transactional
    public UiNlDtos.TaskItem runTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (STATUS_RUNNING.equals(t.getStatus())) {
            throw new IllegalArgumentException("task is running");
        }
        if (STATUS_COMPLETED.equals(t.getStatus())) {
            throw new IllegalArgumentException("task already completed");
        }
        if (!List.of(STATUS_READY, STATUS_FAILED, STATUS_CANCELLED).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow run: " + t.getStatus());
        }
        // 允许 READY/FAILED/CANCELLED/PENDING 直接执行；若还没生成步骤，提示用户先生成
        long stepCount = stepMapper.selectCount(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId)
                .eq(UiNlTaskStepEntity::getIsDeleted, 0));
        if (stepCount <= 0) {
            throw new IllegalArgumentException("no steps found, please generate steps first");
        }

        // 执行前先探测 runner 健康状态，避免进入 RUNNING 后才报连接失败
        uiRunnerClient.ensureHealthy();

        UiNlCaseEntity c = assertCaseExists(t.getUiNlCaseId());
        ModelConfigEntity model = assertModelConfigEnabled(t.getModelConfigId());
        PromptTemplateEntity prompt = assertPromptTemplateEnabled(t.getPromptTemplateId());

        LocalDateTime now = LocalDateTime.now();
        t.setStatus(STATUS_RUNNING);
        t.setStartedAt(now);
        t.setFinishedAt(null);
        t.setErrorMessage(null);
        t.setInterruptReason(null);
        t.setUpdatedBy(userId);
        t.setUpdatedAt(now);
        if (!StringUtils.hasText(t.getRunnerRunId())) {
            t.setRunnerRunId("RUN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT));
        }
        taskMapper.updateById(t);

        UiRunnerClient.RunResponse response = uiRunnerClient.run(
                t.getRunnerRunId(),
                composeRunnerTaskText(prompt, c),
                c.getBaseUrl(),
                t.getHeadless() != null && t.getHeadless() == 1,
                StringUtils.hasText(t.getModelKey()) ? t.getModelKey() : model.getModelKey(),
                t.getTimeoutSeconds()
        );
        if (!response.accepted()) {
            markTaskFailed(t.getId(), userId, StringUtils.hasText(response.message()) ? response.message() : "runner rejected");
        }
        operationLogService.log("UI_NL_TASK", t.getId(), "RUN", null, null, null);
        return toTaskItem(assertTaskExists(t.getId()));
    }

    private List<UiRunnerClient.StepResult> generateStepsByLlm(
            ModelConfigEntity model,
            PromptTemplateEntity prompt,
            UiNlCaseEntity c
    ) {
        try {
            String promptText = StringUtils.hasText(prompt.getContent()) ? prompt.getContent().trim() : "";
            String requirementText = buildPlannerRequirementText(c);
            ModelClient.ModelChatInput in = ModelClient.ModelChatInput.textOnly(promptText, requirementText);
            ModelClient.ModelCallResult out = modelClient.chatCompletion(model, in);
            String raw = out == null ? "" : nullSafe(out.content());
            List<UiRunnerClient.StepResult> steps = parsePlannedStepsFromJson(raw);
            if (steps.isEmpty()) {
                return fallbackPlanByLines(c);
            }
            return steps;
        } catch (Exception e) {
            log.warn("llm step generation failed, fallback to line split: {}", e.getMessage());
            return fallbackPlanByLines(c);
        }
    }

    private String buildPlannerRequirementText(UiNlCaseEntity c) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(c.getTitle())) {
            sb.append("测试名称：").append(c.getTitle().trim()).append("\n");
        }
        if (StringUtils.hasText(c.getBaseUrl())) {
            sb.append("baseUrl：").append(c.getBaseUrl().trim()).append("\n");
        }
        if (StringUtils.hasText(c.getTargetEnv())) {
            sb.append("目标环境：").append(c.getTargetEnv().trim()).append("\n");
        }
        if (StringUtils.hasText(c.getPrecondition())) {
            sb.append("前置条件：").append(c.getPrecondition().trim()).append("\n");
        }
        sb.append("\n用户自然语言需求：\n").append(c.getNlText().trim());
        return sb.toString();
    }

    private List<UiRunnerClient.StepResult> parsePlannedStepsFromJson(String raw) throws Exception {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        JsonNode node = objectMapper.readTree(raw.trim());
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<UiRunnerClient.StepResult> out = new ArrayList<>();
        int seq = 1;
        for (JsonNode it : node) {
            if (it == null || it.isNull() || !it.isObject()) continue;
            String desc = text(it, "description");
            String exp = text(it, "expected_result");
            if (!StringUtils.hasText(exp)) exp = text(it, "expectedResult");
            if (!StringUtils.hasText(desc)) continue;
            String expectJson = StringUtils.hasText(exp)
                    ? objectMapper.writeValueAsString(objectMapper.createObjectNode().put("expected_result", exp.trim()))
                    : null;
            int stepNo = seq++;
            out.add(new UiRunnerClient.StepResult(stepNo, "步骤" + stepNo, "PLAN", "PENDING", null, null, null, null, desc.trim(), expectJson, null));
        }
        return out;
    }

    private String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText(null);
    }

    private List<UiRunnerClient.StepResult> fallbackPlanByLines(UiNlCaseEntity c) {
        List<UiRunnerClient.StepResult> generated = new ArrayList<>();
        String[] lines = c.getNlText().split("\\r?\\n");
        int stepNo = 1;
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (t.isEmpty()) continue;
            generated.add(new UiRunnerClient.StepResult(stepNo, "步骤" + stepNo, "PLAN", "PENDING", null, null, null, null, t, null, null));
            stepNo++;
        }
        if (generated.isEmpty()) {
            generated.add(new UiRunnerClient.StepResult(1, "步骤1", "PLAN", "PENDING", null, null, null, null, c.getNlText(), null, null));
        }
        return generated;
    }

    @Transactional
    public void cancelTask(Long taskId, String reason, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (STATUS_RUNNING.equals(t.getStatus()) && StringUtils.hasText(t.getRunnerRunId())) {
            uiRunnerClient.cancel(t.getRunnerRunId());
        }
        t.setStatus(STATUS_CANCELLED);
        t.setInterruptBy(userId);
        t.setInterruptReason(StringUtils.hasText(reason) ? reason.trim() : "manual cancel");
        t.setFinishedAt(LocalDateTime.now());
        t.setUpdatedBy(userId);
        t.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        operationLogService.log("UI_NL_TASK", t.getId(), "CANCEL", null, null, t.getInterruptReason());
    }

    @Transactional
    public UiNlDtos.TaskItem retryTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (STATUS_RUNNING.equals(t.getStatus())) {
            throw new IllegalArgumentException("running task cannot retry");
        }
        t.setStatus(STATUS_PENDING);
        t.setRunnerRunId(null);
        t.setErrorMessage(null);
        t.setResultSummary(null);
        t.setInterruptReason(null);
        t.setFinishedAt(null);
        t.setStartedAt(null);
        t.setUpdatedBy(userId);
        t.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        // 物理删除：避免 uk(task_id, step_no) 与逻辑删除冲突
        stepMapper.delete(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId));
        operationLogService.log("UI_NL_TASK", t.getId(), "RETRY", null, null, null);
        return toTaskItem(t);
    }

    public List<UiNlDtos.StepItem> listTaskSteps(Long taskId) {
        assertTaskExists(taskId);
        return stepMapper.selectList(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                        .eq(UiNlTaskStepEntity::getTaskId, taskId)
                        .eq(UiNlTaskStepEntity::getIsDeleted, 0)
                        .orderByAsc(UiNlTaskStepEntity::getStepNo))
                .stream()
                .map(this::toStepItem)
                .toList();
    }

    public UiNlDtos.StepItem detailStep(Long stepId) {
        UiNlTaskStepEntity s = stepMapper.selectOne(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getId, stepId)
                .eq(UiNlTaskStepEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (s == null) throw new IllegalArgumentException("step not found");
        return toStepItem(s);
    }

    public PagedResult<UiNlDtos.ReportItem> listReports(Long projectId, Long versionId, String status, int pageNo, int pageSize) {
        var wrapper = new LambdaQueryWrapper<UiNlReportEntity>()
                .eq(UiNlReportEntity::getIsDeleted, 0)
                .orderByDesc(UiNlReportEntity::getId);
        if (projectId != null && projectId > 0) wrapper.eq(UiNlReportEntity::getProjectId, projectId);
        if (versionId != null && versionId > 0) wrapper.eq(UiNlReportEntity::getVersionId, versionId);
        if (StringUtils.hasText(status)) wrapper.eq(UiNlReportEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.max(1, pageSize);
        Page<UiNlReportEntity> page = reportMapper.selectPage(new Page<>(safeNo, safeSize), wrapper);
        List<UiNlDtos.ReportItem> records = page.getRecords().stream().map(this::toReportItem).toList();
        return new PagedResult<>(records, safeNo, safeSize, page.getTotal());
    }

    public UiNlDtos.ReportItem detailReport(Long reportId) {
        UiNlReportEntity r = reportMapper.selectOne(new LambdaQueryWrapper<UiNlReportEntity>()
                .eq(UiNlReportEntity::getId, reportId)
                .eq(UiNlReportEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (r == null) throw new IllegalArgumentException("report not found");
        return toReportItem(r);
    }

    @Transactional
    public void pollRunnerAndUpdateTask(UiNlTaskEntity task) {
        if (!STATUS_RUNNING.equals(task.getStatus()) || !StringUtils.hasText(task.getRunnerRunId())) {
            return;
        }
        UiRunnerClient.StatusResponse status = uiRunnerClient.status(task.getRunnerRunId());
        if (!StringUtils.hasText(status.status())) return;
        String st = status.status().trim().toUpperCase(Locale.ROOT);
        if (List.of("RUNNING", "QUEUED", "PENDING").contains(st)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<UiRunnerClient.StepResult> stepResults = status.steps() == null ? List.of() : status.steps();
        replaceSteps(task.getId(), stepResults, now);

        if ("COMPLETED".equals(st) || "SUCCESS".equals(st)) {
            int total = stepResults.size();
            int passed = (int) stepResults.stream().filter(s -> "SUCCESS".equalsIgnoreCase(nullSafe(s.status()))).count();
            int failed = Math.max(0, total - passed);
            createOrUpdateReport(task, "SUCCESS", total, passed, failed, status.summary(), status.artifactsJson(), now);
            taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                    .set(UiNlTaskEntity::getStatus, STATUS_COMPLETED)
                    .set(UiNlTaskEntity::getFinishedAt, now)
                    .set(UiNlTaskEntity::getErrorMessage, null)
                    .set(UiNlTaskEntity::getResultSummary, trimToNull(status.summary()))
                    .set(UiNlTaskEntity::getUpdatedAt, now)
                    .eq(UiNlTaskEntity::getId, task.getId())
                    .eq(UiNlTaskEntity::getIsDeleted, 0));
            operationLogService.log("UI_NL_TASK", task.getId(), STATUS_COMPLETED, null, null, null);
            return;
        }

        String error = StringUtils.hasText(status.errorMessage()) ? status.errorMessage() : "runner execution failed";
        createOrUpdateReport(task, "FAILED", stepResults.size(), 0, stepResults.size(), error, status.artifactsJson(), now);
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_FAILED)
                .set(UiNlTaskEntity::getFinishedAt, now)
                .set(UiNlTaskEntity::getErrorMessage, error)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, task.getId())
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        operationLogService.log("UI_NL_TASK", task.getId(), STATUS_FAILED, null, null, error);
    }

    public List<UiNlTaskEntity> listRunningTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<UiNlTaskEntity>()
                .eq(UiNlTaskEntity::getIsDeleted, 0)
                .eq(UiNlTaskEntity::getStatus, STATUS_RUNNING)
                .orderByAsc(UiNlTaskEntity::getStartedAt));
    }

    @Transactional
    public void planQueuedTasks() {
        // 状态机调整：不再后台自动从 QUEUED 推进到 READY（避免覆盖人工「生成步骤」结果）。
        // 该方法保留为空实现，兼容旧代码调用。
    }

    private void createOrUpdateReport(
            UiNlTaskEntity task,
            String status,
            int total,
            int passed,
            int failed,
            String summary,
            String artifactsJson,
            LocalDateTime finishedAt
    ) {
        UiNlReportEntity r = reportMapper.selectOne(new LambdaQueryWrapper<UiNlReportEntity>()
                .eq(UiNlReportEntity::getTaskId, task.getId())
                .eq(UiNlReportEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (r == null) {
            r = new UiNlReportEntity();
            r.setReportNo("REP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
            r.setTaskId(task.getId());
            r.setProjectId(task.getProjectId());
            r.setVersionId(task.getVersionId());
            r.setCreatedBy(task.getSubmittedBy());
            r.setStartedAt(task.getStartedAt());
            r.setIsDeleted(0);
            r.setCreatedAt(LocalDateTime.now());
        }
        r.setStatus(status);
        r.setTotalSteps(total);
        r.setPassedSteps(passed);
        r.setFailedSteps(failed);
        r.setSummary(trimToNull(summary));
        r.setReportJson(trimToNull(summary));
        r.setArtifactsJson(trimToNull(artifactsJson));
        r.setFinishedAt(finishedAt);
        r.setUpdatedAt(LocalDateTime.now());
        if (r.getId() == null) {
            reportMapper.insert(r);
        } else {
            reportMapper.updateById(r);
        }
    }

    private void replaceSteps(Long taskId, List<UiRunnerClient.StepResult> steps, LocalDateTime now) {
        // 物理删除：避免 uk(task_id, step_no) 与逻辑删除冲突
        stepMapper.delete(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId));
        List<UiRunnerClient.StepResult> sorted = steps.stream()
                .sorted(Comparator.comparing(s -> s.stepNo() == null ? Integer.MAX_VALUE : s.stepNo()))
                .toList();
        int seq = 1;
        for (UiRunnerClient.StepResult s : sorted) {
            UiNlTaskStepEntity e = new UiNlTaskStepEntity();
            e.setTaskId(taskId);
            // 不信任外部 stepNo，避免重复 stepNo 触发 uk(task_id, step_no)
            e.setStepNo(seq++);
            e.setStepTitle(trimToNull(s.title()));
            e.setActionType(StringUtils.hasText(s.actionType()) ? s.actionType().trim() : "PLAN");
            e.setTargetJson(trimToNull(s.targetJson()));
            e.setInputValue(trimToNull(s.inputValue()));
            e.setExpectJson(trimToNull(s.expectJson()));
            e.setStatus(StringUtils.hasText(s.status()) ? s.status().trim().toUpperCase(Locale.ROOT) : "PENDING");
            e.setDurationMs(s.durationMs());
            e.setErrorMessage(trimToNull(s.errorMessage()));
            e.setScreenshotPath(trimToNull(s.screenshotPath()));
            e.setRawLog(trimToNull(s.rawLog()));
            e.setStartedAt(null);
            e.setFinishedAt(null);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            e.setIsDeleted(0);
            stepMapper.insert(e);
        }
    }

    private void markTaskFailed(Long taskId, Long userId, String err) {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_FAILED)
                .set(UiNlTaskEntity::getErrorMessage, err)
                .set(UiNlTaskEntity::getFinishedAt, now)
                .set(UiNlTaskEntity::getUpdatedBy, userId)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, taskId)
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        throw new IllegalArgumentException(err);
    }

    private UiNlCaseEntity assertCaseExists(Long caseId) {
        UiNlCaseEntity e = caseMapper.selectOne(new LambdaQueryWrapper<UiNlCaseEntity>()
                .eq(UiNlCaseEntity::getId, caseId)
                .eq(UiNlCaseEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (e == null) throw new IllegalArgumentException("ui nl case not found: " + caseId);
        return e;
    }

    private UiNlTaskEntity assertTaskExists(Long taskId) {
        UiNlTaskEntity e = taskMapper.selectOne(new LambdaQueryWrapper<UiNlTaskEntity>()
                .eq(UiNlTaskEntity::getId, taskId)
                .eq(UiNlTaskEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (e == null) throw new IllegalArgumentException("ui nl task not found: " + taskId);
        return e;
    }

    private ModelConfigEntity assertModelConfigEnabled(Long modelConfigId) {
        if (modelConfigId == null || modelConfigId <= 0) {
            throw new IllegalArgumentException("modelConfigId is required");
        }
        ModelConfigEntity e = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfigEntity>()
                .eq(ModelConfigEntity::getId, modelConfigId)
                .eq(ModelConfigEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (e == null) throw new IllegalArgumentException("model config not found: " + modelConfigId);
        if (!"ENABLED".equalsIgnoreCase(nullSafe(e.getStatus()))) {
            throw new IllegalArgumentException("model config is disabled: " + modelConfigId);
        }
        return e;
    }

    private PromptTemplateEntity assertPromptTemplateEnabled(Long promptTemplateId) {
        if (promptTemplateId == null || promptTemplateId <= 0) {
            throw new IllegalArgumentException("promptTemplateId is required");
        }
        PromptTemplateEntity e = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getId, promptTemplateId)
                .eq(PromptTemplateEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (e == null) throw new IllegalArgumentException("prompt template not found: " + promptTemplateId);
        if (!"ENABLED".equalsIgnoreCase(nullSafe(e.getStatus()))) {
            throw new IllegalArgumentException("prompt template is disabled: " + promptTemplateId);
        }
        return e;
    }

    private String composeRunnerTaskText(PromptTemplateEntity prompt, UiNlCaseEntity c) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(prompt.getContent())) {
            sb.append("【提示词约束】\n").append(prompt.getContent().trim()).append("\n\n");
        }
        if (StringUtils.hasText(c.getPrecondition())) {
            sb.append("【前置条件】\n").append(c.getPrecondition().trim()).append("\n\n");
        }
        sb.append("【自然语言测试目标】\n").append(c.getNlText().trim());
        return sb.toString();
    }

    private UiNlDtos.CaseItem toCaseItem(UiNlCaseEntity e) {
        return new UiNlDtos.CaseItem(
                e.getId(), e.getCaseNo(), e.getProjectId(), e.getVersionId(),
                e.getTitle(), e.getNlText(), e.getPrecondition(), e.getTargetEnv(),
                e.getBaseUrl(), e.getCredentialRef(), e.getStatus(), e.getTagsJson(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private UiNlDtos.TaskItem toTaskItem(UiNlTaskEntity e) {
        return new UiNlDtos.TaskItem(
                e.getId(), e.getProjectId(), e.getVersionId(), e.getUiNlCaseId(), e.getTaskNo(),
                e.getStatus(), e.getSubmittedBy(), e.getSubmittedAt(), e.getStartedAt(), e.getFinishedAt(),
                e.getRunnerRunId(), e.getModelConfigId(), e.getPromptTemplateId(),
                e.getHeadless() != null && e.getHeadless() == 1, e.getBrowserName(),
                e.getModelKey(), e.getTimeoutSeconds(), e.getPayloadJson(), e.getResultSummary(),
                e.getInterruptReason(), e.getErrorMessage(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private UiNlDtos.StepItem toStepItem(UiNlTaskStepEntity e) {
        return new UiNlDtos.StepItem(
                e.getId(), e.getTaskId(), e.getStepNo(), e.getStepTitle(), e.getActionType(),
                e.getTargetJson(), e.getInputValue(), e.getExpectJson(), e.getStatus(),
                e.getDurationMs(), e.getErrorMessage(), e.getScreenshotPath(),
                e.getStartedAt(), e.getFinishedAt(), e.getRawLog()
        );
    }

    private UiNlDtos.ReportItem toReportItem(UiNlReportEntity e) {
        return new UiNlDtos.ReportItem(
                e.getId(), e.getReportNo(), e.getTaskId(), e.getProjectId(), e.getVersionId(), e.getStatus(),
                e.getTotalSteps(), e.getPassedSteps(), e.getFailedSteps(), e.getSummary(),
                e.getReportJson(), e.getArtifactsJson(), e.getStartedAt(), e.getFinishedAt(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private String trimToNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
