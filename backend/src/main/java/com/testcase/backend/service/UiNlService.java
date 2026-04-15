package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testcase.backend.common.StatusConstants;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.dto.UiNlDtos;
import com.testcase.backend.entity.UiNlCaseEntity;
import com.testcase.backend.entity.ModelConfigEntity;
import com.testcase.backend.entity.PromptTemplateEntity;
import com.testcase.backend.entity.UiNlReportEntity;
import com.testcase.backend.entity.UiNlTaskEntity;
import com.testcase.backend.entity.UiNlTaskExecStepEntity;
import com.testcase.backend.entity.UiNlTaskStepEntity;
import com.testcase.backend.mapper.ModelConfigMapper;
import com.testcase.backend.mapper.PromptTemplateMapper;
import com.testcase.backend.mapper.UiNlCaseMapper;
import com.testcase.backend.mapper.UiNlReportMapper;
import com.testcase.backend.mapper.UiNlTaskExecStepMapper;
import com.testcase.backend.mapper.UiNlTaskMapper;
import com.testcase.backend.mapper.UiNlTaskStepMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UiNlService {
    private static final Logger log = LoggerFactory.getLogger(UiNlService.class);

    public static final String STATUS_PENDING = StatusConstants.UiNlTask.PENDING;
    public static final String STATUS_QUEUED = StatusConstants.UiNlTask.QUEUED;
    public static final String STATUS_PLANNING = StatusConstants.UiNlTask.PLANNING;
    public static final String STATUS_READY = StatusConstants.UiNlTask.READY;
    public static final String STATUS_RUNNING = StatusConstants.UiNlTask.RUNNING;
    public static final String STATUS_COMPLETED = StatusConstants.UiNlTask.COMPLETED;
    public static final String STATUS_FAILED = StatusConstants.UiNlTask.FAILED;
    public static final String STATUS_CANCELLED = StatusConstants.UiNlTask.CANCELLED;
    public static final String STATUS_INTERRUPTED = StatusConstants.UiNlTask.INTERRUPTED;

    /**
     * 规划步骤表 {@code ui_nl_task_steps.status} 专用：表示「模型生成 / 人工编辑」，与 {@link UiNlTaskExecStepEntity} 的执行结果无关。
     */
    public static final String STATUS_PLAN_STEP_GENERATED = "GENERATED";
    public static final String STATUS_PLAN_STEP_EDITED = "EDITED";

    /** 传给 ui-runner 的短约束；具体步骤由 plannedSteps 承载，与步骤表一致。 */
    private static final String RUNNER_EXEC_TASK_HINT =
            "严格遵守下列步骤顺序，不要跳过；每步完成后根据预期自检，再继续下一步。";

    private final UiNlCaseMapper caseMapper;
    private final UiNlTaskMapper taskMapper;
    private final UiNlTaskStepMapper stepMapper;
    private final UiNlTaskExecStepMapper execStepMapper;
    private final UiNlReportMapper reportMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final UiRunnerClient uiRunnerClient;
    private final UiNlHtmlReportService uiNlHtmlReportService;
    private final OperationLogService operationLogService;

    public UiNlService(
            UiNlCaseMapper caseMapper,
            UiNlTaskMapper taskMapper,
            UiNlTaskStepMapper stepMapper,
            UiNlTaskExecStepMapper execStepMapper,
            UiNlReportMapper reportMapper,
            ModelConfigMapper modelConfigMapper,
            PromptTemplateMapper promptTemplateMapper,
            ModelClient modelClient,
            ObjectMapper objectMapper,
            UiRunnerClient uiRunnerClient,
            UiNlHtmlReportService uiNlHtmlReportService,
            OperationLogService operationLogService
    ) {
        this.caseMapper = caseMapper;
        this.taskMapper = taskMapper;
        this.stepMapper = stepMapper;
        this.execStepMapper = execStepMapper;
        this.reportMapper = reportMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.uiRunnerClient = uiRunnerClient;
        this.uiNlHtmlReportService = uiNlHtmlReportService;
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
        e.setStatus(StringUtils.hasText(body.status()) ? body.status().trim().toUpperCase(Locale.ROOT) : StatusConstants.Switch.ENABLED);
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
        t.setCreatedBy(userId);
        t.setUpdatedBy(userId);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t.setIsDeleted(0);
        taskMapper.insert(t);
        operationLogService.log("UI_NL_TASK", t.getId(), "CREATE", null, t, null);
        return toTaskItem(t);
    }

    public PagedResult<UiNlDtos.TaskItem> listTasks(Long projectId, Long versionId, String status, String caseTitle, String lastExecStatus, int pageNo, int pageSize) {
        int safeNo = Math.max(1, pageNo);
        int safeSize = Math.max(1, pageSize);
        var wrapper = new LambdaQueryWrapper<UiNlTaskEntity>()
                .eq(UiNlTaskEntity::getIsDeleted, 0)
                .orderByDesc(UiNlTaskEntity::getId);
        if (projectId != null && projectId > 0) wrapper.eq(UiNlTaskEntity::getProjectId, projectId);
        if (versionId != null && versionId > 0) wrapper.eq(UiNlTaskEntity::getVersionId, versionId);
        if (StringUtils.hasText(status)) wrapper.eq(UiNlTaskEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        if (StringUtils.hasText(lastExecStatus)) {
            String le = lastExecStatus.trim().toUpperCase(Locale.ROOT);
            if ("NOT_EXECUTED".equals(le)) {
                wrapper.isNull(UiNlTaskEntity::getLastExecStatus);
            } else {
                wrapper.eq(UiNlTaskEntity::getLastExecStatus, le);
            }
        }
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
        if (STATUS_RUNNING.equals(t.getLastExecStatus())) {
            throw new IllegalArgumentException("browser execution in progress, cannot regenerate steps now");
        }
        if (List.of(STATUS_PLANNING, STATUS_QUEUED).contains(t.getStatus())) {
            throw new IllegalArgumentException("step generation already in progress: " + t.getStatus());
        }
        if (!List.of(STATUS_PENDING, STATUS_FAILED, STATUS_CANCELLED, STATUS_INTERRUPTED, STATUS_READY).contains(t.getStatus())) {
            throw new IllegalArgumentException("task status does not allow generate steps: " + t.getStatus());
        }

        // 入队：只把任务推进到 QUEUED，真正生成由后台 planner 执行（可中断）
        LocalDateTime now = LocalDateTime.now();
        // 物理删除：避免 uk(task_id, step_no) 与逻辑删除冲突
        stepMapper.delete(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId));
        execStepMapper.delete(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                .eq(UiNlTaskExecStepEntity::getTaskId, taskId));

        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_QUEUED)
                .set(UiNlTaskEntity::getLastExecStatus, null)
                .set(UiNlTaskEntity::getErrorMessage, null)
                .set(UiNlTaskEntity::getInterruptReason, null)
                .set(UiNlTaskEntity::getPlanStartedAt, null)
                .set(UiNlTaskEntity::getPlanFinishedAt, null)
                .set(UiNlTaskEntity::getExecStartedAt, null)
                .set(UiNlTaskEntity::getExecFinishedAt, null)
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
                .set(UiNlTaskEntity::getPlanStartedAt, now)
                .set(UiNlTaskEntity::getPlanFinishedAt, null)
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
            replacePlanSteps(task.getId(), planned, now);

            LocalDateTime planDone = LocalDateTime.now();
            taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                    .set(UiNlTaskEntity::getStatus, STATUS_READY)
                    .set(UiNlTaskEntity::getPlanFinishedAt, planDone)
                    .set(UiNlTaskEntity::getUpdatedAt, planDone)
                    .eq(UiNlTaskEntity::getId, task.getId())
                    .eq(UiNlTaskEntity::getIsDeleted, 0));
            operationLogService.log("UI_NL_TASK", task.getId(), STATUS_READY, null, null, "steps planned");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "step generation failed" : e.getMessage();
            taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                    .set(UiNlTaskEntity::getStatus, STATUS_FAILED)
                    .set(UiNlTaskEntity::getPlanFinishedAt, LocalDateTime.now())
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
                .set(UiNlTaskEntity::getInterruptReason, StringUtils.hasText(reason) ? reason.trim() : "manual interrupt")
                .set(UiNlTaskEntity::getPlanFinishedAt, now)
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
        if (STATUS_RUNNING.equals(t.getLastExecStatus())) {
            throw new IllegalArgumentException("browser execution in progress, cannot update task");
        }
        if (List.of(STATUS_PLANNING, STATUS_QUEUED).contains(t.getStatus())) {
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
        t.setUpdatedBy(userId);
        t.setUpdatedAt(now);
        taskMapper.updateById(t);
        operationLogService.log("UI_NL_TASK", t.getId(), "UPDATE", null, t, null);
        return toTaskItem(t);
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (STATUS_RUNNING.equals(t.getLastExecStatus())) {
            throw new IllegalArgumentException("browser execution in progress, cannot delete");
        }
        if (List.of(STATUS_PLANNING, STATUS_QUEUED).contains(t.getStatus())) {
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
        execStepMapper.delete(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                .eq(UiNlTaskExecStepEntity::getTaskId, taskId));
        operationLogService.log("UI_NL_TASK", taskId, "DELETE", null, null, null);
    }

    @Transactional
    public UiNlDtos.TaskItem runTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (STATUS_RUNNING.equals(t.getLastExecStatus())) {
            throw new IllegalArgumentException("browser execution is already running");
        }
        if (!STATUS_READY.equals(t.getStatus())) {
            throw new IllegalArgumentException("task must be READY (steps generated) to run browser, current: " + t.getStatus());
        }
        // 仅 READY：步骤已生成；执行结果写入 last_exec_status，不修改 status
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
        assertPromptTemplateEnabled(t.getPromptTemplateId());

        List<UiNlTaskStepEntity> plannedRows = stepMapper.selectList(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId)
                .eq(UiNlTaskStepEntity::getIsDeleted, 0)
                .orderByAsc(UiNlTaskStepEntity::getStepNo));
        ArrayNode plannedJson = objectMapper.createArrayNode();
        for (UiNlTaskStepEntity s : plannedRows) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("stepNo", s.getStepNo() == null ? 0 : s.getStepNo());
            if (StringUtils.hasText(s.getStepTitle())) o.put("title", s.getStepTitle().trim());
            if (StringUtils.hasText(s.getActionType())) o.put("actionType", s.getActionType().trim());
            if (StringUtils.hasText(s.getInputValue())) o.put("inputValue", s.getInputValue().trim());
            if (StringUtils.hasText(s.getExpectJson())) o.put("expectJson", s.getExpectJson().trim());
            plannedJson.add(o);
        }

        // latest-only：新一轮执行前先清理旧执行轨迹
        execStepMapper.delete(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                .eq(UiNlTaskExecStepEntity::getTaskId, taskId));

        LocalDateTime now = LocalDateTime.now();
        if (!StringUtils.hasText(t.getRunnerRunId())) {
            t.setRunnerRunId("RUN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT));
        }
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getLastExecStatus, STATUS_RUNNING)
                .set(UiNlTaskEntity::getExecStartedAt, now)
                .set(UiNlTaskEntity::getExecFinishedAt, null)
                .set(UiNlTaskEntity::getErrorMessage, null)
                .set(UiNlTaskEntity::getInterruptReason, null)
                .set(UiNlTaskEntity::getRunnerRunId, t.getRunnerRunId())
                .set(UiNlTaskEntity::getUpdatedBy, userId)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, taskId)
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        t = assertTaskExists(taskId);

        UiRunnerClient.RunResponse response = uiRunnerClient.run(
                t.getRunnerRunId(),
                RUNNER_EXEC_TASK_HINT,
                c.getBaseUrl(),
                t.getHeadless() != null && t.getHeadless() == 1,
                StringUtils.hasText(t.getModelKey()) ? t.getModelKey() : model.getModelKey(),
                t.getTimeoutSeconds(),
                plannedJson
        );
        if (!response.accepted()) {
            markRunnerLaunchRejected(t.getId(), userId, StringUtils.hasText(response.message()) ? response.message() : "runner rejected");
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
        String runId = t.getRunnerRunId();
        if (STATUS_RUNNING.equals(t.getLastExecStatus()) && StringUtils.hasText(runId)) {
            try {
                uiRunnerClient.cancel(runId);
            } catch (Exception e) {
                log.warn("cancel runner failed taskId={} runId={}: {}", taskId, runId, e.getMessage());
            }
        }
        // last_exec=RUNNING 时轮询依赖该字段；此处拉 runner 终态写入执行轨迹
        LocalDateTime now = LocalDateTime.now();
        if (StringUtils.hasText(runId)) {
            tryPullRunnerTerminalAndSync(taskId, runId, now);
        }
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_READY)
                .set(UiNlTaskEntity::getLastExecStatus, STATUS_CANCELLED)
                .set(UiNlTaskEntity::getInterruptReason, StringUtils.hasText(reason) ? reason.trim() : "manual cancel")
                .set(UiNlTaskEntity::getExecFinishedAt, now)
                .set(UiNlTaskEntity::getUpdatedBy, userId)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, taskId)
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        operationLogService.log("UI_NL_TASK", taskId, "CANCEL", null, null, reason);
    }

    /**
     * 中断后拉取 runner 终态并写入执行轨迹表；规划步骤状态仅表示生成/编辑，不再镜像执行结果。
     */
    private void tryPullRunnerTerminalAndSync(Long taskId, String runId, LocalDateTime now) {
        for (int attempt = 0; attempt < 8; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            UiRunnerClient.StatusResponse status;
            try {
                status = uiRunnerClient.status(runId);
            } catch (Exception e) {
                log.debug("runner status after cancel failed: {}", e.getMessage());
                continue;
            }
            if (status == null || !StringUtils.hasText(status.status())) {
                continue;
            }
            String st = status.status().trim().toUpperCase(Locale.ROOT);
            if (List.of("RUNNING", "QUEUED", "PENDING").contains(st)) {
                continue;
            }
            List<UiRunnerClient.StepResult> stepResults = status.steps() == null ? List.of() : status.steps();
            replaceExecutionSteps(taskId, stepResults, now);
            return;
        }
    }

    @Transactional
    public UiNlDtos.TaskItem retryTask(Long taskId, Long userId) {
        UiNlTaskEntity t = assertTaskExists(taskId);
        if (STATUS_RUNNING.equals(t.getLastExecStatus())) {
            throw new IllegalArgumentException("browser execution in progress, cannot retry");
        }
        t.setStatus(STATUS_PENDING);
        t.setLastExecStatus(null);
        t.setRunnerRunId(null);
        t.setErrorMessage(null);
        t.setResultSummary(null);
        t.setInterruptReason(null);
        t.setPlanStartedAt(null);
        t.setPlanFinishedAt(null);
        t.setExecStartedAt(null);
        t.setExecFinishedAt(null);
        t.setUpdatedBy(userId);
        t.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        // 物理删除：避免 uk(task_id, step_no) 与逻辑删除冲突
        stepMapper.delete(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getTaskId, taskId));
        execStepMapper.delete(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                .eq(UiNlTaskExecStepEntity::getTaskId, taskId));
        operationLogService.log("UI_NL_TASK", t.getId(), "RETRY", null, null, null);
        return toTaskItem(t);
    }

    public List<UiNlDtos.StepItem> listTaskSteps(Long taskId, String phaseRaw) {
        assertTaskExists(taskId);
        String phase = normalizeStepPhase(phaseRaw);
        if ("EXEC".equals(phase)) {
            return execStepMapper.selectList(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                            .eq(UiNlTaskExecStepEntity::getTaskId, taskId)
                            .eq(UiNlTaskExecStepEntity::getIsDeleted, 0)
                            .orderByAsc(UiNlTaskExecStepEntity::getStepNo))
                    .stream()
                    .map(e -> toStepItem(e, "EXEC"))
                    .toList();
        }
        return stepMapper.selectList(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                        .eq(UiNlTaskStepEntity::getTaskId, taskId)
                        .eq(UiNlTaskStepEntity::getIsDeleted, 0)
                        .orderByAsc(UiNlTaskStepEntity::getStepNo))
                .stream()
                .map(e -> toStepItem(e, "PLAN"))
                .toList();
    }

    public UiNlDtos.StepItem detailStep(Long stepId, String phaseRaw) {
        String phase = normalizeStepPhase(phaseRaw);
        if ("EXEC".equals(phase)) {
            UiNlTaskExecStepEntity s = execStepMapper.selectOne(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                    .eq(UiNlTaskExecStepEntity::getId, stepId)
                    .eq(UiNlTaskExecStepEntity::getIsDeleted, 0)
                    .last("LIMIT 1"));
            if (s == null) throw new IllegalArgumentException("step not found");
            return toStepItem(s, "EXEC");
        }
        UiNlTaskStepEntity s = stepMapper.selectOne(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getId, stepId)
                .eq(UiNlTaskStepEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (s == null) throw new IllegalArgumentException("step not found");
        return toStepItem(s, "PLAN");
    }

    @Transactional
    public UiNlDtos.StepItem updatePlanStep(Long stepId, UiNlDtos.UpdatePlanStepRequest body) {
        UiNlTaskStepEntity s = stepMapper.selectOne(new LambdaQueryWrapper<UiNlTaskStepEntity>()
                .eq(UiNlTaskStepEntity::getId, stepId)
                .eq(UiNlTaskStepEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (s == null) {
            throw new IllegalArgumentException("step not found");
        }
        UiNlTaskEntity task = assertTaskExists(s.getTaskId());
        if (STATUS_RUNNING.equals(task.getLastExecStatus())) {
            throw new IllegalArgumentException("cannot edit plan steps while browser execution is running");
        }
        String st = task.getStatus() == null ? "" : task.getStatus().trim().toUpperCase(Locale.ROOT);
        if (List.of(STATUS_QUEUED, STATUS_PLANNING).contains(st)) {
            throw new IllegalArgumentException("cannot edit plan steps while task is " + st);
        }
        if (!StringUtils.hasText(body.stepTitle()) || !StringUtils.hasText(body.inputValue())) {
            throw new IllegalArgumentException("stepTitle and inputValue are required");
        }
        String expectRaw = trimToNull(body.expectJson());
        if (StringUtils.hasText(expectRaw)) {
            try {
                objectMapper.readTree(expectRaw);
            } catch (Exception e) {
                throw new IllegalArgumentException("expectJson must be valid JSON");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        s.setStepTitle(body.stepTitle().trim());
        s.setInputValue(body.inputValue().trim());
        s.setActionType(trimToNull(body.actionType()));
        s.setExpectJson(expectRaw);
        s.setStatus(STATUS_PLAN_STEP_EDITED);
        s.setUpdatedAt(now);
        stepMapper.updateById(s);
        operationLogService.log("UI_NL_PLAN_STEP", s.getId(), "UPDATE", null, null, null);
        return toStepItem(s, "PLAN");
    }

    /**
     * 执行步骤截图二进制流（用于前端步骤详情预览）。路径解析规则与 HTML 报告内嵌截图一致。
     */
    public ResponseEntity<byte[]> execStepScreenshot(Long stepId) {
        UiNlTaskExecStepEntity step = execStepMapper.selectOne(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                .eq(UiNlTaskExecStepEntity::getId, stepId)
                .eq(UiNlTaskExecStepEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (step == null) {
            throw new IllegalArgumentException("step not found");
        }
        if (!StringUtils.hasText(step.getScreenshotPath())) {
            throw new IllegalArgumentException("screenshot path is empty");
        }
        UiNlReportEntity report = reportMapper.selectOne(new LambdaQueryWrapper<UiNlReportEntity>()
                .eq(UiNlReportEntity::getTaskId, step.getTaskId())
                .eq(UiNlReportEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        String artifactsJson = report == null ? null : report.getArtifactsJson();
        Path path = uiNlHtmlReportService.resolveExecScreenshotPath(step.getScreenshotPath(), artifactsJson);
        if (path == null) {
            throw new IllegalArgumentException("screenshot file not found");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (Exception e) {
            throw new IllegalArgumentException("screenshot read failed: " + e.getMessage());
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (name.endsWith(".png")) {
            mt = MediaType.IMAGE_PNG;
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            mt = MediaType.IMAGE_JPEG;
        } else if (name.endsWith(".webp")) {
            mt = MediaType.parseMediaType("image/webp");
        }
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(bytes);
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

    public ReportHtmlPayload reportHtml(Long reportId) {
        UiNlReportEntity r = reportMapper.selectOne(new LambdaQueryWrapper<UiNlReportEntity>()
                .eq(UiNlReportEntity::getId, reportId)
                .eq(UiNlReportEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (r == null) {
            throw new IllegalArgumentException("report not found");
        }
        if (!StringUtils.hasText(r.getReportFilePath())) {
            throw new IllegalArgumentException("report html not generated");
        }
        Resource resource = new FileSystemResource(r.getReportFilePath().trim());
        if (!resource.exists()) {
            throw new IllegalArgumentException("report html file not found");
        }
        String fileName = StringUtils.hasText(r.getReportNo()) ? r.getReportNo().trim() + ".html" : "ui-nl-report.html";
        return new ReportHtmlPayload(resource, fileName);
    }

    @Transactional
    public void pollRunnerAndUpdateTask(UiNlTaskEntity task) {
        if (!STATUS_RUNNING.equals(task.getLastExecStatus()) || !StringUtils.hasText(task.getRunnerRunId())) {
            return;
        }
        UiRunnerClient.StatusResponse status;
        try {
            status = uiRunnerClient.status(task.getRunnerRunId());
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // runner 重启后内存态丢失，GET /runs/{id} 会持续 404；若不落库结束，任务会永远 RUNNING 并刷屏轮询
            if (msg.contains("http 404")) {
                String err = "runner 中已找不到本次执行记录（runId=" + task.getRunnerRunId()
                        + "）。常见原因：重启过 ui-runner 或 run从未成功创建。请重新点击「执行任务」。";
                finalizeTaskFailedFromRunnerPoll(task, err);
                return;
            }
            throw e;
        }
        if (!StringUtils.hasText(status.status())) return;
        String st = status.status().trim().toUpperCase(Locale.ROOT);
        if (List.of("RUNNING", "QUEUED", "PENDING").contains(st)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<UiRunnerClient.StepResult> stepResults = status.steps() == null ? List.of() : status.steps();
        replaceExecutionSteps(task.getId(), stepResults, now);

        if ("COMPLETED".equals(st) || "SUCCESS".equals(st)) {
            int total = stepResults.size();
            int passed = (int) stepResults.stream().filter(s -> "SUCCESS".equalsIgnoreCase(nullSafe(s.status()))).count();
            int failed = Math.max(0, total - passed);
            createOrUpdateReport(task, "SUCCESS", total, passed, failed, status.summary(), status.artifactsJson(), now);
            taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                    .set(UiNlTaskEntity::getStatus, STATUS_READY)
                    .set(UiNlTaskEntity::getLastExecStatus, STATUS_COMPLETED)
                    .set(UiNlTaskEntity::getExecFinishedAt, now)
                    .set(UiNlTaskEntity::getErrorMessage, null)
                    .set(UiNlTaskEntity::getInterruptReason, null)
                    .set(UiNlTaskEntity::getResultSummary, wrapRunnerSummaryAsReportJson(status.summary()))
                    .set(UiNlTaskEntity::getUpdatedAt, now)
                    .eq(UiNlTaskEntity::getId, task.getId())
                    .eq(UiNlTaskEntity::getIsDeleted, 0));
            operationLogService.log("UI_NL_TASK", task.getId(), "EXEC_COMPLETED", null, null, null);
            return;
        }

        String error = StringUtils.hasText(status.errorMessage()) ? status.errorMessage() : "runner execution failed";
        int totalF = stepResults.size();
        int passedF = (int) stepResults.stream().filter(s -> "SUCCESS".equalsIgnoreCase(nullSafe(s.status()))).count();
        int failedF = Math.max(0, totalF - passedF);
        createOrUpdateReport(task, "FAILED", totalF, passedF, failedF, error, status.artifactsJson(), now);
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_READY)
                .set(UiNlTaskEntity::getLastExecStatus, STATUS_FAILED)
                .set(UiNlTaskEntity::getExecFinishedAt, now)
                .set(UiNlTaskEntity::getErrorMessage, error)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, task.getId())
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        operationLogService.log("UI_NL_TASK", task.getId(), "EXEC_FAILED", null, null, error);
    }

    private void finalizeTaskFailedFromRunnerPoll(UiNlTaskEntity task, String error) {
        LocalDateTime now = LocalDateTime.now();
        Long uid = task.getUpdatedBy() != null ? task.getUpdatedBy()
                : (task.getSubmittedBy() != null ? task.getSubmittedBy() : 1L);
        createOrUpdateReport(task, "FAILED", 0, 0, 0, error, null, now);
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_READY)
                .set(UiNlTaskEntity::getLastExecStatus, STATUS_FAILED)
                .set(UiNlTaskEntity::getExecFinishedAt, now)
                .set(UiNlTaskEntity::getErrorMessage, error)
                .set(UiNlTaskEntity::getUpdatedBy, uid)
                .set(UiNlTaskEntity::getUpdatedAt, now)
                .eq(UiNlTaskEntity::getId, task.getId())
                .eq(UiNlTaskEntity::getIsDeleted, 0));
        operationLogService.log("UI_NL_TASK", task.getId(), "EXEC_FAILED", null, null, error);
    }

    public List<UiNlTaskEntity> listRunningTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<UiNlTaskEntity>()
                .eq(UiNlTaskEntity::getIsDeleted, 0)
                .eq(UiNlTaskEntity::getLastExecStatus, STATUS_RUNNING)
                .orderByAsc(UiNlTaskEntity::getExecStartedAt));
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
        UiNlCaseEntity uiCase = caseMapper.selectOne(new LambdaQueryWrapper<UiNlCaseEntity>()
                .eq(UiNlCaseEntity::getId, task.getUiNlCaseId())
                .eq(UiNlCaseEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        List<UiNlTaskExecStepEntity> execSteps = execStepMapper.selectList(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                .eq(UiNlTaskExecStepEntity::getTaskId, task.getId())
                .eq(UiNlTaskExecStepEntity::getIsDeleted, 0)
                .orderByAsc(UiNlTaskExecStepEntity::getStepNo));
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
            r.setIsDeleted(0);
            r.setCreatedAt(LocalDateTime.now());
        }
        r.setStartedAt(task.getExecStartedAt());
        r.setStatus(status);
        r.setTotalSteps(total);
        r.setPassedSteps(passed);
        r.setFailedSteps(failed);
        r.setSummary(trimToNull(summary));
        r.setArtifactsJson(trimToNull(artifactsJson));
        r.setFinishedAt(finishedAt);
        r.setUpdatedAt(LocalDateTime.now());
        String reportFilePath = uiNlHtmlReportService.generateReport(task, uiCase, r, execSteps);
        r.setReportFilePath(trimToNull(reportFilePath));
        r.setReportGeneratedAt(StringUtils.hasText(reportFilePath) ? LocalDateTime.now() : null);
        if (r.getId() == null) {
            reportMapper.insert(r);
        } else {
            reportMapper.updateById(r);
        }
    }

    private void replacePlanSteps(Long taskId, List<UiRunnerClient.StepResult> steps, LocalDateTime now) {
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
            e.setInputValue(trimToNull(s.inputValue()));
            e.setExpectJson(trimToNull(s.expectJson()));
            e.setStatus(STATUS_PLAN_STEP_GENERATED);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            e.setIsDeleted(0);
            stepMapper.insert(e);
        }
    }

    private void replaceExecutionSteps(Long taskId, List<UiRunnerClient.StepResult> steps, LocalDateTime now) {
        // latest-only：每次执行只保留最新轨迹
        execStepMapper.delete(new LambdaQueryWrapper<UiNlTaskExecStepEntity>()
                .eq(UiNlTaskExecStepEntity::getTaskId, taskId));
        List<UiRunnerClient.StepResult> sorted = steps.stream()
                .sorted(Comparator.comparing(s -> s.stepNo() == null ? Integer.MAX_VALUE : s.stepNo()))
                .toList();
        int seq = 1;
        for (UiRunnerClient.StepResult s : sorted) {
            UiNlTaskExecStepEntity e = new UiNlTaskExecStepEntity();
            e.setTaskId(taskId);
            e.setStepNo(seq++);
            e.setStepTitle(trimToNull(s.title()));
            e.setActionType(StringUtils.hasText(s.actionType()) ? s.actionType().trim() : "ACTION");
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
            execStepMapper.insert(e);
        }
    }

    /** runner 拒绝启动：步骤生成状态保持 READY，失败记入 last_exec */
    private void markRunnerLaunchRejected(Long taskId, Long userId, String err) {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<UiNlTaskEntity>()
                .set(UiNlTaskEntity::getStatus, STATUS_READY)
                .set(UiNlTaskEntity::getLastExecStatus, STATUS_FAILED)
                .set(UiNlTaskEntity::getErrorMessage, err)
                .set(UiNlTaskEntity::getExecFinishedAt, now)
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
        if (!StatusConstants.Switch.ENABLED.equalsIgnoreCase(nullSafe(e.getStatus()))) {
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
        if (!StatusConstants.Switch.ENABLED.equalsIgnoreCase(nullSafe(e.getStatus()))) {
            throw new IllegalArgumentException("prompt template is disabled: " + promptTemplateId);
        }
        return e;
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
                e.getStatus(), e.getLastExecStatus(), e.getSubmittedBy(), e.getSubmittedAt(),
                e.getPlanStartedAt(), e.getPlanFinishedAt(), e.getExecStartedAt(), e.getExecFinishedAt(),
                e.getRunnerRunId(), e.getModelConfigId(), e.getPromptTemplateId(),
                e.getHeadless() != null && e.getHeadless() == 1, e.getBrowserName(),
                e.getModelKey(), e.getTimeoutSeconds(), e.getResultSummary(),
                e.getInterruptReason(), e.getErrorMessage(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private UiNlDtos.StepItem toStepItem(UiNlTaskStepEntity e, String phase) {
        return new UiNlDtos.StepItem(
                e.getId(), e.getTaskId(), e.getStepNo(), e.getStepTitle(), e.getActionType(),
                null, e.getInputValue(), e.getExpectJson(), e.getStatus(),
                null, null, null, null, null, null, phase
        );
    }

    private UiNlDtos.StepItem toStepItem(UiNlTaskExecStepEntity e, String phase) {
        return new UiNlDtos.StepItem(
                e.getId(), e.getTaskId(), e.getStepNo(), e.getStepTitle(), e.getActionType(),
                e.getTargetJson(), e.getInputValue(), e.getExpectJson(), e.getStatus(),
                e.getDurationMs(), e.getErrorMessage(), e.getScreenshotPath(),
                e.getStartedAt(), e.getFinishedAt(), e.getRawLog(), phase
        );
    }

    private UiNlDtos.ReportItem toReportItem(UiNlReportEntity e) {
        return new UiNlDtos.ReportItem(
                e.getId(), e.getReportNo(), e.getTaskId(), e.getProjectId(), e.getVersionId(), e.getStatus(),
                e.getTotalSteps(), e.getPassedSteps(), e.getFailedSteps(), e.getSummary(),
                e.getArtifactsJson(), e.getStartedAt(), e.getFinishedAt(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getReportFilePath(), e.getReportGeneratedAt()
        );
    }

    private String normalizeStepPhase(String phaseRaw) {
        if (!StringUtils.hasText(phaseRaw)) {
            return "PLAN";
        }
        String s = phaseRaw.trim().toUpperCase(Locale.ROOT);
        return "EXEC".equals(s) ? "EXEC" : "PLAN";
    }

    /**
     * MySQL JSON 列 {@code ui_nl_tasks.result_summary} 须为合法 JSON，不能写入任意纯文本。
     */
    private String wrapRunnerSummaryAsReportJson(String summary) {
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        try {
            var node = objectMapper.createObjectNode();
            node.put("runnerSummary", summary.trim());
            node.put("schemaVersion", 1);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("wrap result_summary json failed: {}", e.getMessage());
            return "{\"runnerSummary\":\"\",\"schemaVersion\":1}";
        }
    }

    private String trimToNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public record ReportHtmlPayload(Resource resource, String fileName) {
    }
}
