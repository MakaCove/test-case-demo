<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
const steps = ref<UiNlStep[]>([])

const stepDetailVisible = ref(false)
const currentStep = ref<UiNlStep | null>(null)

const taskStatus = computed(() => task.value?.status || '')

function statusLabel(statusValue?: string) {
  if (!statusValue) return '—'
  const s = statusValue.trim().toUpperCase()
  if (s === 'PENDING') return '待启动'
  if (s === 'QUEUED') return '排队'
  if (s === 'PLANNING') return '生成中'
  if (s === 'READY') return '待执行'
  if (s === 'RUNNING') return '执行中'
  if (s === 'COMPLETED') return '完成'
  if (s === 'FAILED') return '失败'
  if (s === 'INTERRUPTED') return '中断'
  if (s === 'CANCELLED') return '取消'
  return statusValue
}

function stepStatusLabel(statusValue?: string) {
  if (!statusValue) return '—'
  const s = statusValue.trim().toUpperCase()
  if (s === 'PENDING') return '待执行'
  if (s === 'RUNNING') return '执行中'
  if (s === 'SUCCESS' || s === 'COMPLETED') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'CANCELLED') return '取消'
  return statusValue
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
    steps.value = await api.getUiNlTaskSteps(taskId)
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
  if (!['READY', 'FAILED', 'CANCELLED'].includes(taskStatus.value)) {
    ElMessage.warning('当前状态不允许执行，请先生成步骤')
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

async function openStepDetail(row: UiNlStep) {
  currentStep.value = await api.getUiNlStep(row.id)
  stepDetailVisible.value = true
}

function backToList() {
  router.push('/ui-nl-steps')
}

onMounted(async () => {
  await loadProjects()
  await loadVersions()
  await refresh()
})
</script>

<template>
  <div class="page-shell">
    <el-card>
      <div class="top-bar">
        <div class="top-left">
          <el-button @click="backToList">返回列表</el-button>
          <el-tag v-if="task" type="info" class="status-tag">状态：{{ statusLabel(task.status) }}</el-tag>
        </div>
        <div class="top-actions">
          <el-button type="primary" :loading="loadingTask || loadingSteps" @click="refresh">刷新</el-button>
          <el-button type="success" :disabled="!task" @click="executeTask">执行任务</el-button>
        </div>
      </div>
    </el-card>

    <el-card v-loading="loadingTask" class="meta-card">
      <el-descriptions v-if="task" :column="3" border>
        <el-descriptions-item label="任务号">{{ task.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ projectLabel(task.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ versionLabel(task.versionId) }}</el-descriptions-item>
        <el-descriptions-item label="用例标题">{{ caseTitleText || `用例#${task.uiNlCaseId}` }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ formatDateTime(task.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="错误信息">{{ task.errorMessage || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-else class="empty-hint">未找到任务或 taskId 无效</div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="steps" :size="tableDensity" border stripe v-loading="loadingSteps" height="100%">
        <el-table-column prop="stepNo" label="序号" width="70" />
        <el-table-column prop="stepTitle" label="标题" min-width="140" />
        <el-table-column prop="actionType" label="动作" width="120" />
        <el-table-column prop="inputValue" label="操作内容" min-width="320" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
        <el-table-column label="开始" width="170">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束" width="170">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openStepDetail(row)">步骤详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="stepDetailVisible" title="步骤详情" size="960px">
      <div v-if="currentStep" class="detail-wrap">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="步骤序号">{{ currentStep.stepNo }}</el-descriptions-item>
          <el-descriptions-item label="步骤标题">{{ currentStep.stepTitle || '—' }}</el-descriptions-item>
          <el-descriptions-item label="步骤状态">{{ stepStatusLabel(currentStep.status) }}</el-descriptions-item>
          <el-descriptions-item label="动作类型">{{ currentStep.actionType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="任务号">{{ task?.taskNo || `任务#${currentStep.taskId}` }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ currentStep.durationMs ?? '—' }} ms</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <el-form label-position="top">
          <el-form-item label="目标定位信息"><el-input :model-value="currentStep.targetJson || ''" type="textarea" :rows="4" readonly /></el-form-item>
          <el-form-item label="操作内容"><el-input :model-value="currentStep.inputValue || ''" type="textarea" :rows="2" readonly /></el-form-item>
          <el-form-item label="预期结果"><el-input :model-value="currentStep.expectJson || ''" type="textarea" :rows="4" readonly /></el-form-item>
          <el-form-item label="原始日志"><el-input :model-value="currentStep.rawLog || ''" type="textarea" :rows="6" readonly /></el-form-item>
          <el-form-item label="截图路径"><el-input :model-value="currentStep.screenshotPath || ''" readonly /></el-form-item>
          <el-form-item label="错误信息"><el-input :model-value="currentStep.errorMessage || ''" type="textarea" :rows="2" readonly /></el-form-item>
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
.status-tag { font-size: 15px; font-weight: 600; }
.meta-card { }
.table-card { min-height: 0; display: flex; flex-direction: column; }
.table-card :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.detail-wrap { display: flex; flex-direction: column; gap: 10px; }
.empty-hint { color: var(--el-text-color-secondary); padding: 8px 0; }
</style>

