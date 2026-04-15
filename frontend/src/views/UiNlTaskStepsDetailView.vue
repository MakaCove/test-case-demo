<script setup lang="ts">
import { computed, inject, onMounted, onUnmounted, ref, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, type Project, type UiNlStep, type UiNlTask, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const route = useRoute()
const router = useRouter()

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const task = ref<UiNlTask | null>(null)
const caseTitleText = ref('')

const loadingTask = ref(false)
const loadingSteps = ref(false)
const planSteps = ref<UiNlStep[]>([])
const execSteps = ref<UiNlStep[]>([])
const activePhase = ref<'PLAN' | 'EXEC'>('PLAN')

const stepDetailVisible = ref(false)
const currentStep = ref<UiNlStep | null>(null)
const currentStepPhase = ref<'PLAN' | 'EXEC'>('PLAN')
const screenshotPreviewUrl = ref<string | null>(null)
const screenshotLoading = ref(false)

function revokeScreenshotPreview() {
  if (screenshotPreviewUrl.value) {
    URL.revokeObjectURL(screenshotPreviewUrl.value)
    screenshotPreviewUrl.value = null
  }
}

const taskStatus = computed(() => task.value?.status || '')
const normalizedTaskStatus = computed(() => taskStatus.value.trim().toUpperCase())
const normalizedLastExec = computed(() => (task.value?.lastExecStatus || '').trim().toUpperCase())

const hasPlanSteps = computed(() => planSteps.value.length > 0)

/** 与后端 runTask 一致：步骤生成须为 READY，且当前没有在跑浏览器 */
const canRunUiNlTask = computed(() => {
  if (!task.value) return false
  if (normalizedTaskStatus.value !== 'READY') return false
  if (normalizedLastExec.value === 'RUNNING') return false
  return hasPlanSteps.value
})

const runUiNlTaskBlockReason = computed(() => {
  if (!task.value) return '未加载任务'
  const s = normalizedTaskStatus.value
  if (normalizedLastExec.value === 'RUNNING') return '浏览器任务执行中，请稍候'
  if (['QUEUED', 'PLANNING'].includes(s)) return '步骤正在生成中，请稍候再执行'
  if (['PENDING'].includes(s)) return '请先在任务列表点击「生成步骤」，待步骤生成状态为「步骤已生成」后再执行'
  if (s !== 'READY') {
    return `当前步骤生成状态（${taskPlanStatusLabel(task.value.status)}）不允许执行，需为「步骤已生成」`
  }
  if (!hasPlanSteps.value) return '暂无规划步骤，请先生成步骤'
  return ''
})

const runUiNlTaskButtonLabel = computed(() => {
  const le = normalizedLastExec.value
  if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(le)) return '再次执行'
  return '执行任务'
})

/** 仅浏览器执行中可中断（排队/生成中请在任务列表处理或等待结束） */
const canInterruptUiNlTask = computed(() => {
  if (!task.value) return false
  return normalizedLastExec.value === 'RUNNING'
})

const interruptUiNlTaskBlockReason = computed(() => {
  if (!task.value) return '未加载任务'
  if (canInterruptUiNlTask.value) return ''
  const s = normalizedTaskStatus.value
  if (['QUEUED', 'PLANNING'].includes(s)) return '排队或生成步骤中不可在此中断，请至任务列表操作或等待完成'
  return '仅在浏览器执行中可中断'
})

const interruptSubmitting = ref(false)

const canEditPlanSteps = computed(() => {
  if (!task.value) return false
  if (normalizedLastExec.value === 'RUNNING') return false
  const s = normalizedTaskStatus.value
  return !['QUEUED', 'PLANNING'].includes(s)
})

const planEditBlockedReason = computed(() => {
  if (!task.value) return '未加载任务'
  if (normalizedLastExec.value === 'RUNNING') return '浏览器执行中，暂不可修改规划步骤'
  const s = normalizedTaskStatus.value
  if (['QUEUED', 'PLANNING'].includes(s)) return '步骤正在生成中，请稍候再修改'
  return ''
})

const planEditVisible = ref(false)
const planEditSaving = ref(false)
const planEditForm = ref({
  id: 0,
  stepNo: 0,
  stepTitle: '',
  actionType: '',
  inputValue: '',
  expectJson: '',
})

function openPlanEdit(row: UiNlStep) {
  planEditForm.value = {
    id: row.id,
    stepNo: row.stepNo,
    stepTitle: row.stepTitle ?? '',
    actionType: row.actionType ?? '',
    inputValue: row.inputValue ?? '',
    expectJson: row.expectJson ?? '',
  }
  planEditVisible.value = true
}

