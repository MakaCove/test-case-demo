<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Cpu, Document, Files, FolderOpened, Histogram, Link, Memo,
  RefreshRight, Timer, TrendCharts, WarningFilled, Tickets,
  CircleCheck,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, type ExportRecord, type GenerationTask, type Project, type Version } from '../api/api'
import { actionLabel, operationObjectDisplay, type OperationLogRow } from '../utils/operationLogDisplay'
import { formatDateTime } from '../utils/formatDateTime'
import {
  EXPORT_STATUS,
  GENERATION_TASK_STATUS,
  statusLabel as dictStatusLabel,
  statusTagType as dictStatusTagType,
} from '../utils/statusDictionary'

const router = useRouter()

// ── 加载状态 ──
const overviewLoading = ref(false)
const tasksLoading = ref(false)
const exportsLoading = ref(false)
const logsLoading = ref(false)
const lastRefreshAt = ref<string>('')

// ── 总览计数 ──
const projectTotal = ref(0)
const versionTotal = ref(0)
const assetTotal = ref(0)
const testCaseTotal = ref(0)
const apiCaseTotal = ref(0)
const uiNlCaseTotal = ref(0)
const uiNlTaskTotal = ref(0)

// ── 质量积压 ──
const pendingReviewFn = ref(0)
const pendingReviewApi = ref(0)
const notExecutedFn = ref(0)
const notExecutedApi = ref(0)

// ── 任务脉冲 ──
const pendingTotal = ref(0)
const queuedTotal = ref(0)
const runningTotal = ref(0)
const completedTaskTotal = ref(0)
const failedTaskTotal = ref(0)

// ── UI 任务脉冲 ──
const uiPlanQueuedTotal = ref(0)
const uiPlanPlanningTotal = ref(0)
const uiPlanFailedTotal = ref(0)
const uiExecRunningTotal = ref(0)
const uiExecFailedTotal = ref(0)

// ── 导出脉冲 ──
const exportQueuedTotal = ref(0)
const exportRunningTotal = ref(0)

// ── 列表数据 ──
const recentTasks = ref<GenerationTask[]>([])
const recentExports = ref<ExportRecord[]>([])
const recentLogs = ref<OperationLogRow[]>([])

// ── 名称映射 ──
const projectNameMap = ref<Map<number, string>>(new Map())
const versionNameMap = ref<Map<number, string>>(new Map())

const activeTab = ref<'tasks' | 'exports'>('tasks')
let pollTimer: ReturnType<typeof setInterval> | null = null

// ── 用户欢迎信息 ──
const loginUserName = computed(() => {
  const raw = localStorage.getItem('userInfo')
  if (!raw) return '用户'
  try {
    const parsed = JSON.parse(raw) as { displayName?: string; username?: string }
    return parsed.displayName || parsed.username || '用户'
  } catch {
    return '用户'
  }
})

// ── 工具函数 ──
function caseCategoryLabel(caseCategory: string | undefined) {
  return (caseCategory || 'FUNCTIONAL').trim().toUpperCase() === 'API' ? '接口' : '功能'
}

function caseCategoryTagType(caseCategory: string | undefined) {
  return (caseCategory || 'FUNCTIONAL').trim().toUpperCase() === 'API' ? 'warning' : 'primary'
}

function taskStatusTagType(s: string) {
  return dictStatusTagType(GENERATION_TASK_STATUS, s, 'info')
}

function taskStatusLabel(s: string) {
  return dictStatusLabel(GENERATION_TASK_STATUS, s, '—')
}

function taskRequirementAssetsTooltip(row: GenerationTask): string {
  const list = row.requirementAssets || []
  return list.map((a) => `${a.assetCode} · ${(a.title && String(a.title).trim()) || '—'}`).join('\n')
}

function taskRequirementAssetsSummary(row: GenerationTask): string {
  const list = row.requirementAssets || []
  if (!list.length) return '—'
  const first = list[0]
  const t0 = (first.title && String(first.title).trim()) || '—'
  if (list.length === 1) return `${first.assetCode} · ${t0}`
  return `${first.assetCode} · ${t0} 等 ${list.length} 条`
}