function onPlanEditDialogClosed() {
  planEditForm.value = { id: 0, stepNo: 0, stepTitle: '', actionType: '', inputValue: '', expectJson: '' }
}

async function savePlanEdit() {
  const f = planEditForm.value
  if (!f.stepTitle.trim() || !f.inputValue.trim()) {
    ElMessage.warning('请填写步骤标题与操作内容')
    return
  }
  const exp = f.expectJson.trim()
  if (exp) {
    try {
      JSON.parse(exp)
    } catch {
      ElMessage.warning('预期结果须为合法 JSON，例如 {"expected_result":"描述"}')
      return
    }
  }
  planEditSaving.value = true
  try {
    await api.updateUiNlPlanStep(f.id, {
      stepTitle: f.stepTitle.trim(),
      actionType: f.actionType.trim() || undefined,
      inputValue: f.inputValue.trim(),
      expectJson: exp || undefined,
    })
    ElMessage.success('已保存')
    planEditVisible.value = false
    await refresh()
  } catch (e: any) {
    ElMessage.error(readableError(e, '保存失败'))
  } finally {
    planEditSaving.value = false
  }
}

/** 任务步骤生成流程（任务中心主状态） */
function taskPlanStatusLabel(statusValue?: string) {
  if (!statusValue) return '—'
  const s = statusValue.trim().toUpperCase()
  if (s === 'PENDING') return '待启动'
  if (s === 'QUEUED') return '排队'
  if (s === 'PLANNING') return '生成中'
  if (s === 'READY') return '步骤已生成'
  if (s === 'FAILED') return '生成失败'
  if (s === 'INTERRUPTED') return '生成中断'
  if (s === 'CANCELLED') return '已取消'
  return statusValue
}

/** 最近一轮浏览器执行摘要 */
function lastExecStatusLabel(v?: string | null) {
  if (v == null || !String(v).trim()) return '未执行'
  const s = String(v).trim().toUpperCase()
  if (s === 'RUNNING') return '执行中'
  if (s === 'COMPLETED') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'CANCELLED') return '已取消'
  return String(v)
}

/** 执行轨迹：runner 步骤结果 */
function stepStatusLabel(statusValue?: string) {
  if (!statusValue) return '—'
  const s = statusValue.trim().toUpperCase()
  if (s === 'PENDING') return '待执行'
  if (s === 'RUNNING') return '执行中'
  if (s === 'SUCCESS' || s === 'COMPLETED') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'CANCELLED') return '取消'
  if (s === 'SKIPPED') return '跳过'
  if (s === 'INTERRUPTED') return '中断'
  return statusValue
}

/** 规划步骤：仅表示模型生成 / 人工编辑，与执行成败无关 */
function planStepStatusLabel(statusValue?: string) {
  const s = (statusValue || '').trim().toUpperCase()
  if (s === 'EDITED') return '已编辑'
  if (s === 'GENERATED' || s === 'PENDING' || !s) return '已生成'
  return '已生成'
}

function readableError(err: any, fallback: string) {
  const raw = String(err?.message || '').trim()
  if (!raw) return fallback
  return raw
    .replace(/^business validation failed:\s*/i, '')
    .replace(/^request failed:\s*/i, '')
}

function projectLabel(id?: number) {
  const p = projects.value.find((x) => x.id === id)
  return p ? `${p.name}（${p.code}）` : id ? `项目#${id}` : '—'
}

function versionLabel(id?: number) {
  const v = versions.value.find((x) => x.id === id)
  return v ? `${v.versionNo}${v.name ? ` - ${v.name}` : ''}` : id ? `版本#${id}` : '—'
}

async function loadProjects() {
  const data = await api.getProjects({ pageNo: 1, pageSize: 200 })
  projects.value = data.records
}

async function loadVersions() {
  const data = await api.getAllVersions({ pageNo: 1, pageSize: 500 })
  versions.value = data.records
}

async function loadTaskAndSteps(taskId: number) {
  loadingTask.value = true
  try {
    task.value = await api.getUiNlTask(taskId)
    if (task.value?.uiNlCaseId) {
      const c = await api.getUiNlCase(task.value.uiNlCaseId).catch(() => null)
      caseTitleText.value = c?.title || `用例#${task.value.uiNlCaseId}`
    } else {
      caseTitleText.value = ''
    }
  } finally {
    loadingTask.value = false
  }

  loadingSteps.value = true
  try {
    const [plan, exec] = await Promise.all([
      api.getUiNlTaskSteps(taskId, 'PLAN'),
      api.getUiNlTaskSteps(taskId, 'EXEC'),
    ])
    planSteps.value = plan
    execSteps.value = exec
  } catch (e: any) {
    ElMessage.error(e.message || '加载步骤失败')
  } finally {
    loadingSteps.value = false
  }
}

async function refresh() {
  const taskId = Number(route.query.taskId || 0)
  if (!taskId) {
    ElMessage.warning('缺少 taskId')
    return
  }
  await loadTaskAndSteps(taskId)
}

async function executeTask() {
  if (!task.value?.id) return
  if (!canRunUiNlTask.value) {
    const tip = runUiNlTaskBlockReason.value || '当前不可执行'
    ElMessage.warning(tip)
    return
  }
  try {
    await api.runUiNlTask(task.value.id)
    ElMessage.success('已提交执行')
    await refresh()
  } catch (e: any) {
    ElMessage.error(readableError(e, '执行任务失败，请检查 runner 服务'))
  }
}

async function interruptUiNlTask() {
  if (!task.value?.id || !canInterruptUiNlTask.value) {
    ElMessage.warning(interruptUiNlTaskBlockReason.value || '当前不可中断')
    return
  }
  const ok = await ElMessageBox.confirm(
    '确认中断当前执行？将通知 runner 停止浏览器任务（任务将标记为已取消）。',
    '中断任务',
    { type: 'warning', confirmButtonText: '确认中断', cancelButtonText: '取消' },
  ).catch(() => false)
  if (!ok) return
  interruptSubmitting.value = true
  try {
    await api.cancelUiNlTask(task.value.id, '用户中断执行')
    ElMessage.success('已请求停止执行')
    await refresh()
  } catch (e: any) {
    ElMessage.error(readableError(e, '中断失败'))
  } finally {
    interruptSubmitting.value = false
  }
}

async function openStepDetail(row: UiNlStep, phase: 'PLAN' | 'EXEC') {
  revokeScreenshotPreview()
  currentStep.value = await api.getUiNlStep(row.id, phase)
  currentStepPhase.value = phase
  stepDetailVisible.value = true
  if (phase === 'EXEC' && currentStep.value?.screenshotPath) {
    screenshotLoading.value = true
    try {
      const blob = await api.getUiNlExecStepScreenshotBlob(currentStep.value.id)
      screenshotPreviewUrl.value = URL.createObjectURL(blob)
    } catch {
      screenshotPreviewUrl.value = null
    } finally {
      screenshotLoading.value = false
    }
  }
}

const displayedSteps = computed(() => (activePhase.value === 'PLAN' ? planSteps.value : execSteps.value))
const isPlanPhase = computed(() => activePhase.value === 'PLAN')

function hasText(v?: string | null) {
  return !!String(v ?? '').trim()
}

function planStepAt(stepNo?: number) {
  if (!stepNo || stepNo <= 0) return null
  return planSteps.value.find((s) => s.stepNo === stepNo) || null
}

function execExpectSourceLabel(step?: UiNlStep | null) {
  if (!step) return '—'
  const no = Number(step.stepNo || 0)
  if (!no) return '无对应规划步骤'
  const mapped = planStepAt(no)
  if (!mapped) return '无对应规划步骤（执行新增）'
  if (hasText(mapped.expectJson)) return `来自规划第 ${no} 步`
  return `对应规划第 ${no} 步未配置预期`
}

function backToList() {
  router.push('/ui-nl-steps')
}

onMounted(async () => {
  await loadProjects()
  await loadVersions()
  await refresh()
})

onUnmounted(() => {
  revokeScreenshotPreview()
})
</script>