function exportStatusLabel(s: string | undefined) {
  return dictStatusLabel(EXPORT_STATUS, s, '—')
}

function exportStatusType(s: string | undefined) {
  return dictStatusTagType(EXPORT_STATUS, s, 'info')
}

function mergeRecentTasks(queued: GenerationTask[], running: GenerationTask[]) {
  const merged = [...running, ...queued]
  merged.sort((a, b) => {
    const ta = a.submittedAt ? Date.parse(a.submittedAt) : 0
    const tb = b.submittedAt ? Date.parse(b.submittedAt) : 0
    return tb - ta
  })
  return merged.slice(0, 12)
}

function touchRefreshTime() {
  lastRefreshAt.value = formatDateTime(new Date().toISOString())
}

function go(path: string, query?: Record<string, string>) {
  router.push(query && Object.keys(query).length ? { path, query } : path)
}

// ── 数据加载 ──
async function loadProjectVersionMaps() {
  try {
    const [pRes, vRes] = await Promise.all([
      api.getProjects({ pageNo: 1, pageSize: 500, sortBy: 'id', sortOrder: 'desc' }),
      api.getAllVersions({ pageNo: 1, pageSize: 1000, sortBy: 'id', sortOrder: 'desc' }),
    ])
    const pm = new Map<number, string>()
    for (const p of pRes.records as Project[]) pm.set(p.id, `${p.name}（${p.code}）`)
    projectNameMap.value = pm
    const vm = new Map<number, string>()
    for (const v of vRes.records as Version[]) {
      vm.set(v.id, `${v.versionNo}${v.name ? ' - ' + v.name : ''}`)
    }
    versionNameMap.value = vm
  } catch { /* 映射失败时回退 ID */ }
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const [p, v, assets, tc, apiTc, uiCases, uiTasks, prFn, prApi, neFn, neApi, comp, fail, exQ, exR] =
      await Promise.all([
        api.getProjects({ pageNo: 1, pageSize: 1 }),
        api.getAllVersions({ pageNo: 1, pageSize: 1 }),
        api.getAllAssets({ pageNo: 1, pageSize: 1 }),
        api.getTestCases({ pageNo: 1, pageSize: 1 }),
        api.getApiTestCases({ pageNo: 1, pageSize: 1 }),
        api.getUiNlCases({ pageNo: 1, pageSize: 1 }),
        api.getUiNlTasks({ pageNo: 1, pageSize: 1 }),
        api.getTestCases({ pageNo: 1, pageSize: 1, reviewStatus: 'PENDING' }),
        api.getApiTestCases({ pageNo: 1, pageSize: 1, reviewStatus: 'PENDING' }),
        api.getTestCases({ pageNo: 1, pageSize: 1, executionStatus: 'NOT_EXECUTED' }),
        api.getApiTestCases({ pageNo: 1, pageSize: 1, executionStatus: 'NOT_EXECUTED' }),
        api.getGenerationTasks({ status: 'COMPLETED', pageNo: 1, pageSize: 1 }),
        api.getGenerationTasks({ status: 'FAILED', pageNo: 1, pageSize: 1 }),
        api.getExportRecords({ pageNo: 1, pageSize: 1, status: 'QUEUED' }),
        api.getExportRecords({ pageNo: 1, pageSize: 1, status: 'RUNNING' }),
      ])
    projectTotal.value = p.total
    versionTotal.value = v.total
    assetTotal.value = assets.total
    testCaseTotal.value = tc.total
    apiCaseTotal.value = apiTc.total
    uiNlCaseTotal.value = uiCases.total
    uiNlTaskTotal.value = uiTasks.total
    pendingReviewFn.value = prFn.total
    pendingReviewApi.value = prApi.total
    notExecutedFn.value = neFn.total
    notExecutedApi.value = neApi.total
    completedTaskTotal.value = comp.total
    failedTaskTotal.value = fail.total
    exportQueuedTotal.value = exQ.total
    exportRunningTotal.value = exR.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    overviewLoading.value = false
  }
}