<template>
  <div class="page-shell">
    <el-card>
      <div class="top-bar">
        <div class="top-left">
          <el-button @click="backToList">返回列表</el-button>
          <template v-if="task">
            <el-tag type="info" class="status-tag">步骤生成：{{ taskPlanStatusLabel(task.status) }}</el-tag>
            <el-tag type="warning" class="status-tag">执行：{{ lastExecStatusLabel(task.lastExecStatus) }}</el-tag>
          </template>
        </div>
        <div class="top-actions">
          <el-button type="primary" :loading="loadingTask || loadingSteps" @click="refresh">刷新</el-button>
          <el-tooltip :disabled="canRunUiNlTask" :content="runUiNlTaskBlockReason || '不可执行'" placement="top">
            <span class="run-task-btn-wrap">
              <el-button type="success" :disabled="!task || !canRunUiNlTask" @click="executeTask">
                {{ runUiNlTaskButtonLabel }}
              </el-button>
            </span>
          </el-tooltip>
          <el-tooltip :disabled="canInterruptUiNlTask" :content="interruptUiNlTaskBlockReason" placement="top">
            <span class="run-task-btn-wrap">
              <el-button
                type="danger"
                plain
                :disabled="!task || !canInterruptUiNlTask"
                :loading="interruptSubmitting"
                @click="interruptUiNlTask"
              >
                中断任务
              </el-button>
            </span>
          </el-tooltip>
        </div>
      </div>
    </el-card>

    <el-card v-loading="loadingTask" class="meta-card">
      <el-descriptions v-if="task" :column="3" border>
        <el-descriptions-item label="任务号">{{ task.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ projectLabel(task.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ versionLabel(task.versionId) }}</el-descriptions-item>
        <el-descriptions-item label="用例标题">{{ caseTitleText || `用例#${task.uiNlCaseId}` }}</el-descriptions-item>
        <el-descriptions-item label="规划开始">{{ formatDateTime(task.planStartedAt) }}</el-descriptions-item>
        <el-descriptions-item label="规划结束">{{ formatDateTime(task.planFinishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="执行开始">{{ formatDateTime(task.execStartedAt) }}</el-descriptions-item>
        <el-descriptions-item label="执行结束">{{ formatDateTime(task.execFinishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="错误信息">{{ task.errorMessage || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-else class="empty-hint">未找到任务或 taskId 无效</div>
    </el-card>

    <el-card class="table-card">
      <el-alert
        class="steps-hint"
        type="info"
        show-icon
        :closable="false"
        title="步骤说明"
        description="规划步骤与执行轨迹已分离：规划侧「状态」仅表示步骤是否由模型生成或经人工编辑；执行成败以「执行轨迹」表为准。"
      />
      <el-tabs v-model="activePhase" class="phase-tabs">
        <el-tab-pane label="规划步骤" name="PLAN" />
        <el-tab-pane label="执行轨迹" name="EXEC" />
      </el-tabs>
      <el-table :data="displayedSteps" :size="tableDensity" border stripe v-loading="loadingSteps" height="100%">
        <el-table-column prop="stepNo" label="序号" width="70" />
        <el-table-column prop="stepTitle" label="标题" min-width="140" />
        <el-table-column v-if="!isPlanPhase" prop="actionType" label="动作" width="120" />
        <el-table-column prop="inputValue" label="操作内容" min-width="320" show-overflow-tooltip />
        <el-table-column v-if="isPlanPhase" prop="expectJson" label="预期结果" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="!isPlanPhase" label="预期来源" min-width="170">
          <template #default="{ row }">{{ execExpectSourceLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="isPlanPhase ? '规划状态' : '执行状态'" width="110">
          <template #default="{ row }">{{
            isPlanPhase ? planStepStatusLabel(row.status) : stepStatusLabel(row.status)
          }}</template>
        </el-table-column>
        <el-table-column v-if="!isPlanPhase" prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column v-if="!isPlanPhase" prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" :width="isPlanPhase ? 148 : 96" fixed="right">
          <template #default="{ row }">
            <template v-if="isPlanPhase">
              <el-tooltip :disabled="canEditPlanSteps" :content="planEditBlockedReason || '不可修改'" placement="top">
                <span class="table-actions-inline">
                  <el-button link type="warning" :disabled="!canEditPlanSteps" @click="openPlanEdit(row)">修改</el-button>
                </span>
              </el-tooltip>
              <el-button link type="primary" @click="openStepDetail(row, 'PLAN')">步骤详情</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click="openStepDetail(row, 'EXEC')">步骤详情</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="planEditVisible" title="修改规划步骤" width="640px" destroy-on-close @closed="onPlanEditDialogClosed">
      <el-form label-position="top">
        <el-form-item label="序号（只读）">
          <el-input :model-value="String(planEditForm.stepNo)" disabled />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="planEditForm.stepTitle" maxlength="500" show-word-limit placeholder="步骤标题" />
        </el-form-item>
        <el-form-item label="动作类型（可选）">
          <el-input v-model="planEditForm.actionType" maxlength="120" placeholder="如 PLAN" />
        </el-form-item>
        <el-form-item label="操作内容" required>
          <el-input v-model="planEditForm.inputValue" type="textarea" :rows="4" placeholder="本步要做什么" />
        </el-form-item>
        <el-form-item label="预期结果（JSON，可选）">
          <el-input v-model="planEditForm.expectJson" type="textarea" :rows="4" placeholder='{"expected_result":"..."}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="planEditSaving" :disabled="!canEditPlanSteps" @click="savePlanEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="stepDetailVisible" title="步骤详情" size="960px" @closed="revokeScreenshotPreview">
      <div v-if="currentStep" class="detail-wrap">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="步骤序号">{{ currentStep.stepNo }}</el-descriptions-item>
          <el-descriptions-item label="步骤标题">{{ currentStep.stepTitle || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="currentStepPhase === 'PLAN' ? '规划状态' : '执行状态'">{{
            currentStepPhase === 'PLAN' ? planStepStatusLabel(currentStep.status) : stepStatusLabel(currentStep.status)
          }}</el-descriptions-item>
          <el-descriptions-item v-if="currentStepPhase === 'EXEC'" label="动作类型">{{ currentStep.actionType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="步骤来源">{{ currentStepPhase === 'PLAN' ? '规划步骤' : '执行轨迹' }}</el-descriptions-item>
          <el-descriptions-item label="任务号">{{ task?.taskNo || `任务#${currentStep.taskId}` }}</el-descriptions-item>
          <el-descriptions-item v-if="currentStepPhase === 'EXEC'" label="耗时">{{ currentStep.durationMs ?? '—' }} ms</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <el-form label-position="top">
          <el-form-item label="操作内容"><el-input :model-value="currentStep.inputValue || ''" type="textarea" :rows="2" readonly /></el-form-item>
          <el-form-item label="预期结果"><el-input :model-value="currentStep.expectJson || ''" type="textarea" :rows="4" readonly /></el-form-item>
          <el-form-item v-if="currentStepPhase === 'EXEC'" label="预期来源">
            <el-input :model-value="execExpectSourceLabel(currentStep)" readonly />
          </el-form-item>
          <el-form-item v-if="currentStepPhase === 'EXEC'" label="目标定位信息"><el-input :model-value="currentStep.targetJson || ''" type="textarea" :rows="4" readonly /></el-form-item>
          <el-form-item v-if="currentStepPhase === 'EXEC'" label="原始日志"><el-input :model-value="currentStep.rawLog || ''" type="textarea" :rows="6" readonly /></el-form-item>
          <el-form-item v-if="currentStepPhase === 'EXEC'" label="截图">
            <div v-loading="screenshotLoading" class="shot-block">
              <el-image
                v-if="screenshotPreviewUrl"
                :src="screenshotPreviewUrl"
                fit="contain"
                :preview-src-list="[screenshotPreviewUrl]"
                preview-teleported
                class="shot-preview"
              />
              <div v-else-if="currentStep.screenshotPath" class="shot-miss">无法加载图片（文件可能已删除或 runner 目录不可访问）</div>
              <el-input
                :model-value="currentStep.screenshotPath || ''"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 8 }"
                readonly
                class="shot-path"
              />
            </div>
          </el-form-item>
          <el-form-item v-if="currentStepPhase === 'EXEC'" label="错误信息"><el-input :model-value="currentStep.errorMessage || ''" type="textarea" :rows="2" readonly /></el-form-item>
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-shell { height: 100%; display: grid; grid-template-rows: auto auto 1fr; gap: 12px; }
.top-bar { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.top-left { display: flex; align-items: center; gap: 8px; }
.top-actions { display: inline-flex; align-items: center; gap: 10px; margin-left: auto; }
.run-task-btn-wrap { display: inline-block; vertical-align: middle; }
.table-actions-inline { display: inline-block; margin-right: 4px; vertical-align: middle; }
.status-tag { font-size: 15px; font-weight: 600; }
.meta-card { }
.steps-hint { margin-bottom: 12px; }
.phase-tabs { margin-bottom: 8px; }
.table-card { min-height: 0; display: flex; flex-direction: column; }
.table-card :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.detail-wrap { display: flex; flex-direction: column; gap: 10px; }
.shot-block { display: flex; flex-direction: column; gap: 10px; min-height: 48px; }
.shot-preview { max-width: 100%; max-height: 420px; border: 1px solid var(--el-border-color); border-radius: 6px; }
.shot-path :deep(textarea) { font-family: ui-monospace, monospace; word-break: break-all; }
.shot-miss { color: var(--el-text-color-secondary); font-size: 13px; }
.empty-hint { color: var(--el-text-color-secondary); padding: 8px 0; }
</style>