async function loadTasksPulse() {
  tasksLoading.value = true
  try {
    const [p1, q1, r1, p8, q8, r8] = await Promise.all([
      api.getGenerationTasks({ status: 'PENDING', pageNo: 1, pageSize: 1 }),
      api.getGenerationTasks({ status: 'QUEUED', pageNo: 1, pageSize: 1 }),
      api.getGenerationTasks({ status: 'RUNNING', pageNo: 1, pageSize: 1 }),
      api.getGenerationTasks({ status: 'PENDING', pageNo: 1, pageSize: 8 }),
      api.getGenerationTasks({ status: 'QUEUED', pageNo: 1, pageSize: 8 }),
      api.getGenerationTasks({ status: 'RUNNING', pageNo: 1, pageSize: 8 }),
    ])
    pendingTotal.value = p1.total
    queuedTotal.value = q1.total
    runningTotal.value = r1.total
    recentTasks.value = mergeRecentTasks([...p8.records, ...q8.records], r8.records)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    tasksLoading.value = false
  }
}

async function loadUiNlPulse() {
  try {
    const [queued, planning, planFailed, execRunning, execFailed] = await Promise.all([
      api.getUiNlTasks({ status: 'QUEUED', pageNo: 1, pageSize: 1 }),
      api.getUiNlTasks({ status: 'PLANNING', pageNo: 1, pageSize: 1 }),
      api.getUiNlTasks({ status: 'FAILED', pageNo: 1, pageSize: 1 }),
      api.getUiNlTasks({ lastExecStatus: 'RUNNING', pageNo: 1, pageSize: 1 }),
      api.getUiNlTasks({ lastExecStatus: 'FAILED', pageNo: 1, pageSize: 1 }),
    ])
    uiPlanQueuedTotal.value = queued.total
    uiPlanPlanningTotal.value = planning.total
    uiPlanFailedTotal.value = planFailed.total
    uiExecRunningTotal.value = execRunning.total
    uiExecFailedTotal.value = execFailed.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function loadRecentExports() {
  exportsLoading.value = true
  try {
    const data = await api.getExportRecords({ pageNo: 1, pageSize: 8 })
    recentExports.value = data.records
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    exportsLoading.value = false
  }
}

async function loadRecentChanges() {
  logsLoading.value = true
  try {
    const data = await api.getOperationLogs({ pageNo: 1, pageSize: 12 })
    recentLogs.value = data.records as OperationLogRow[]
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    logsLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([
    loadOverview(),
    loadProjectVersionMaps(),
    loadRecentChanges(),
    loadTasksPulse(),
    loadUiNlPulse(),
    loadRecentExports(),
  ])
  touchRefreshTime()
}

// ── 计算属性 ──
const primaryCards = computed(() => [
  { key: 'projects',   label: '项目',      value: projectTotal.value,   path: '/projects',       icon: FolderOpened, accent: 'var(--el-color-primary)' },
  { key: 'versions',   label: '版本',      value: versionTotal.value,   path: '/versions',       icon: Histogram,    accent: 'var(--el-color-success)' },
  { key: 'assets',     label: '需求资产',  value: assetTotal.value,     path: '/assets',         icon: Files,        accent: 'var(--el-color-info)' },
  { key: 'fn',         label: '功能用例',  value: testCaseTotal.value,  path: '/test-cases',     icon: Tickets,      accent: '#7c3aed' },
  { key: 'api',        label: '接口用例',  value: apiCaseTotal.value,   path: '/api-test-cases', icon: Link,         accent: 'var(--el-color-warning)' },
  { key: 'uiNlCases',  label: 'UI 用例',   value: uiNlCaseTotal.value,  path: '/ui-nl-cases',    icon: Memo,         accent: '#0ea5e9' },
  { key: 'uiNlTasks',  label: 'UI 任务',   value: uiNlTaskTotal.value,  path: '/ui-nl-tasks',    icon: Cpu,          accent: '#0891b2' },
])

const reviewBacklogTotal = computed(() => pendingReviewFn.value + pendingReviewApi.value)
const executionBacklogTotal = computed(() => notExecutedFn.value + notExecutedApi.value)
const activeTaskTotal = computed(() => pendingTotal.value + queuedTotal.value + runningTotal.value)
const uiActiveTotal = computed(() => uiPlanQueuedTotal.value + uiPlanPlanningTotal.value + uiExecRunningTotal.value)
const uiFailureTotal = computed(() => uiPlanFailedTotal.value + uiExecFailedTotal.value)

const generationSuccessRate = computed(() => {
  const finished = completedTaskTotal.value + failedTaskTotal.value
  if (finished <= 0) return '—'
  return `${((completedTaskTotal.value / finished) * 100).toFixed(1)}%`
})

// ── 轮询（Tab 隐藏时暂停）──
function startPolling() {
  if (pollTimer !== null) return
  pollTimer = window.setInterval(() => {
    if (!document.hidden) {
      loadTasksPulse()
      loadUiNlPulse()
    }
  }, 5000)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function onVisibilityChange() {
  if (document.hidden) stopPolling()
  else startPolling()
}

onMounted(async () => {
  await refreshAll()
  startPolling()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="dashboard-rebuild">
    <section class="command-hero">
      <div class="hero-left">
        <h1>{{ loginUserName }}，欢迎进入 AI 测试平台</h1>
        <span v-if="lastRefreshAt" class="hero-sync">最近同步：{{ lastRefreshAt }}</span>
      </div>
      <div class="hero-right">
        <div class="hero-buttons">
          <el-button size="small" :loading="overviewLoading" @click="refreshAll">
            <el-icon class="btn-icon"><RefreshRight /></el-icon>
            刷新面板
          </el-button>
          <el-button size="small" type="primary" @click="go('/generation-tasks')">
            <el-icon class="btn-icon"><Cpu /></el-icon>
            发起任务
          </el-button>
          <el-button size="small" @click="go('/exports')">
            <el-icon class="btn-icon"><TrendCharts /></el-icon>
            导出中心
          </el-button>
        </div>
      </div>
    </section>

    <section v-loading="overviewLoading" class="overview-grid">
      <button
        v-for="c in primaryCards"
        :key="c.key"
        class="overview-card"
        type="button"
        @click="go(c.path)"
      >
        <div class="card-headline">
          <span>{{ c.label }}</span>
          <span class="overview-icon" :style="{ color: c.accent, background: c.accent + '18' }">
            <el-icon><component :is="c.icon" /></el-icon>
          </span>
        </div>
        <div class="card-number">{{ c.value }}</div>
      </button>
    </section>

    <section class="delivery-strip">
      <button class="strip-item strip-item--warn" @click="go('/test-cases', { reviewStatus: 'PENDING' })">
        <div class="strip-title"><el-icon><WarningFilled /></el-icon> 待评审</div>
        <div class="strip-value">{{ reviewBacklogTotal }}</div>
        <div class="strip-desc">功能 {{ pendingReviewFn }} · 接口 {{ pendingReviewApi }}</div>
      </button>
      <button class="strip-item strip-item--info" @click="go('/test-cases', { executionStatus: 'NOT_EXECUTED' })">
        <div class="strip-title"><el-icon><Timer /></el-icon> 未执行</div>
        <div class="strip-value">{{ executionBacklogTotal }}</div>
        <div class="strip-desc">功能 {{ notExecutedFn }} · 接口 {{ notExecutedApi }}</div>
      </button>
      <div class="strip-item strip-item--ok">
        <div class="strip-title"><el-icon><CircleCheck /></el-icon> 任务成功率</div>
        <div class="strip-value">{{ generationSuccessRate }}</div>
        <div class="strip-desc">完成 {{ completedTaskTotal }} · 失败 {{ failedTaskTotal }}</div>
      </div>
      <button class="strip-item strip-item--risk" @click="go('/generation-tasks')">
        <div class="strip-title">任务中心</div>
        <div class="strip-value strip-value--text">查看任务进展</div>
        <div class="strip-desc">统一查看在途、完成与失败任务</div>
      </button>
    </section>

    <el-row :gutter="16" class="content-row">
      <el-col :xl="16" :lg="15" :md="24">
        <el-card shadow="never" class="center-panel">
          <template #header>
            <div class="panel-header">
              <div>
                <div class="panel-title">任务与导出动态</div>
                <div class="panel-sub">查看在途任务、导出队列和最新状态变化</div>
              </div>
            </div>
          </template>

          <el-tabs v-model="activeTab" class="center-tabs">
            <el-tab-pane name="tasks">
              <template #label>
                <span class="tab-label">
                  任务流
                  <el-badge v-if="activeTaskTotal > 0" :value="activeTaskTotal" />
                </span>
              </template>
              <div class="tab-toolbar">
                <div class="toolbar-stats">
                  <span>待启动 <strong>{{ pendingTotal }}</strong></span>
                  <span>排队 <strong>{{ queuedTotal }}</strong></span>
                  <span>运行中 <strong>{{ runningTotal }}</strong></span>
                </div>
                <el-button link type="primary" size="small" @click="go('/generation-tasks')">进入任务中心</el-button>
              </div>
              <div v-loading="tasksLoading" class="table-wrap">
                <el-table v-if="recentTasks.length" :data="recentTasks" stripe size="small">
                  <el-table-column label="状态" width="96">
                    <template #default="{ row }">
                      <el-tag size="small" :type="taskStatusTagType(row.status)">{{ taskStatusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="类型" width="82">
                    <template #default="{ row }">
                      <el-tag size="small" effect="plain" :type="caseCategoryTagType(row.caseCategory)">
                        {{ caseCategoryLabel(row.caseCategory) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="taskNo" label="任务号" min-width="140" show-overflow-tooltip />
                  <el-table-column label="项目 / 版本" min-width="180" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ projectNameMap.get(row.projectId) || `项目 #${row.projectId}` }} ·
                      {{ versionNameMap.get(row.versionId) || `版本 #${row.versionId}` }}
                    </template>
                  </el-table-column>
                  <el-table-column label="需求资产" min-width="180" show-overflow-tooltip>
                    <template #default="{ row }">
                      <template v-if="(row.requirementAssets?.length || 0) > 1">
                        <el-tooltip placement="top">
                          <template #content>
                            <div class="dash-asset-tooltip">{{ taskRequirementAssetsTooltip(row) }}</div>
                          </template>
                          <span>{{ taskRequirementAssetsSummary(row) }}</span>
                        </el-tooltip>
                      </template>
                      <span v-else>{{ taskRequirementAssetsSummary(row) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="提交时间" min-width="150">
                    <template #default="{ row }">
                      {{ formatDateTime(row.status === 'RUNNING' ? row.startedAt : row.submittedAt) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="70" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="go('/generation-tasks', { taskNo: row.taskNo })">查看</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else description="当前没有在途任务" :image-size="64" />
              </div>
            </el-tab-pane>

            <el-tab-pane name="exports" label="导出流">
              <div class="tab-toolbar">
                <div class="toolbar-stats">
                  <span>排队 <strong>{{ exportQueuedTotal }}</strong></span>
                  <span>进行中 <strong>{{ exportRunningTotal }}</strong></span>
                </div>
                <el-button link type="primary" size="small" @click="go('/exports')">进入导出中心</el-button>
              </div>
              <div v-loading="exportsLoading" class="table-wrap">
                <el-table v-if="recentExports.length" :data="recentExports" stripe size="small">
                  <el-table-column label="状态" width="96">
                    <template #default="{ row }">
                      <el-tag size="small" :type="exportStatusType(row.status)">{{ exportStatusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="exportNo" label="导出单号" min-width="130" show-overflow-tooltip />
                  <el-table-column label="范围" min-width="120" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.exportContent || row.scope || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="项目 / 版本" min-width="180" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ projectNameMap.get(row.projectId) || `项目 #${row.projectId}` }} ·
                      {{ versionNameMap.get(row.versionId) || `版本 #${row.versionId}` }}
                    </template>
                  </el-table-column>
                  <el-table-column label="创建时间" min-width="150">
                    <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="70" fixed="right">
                    <template #default>
                      <el-button link type="primary" @click="go('/exports')">查看</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else description="暂无导出记录" :image-size="64" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>

      <el-col :xl="8" :lg="9" :md="24">
        <div class="right-stack">
          <el-card shadow="never" class="status-card">
            <template #header>
              <div class="panel-header">
                <div class="panel-title">UI 执行态势</div>
                <el-button link type="primary" size="small" @click="go('/ui-nl-tasks')">查看全部</el-button>
              </div>
            </template>
            <div class="status-grid">
              <div class="status-item">
                <span>步骤生成中</span>
                <strong>{{ uiPlanQueuedTotal + uiPlanPlanningTotal }}</strong>
              </div>
              <div class="status-item">
                <span>浏览器执行中</span>
                <strong>{{ uiExecRunningTotal }}</strong>
              </div>
              <div class="status-item" :class="{ danger: uiFailureTotal > 0 }">
                <span>失败待处理</span>
                <strong>{{ uiFailureTotal }}</strong>
              </div>
              <div class="status-item">
                <span>导出排队</span>
                <strong>{{ exportQueuedTotal }}</strong>
              </div>
            </div>
            <div class="status-actions">
              <el-button size="small" @click="go('/ui-nl-steps')">步骤管理</el-button>
              <el-button size="small" @click="go('/ui-nl-reports')">测试报告</el-button>
            </div>
          </el-card>

          <el-card shadow="never" class="quick-card">
            <template #header>
              <div class="panel-title">快捷入口</div>
            </template>
            <div class="quick-grid">
              <button type="button" class="quick-item" @click="go('/assets')"><el-icon><Files /></el-icon><span>需求资产库</span></button>
              <button type="button" class="quick-item quick-item--primary" @click="go('/generation-tasks')"><el-icon><Cpu /></el-icon><span>用例生成任务</span></button>
              <button type="button" class="quick-item" @click="go('/projects')"><el-icon><FolderOpened /></el-icon><span>项目管理</span></button>
              <button type="button" class="quick-item" @click="go('/versions')"><el-icon><Histogram /></el-icon><span>版本管理</span></button>
              <button type="button" class="quick-item" @click="go('/api-test-cases')"><el-icon><Link /></el-icon><span>接口测试用例</span></button>
              <button type="button" class="quick-item" @click="go('/ui-nl-cases')"><el-icon><Memo /></el-icon><span>UI 用例库</span></button>
            </div>
          </el-card>

          <el-card shadow="never" class="log-card">
            <template #header>
              <div class="panel-header">
                <div class="panel-title">最近操作</div>
                <el-button link type="primary" size="small" @click="go('/operation-logs')">全部日志</el-button>
              </div>
            </template>
            <div v-loading="logsLoading" class="log-list">
              <template v-if="recentLogs.length">
                <div v-for="row in recentLogs" :key="row.id" class="log-item">
                  <el-tooltip placement="left" :show-after="300">
                    <template #content>
                      <div>对象 {{ row.objectType }}</div>
                      <div v-if="row.objectId != null">ID {{ row.objectId }}</div>
                      <div v-if="row.remark">备注 {{ row.remark }}</div>
                    </template>
                    <div class="log-body">
                      <div class="log-line">
                        <el-tag size="small" effect="plain">{{ actionLabel(row.action) }}</el-tag>
                        <span class="log-object" :title="operationObjectDisplay(row)">{{ operationObjectDisplay(row) }}</span>
                      </div>
                      <div class="log-meta">
                        {{ row.operatorName || 'unknown' }} · {{ formatDateTime(row.createdAt) }}
                      </div>
                    </div>
                  </el-tooltip>
                </div>
              </template>
              <el-empty v-else description="暂无操作记录" :image-size="52" />
            </div>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard-rebuild {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
}

.command-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding: 18px 20px 16px;
  border-radius: var(--app-radius-md);
  border: 1px solid var(--app-card-border);
  background: linear-gradient(135deg, #f7faff 0%, #f0f6ff 44%, #ffffff 100%);
  box-shadow: var(--app-shadow-sm);
}

.hero-left {
  min-width: 360px;
  flex: 1;
}

.hero-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #2f76e4;
  background: #e9f2ff;
  margin-bottom: 8px;
}

.hero-left h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
  color: var(--app-text-primary);
}

.hero-sync {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

.hero-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  margin-left: auto;
}

.hero-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-icon {
  margin-right: 4px;
  vertical-align: middle;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(155px, 1fr));
  gap: 10px;
}

.overview-card {
  padding: 13px 14px;
  border: 1px solid var(--app-card-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  text-align: left;
}

.overview-card:hover {
  border-color: #bfd4f5;
  box-shadow: var(--app-shadow-sm);
  transform: translateY(-2px);
}

.card-headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--app-text-muted);
}

.overview-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.card-number {
  margin-top: 8px;
  font-size: 26px;
  line-height: 1;
  font-weight: 700;
  color: var(--app-text-primary);
  font-variant-numeric: tabular-nums;
}

.delivery-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
}

.strip-item {
  border: 1px solid var(--app-card-border);
  border-radius: var(--app-radius-sm);
  background: #fff;
  padding: 14px;
  text-align: left;
  transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.strip-item:hover {
  border-color: #bfd4f5;
  box-shadow: var(--app-shadow-sm);
  transform: translateY(-1px);
}

.strip-item--warn { background: linear-gradient(180deg, #fff8ee 0%, #fff 100%); }
.strip-item--info { background: linear-gradient(180deg, #eef6ff 0%, #fff 100%); }
.strip-item--ok { background: linear-gradient(180deg, #eefbf4 0%, #fff 100%); }
.strip-item--risk { background: linear-gradient(180deg, #f6efff 0%, #fff 100%); }

.strip-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.strip-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: var(--app-text-primary);
  line-height: 1;
}

.strip-value--text {
  font-size: 16px;
  line-height: 1.3;
}

.strip-desc {
  margin-top: 8px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.content-row {
  width: 100%;
  align-items: flex-start;
}

.right-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.center-panel {
  border-radius: var(--app-radius-md);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--app-text-primary);
}

.panel-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.center-panel :deep(.el-card__header) {
  border-bottom: 1px solid var(--app-card-border);
  padding: 14px 16px;
}

.center-panel :deep(.el-card__body) {
  padding: 10px 16px 16px;
}

.center-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge :deep(.el-badge__content) {
  transform: translateY(0) scale(0.85);
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--app-card-border);
  margin-bottom: 8px;
}

.toolbar-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.toolbar-stats span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.toolbar-stats strong {
  font-variant-numeric: tabular-nums;
  color: var(--app-text-primary);
  font-weight: 700;
}

.table-wrap {
  min-height: 180px;
}

.dash-table {
  width: 100%;
}

.dash-asset-tooltip {
  white-space: pre-line;
  max-width: 360px;
}

.status-card,
.quick-card,
.log-card {
  border-radius: var(--app-radius-md);
}

.status-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.status-item {
  border: 1px solid var(--app-card-border);
  border-radius: var(--app-radius-sm);
  background: #fff;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-item span {
  font-size: 12px;
  color: var(--app-text-muted);
}

.status-item strong {
  font-size: 21px;
  line-height: 1;
  color: var(--app-text-primary);
}

.status-item.danger strong {
  color: var(--el-color-danger);
}

.status-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.quick-item {
  border: 1px solid var(--app-card-border);
  border-radius: var(--app-radius-sm);
  background: #fff;
  padding: 10px 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.quick-item:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  background: #f6faff;
}

.quick-item--primary {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.log-card :deep(.el-card__body) {
  padding: 10px 14px 12px;
  max-height: 360px;
  overflow: auto;
}

.log-list {
  display: flex;
  flex-direction: column;
}

.log-item {
  border-bottom: 1px dashed var(--app-card-border);
}

.log-item:last-child {
  border-bottom: none;
}

.log-body {
  padding: 10px 3px;
}

.log-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-object {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}

.log-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--app-text-muted);
}

@media (max-width: 1366px) {
  .hero-left h1 {
    font-size: 24px;
  }
}

@media (max-width: 768px) {
  .command-hero {
    padding: 14px;
  }

  .hero-left h1 {
    font-size: 22px;
  }

  .hero-right {
    align-items: flex-start;
    margin-left: 0;
  }

  .hero-buttons {
    flex-wrap: wrap;
  }

  .tab-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .toolbar-stats {
    flex-wrap: wrap;
  }

  .status-grid,
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
