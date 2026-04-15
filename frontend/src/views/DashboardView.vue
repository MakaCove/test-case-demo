<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Cpu,
  Document,
  Files,
  FolderOpened,
  Histogram,
  Link,
  Memo,
  RefreshRight,
  Timer,
  TrendCharts,
  WarningFilled,
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

const overviewLoading = ref(false)
const lastRefreshAt = ref<string>('')

const projectTotal = ref(0)
const versionTotal = ref(0)
const assetTotal = ref(0)
const testCaseTotal = ref(0)
const apiCaseTotal = ref(0)
const uiNlCaseTotal = ref(0)
const uiNlTaskTotal = ref(0)

const pendingReviewFn = ref(0)
const pendingReviewApi = ref(0)
const notExecutedFn = ref(0)
const notExecutedApi = ref(0)

const queuedTotal = ref(0)
const pendingTotal = ref(0)
const runningTotal = ref(0)
const completedTaskTotal = ref(0)
const failedTaskTotal = ref(0)
const uiPlanQueuedTotal = ref(0)
const uiPlanPlanningTotal = ref(0)
const uiPlanFailedTotal = ref(0)
const uiExecRunningTotal = ref(0)
const uiExecFailedTotal = ref(0)

const exportQueuedTotal = ref(0)
const exportRunningTotal = ref(0)

const recentTasks = ref<GenerationTask[]>([])
const tasksLoading = ref(false)

const recentExports = ref<ExportRecord[]>([])
const exportsLoading = ref(false)

const recentLogs = ref<OperationLogRow[]>([])
const logsLoading = ref(false)

const projectNameMap = ref<Map<number, string>>(new Map())
const versionNameMap = ref<Map<number, string>>(new Map())

const activeTab = ref<'tasks' | 'exports'>('tasks')

let pollTimer: ReturnType<typeof setInterval> | null = null

function caseCategoryLabel(caseCategory: string | undefined) {
  const c = (caseCategory || 'FUNCTIONAL').trim().toUpperCase()
  if (c === 'API') return '接口'
  return '功能'
}

function caseCategoryTagType(caseCategory: string | undefined) {
  const c = (caseCategory || 'FUNCTIONAL').trim().toUpperCase()
  return c === 'API' ? 'warning' : 'primary'
}

function taskStatusTagType(s: string) {
  return dictStatusTagType(GENERATION_TASK_STATUS, s, 'info')
}

function taskStatusLabel(s: string) {
  return dictStatusLabel(GENERATION_TASK_STATUS, s, '—')
}

function taskRequirementAssetsTooltip(row: GenerationTask): string {
  const list = row.requirementAssets || []
  if (!list.length) {
    return ''
  }
  return list
    .map((a) => {
      const t = (a.title && String(a.title).trim()) || '—'
      return `${a.assetCode} · ${t}`
    })
    .join('\n')
}

function taskRequirementAssetsSummary(row: GenerationTask): string {
  const list = row.requirementAssets || []
  if (!list.length) {
    return '—'
  }
  const first = list[0]
  const t0 = (first.title && String(first.title).trim()) || '—'
  if (list.length === 1) {
    return `${first.assetCode} · ${t0}`
  }
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

async function loadProjectVersionMaps() {
  try {
    const [pRes, vRes] = await Promise.all([
      api.getProjects({ pageNo: 1, pageSize: 500, sortBy: 'id', sortOrder: 'desc' }),
      api.getAllVersions({ pageNo: 1, pageSize: 1000, sortBy: 'id', sortOrder: 'desc' }),
    ])
    const pm = new Map<number, string>()
    for (const p of pRes.records as Project[]) {
      pm.set(p.id, `${p.name}（${p.code}）`)
    }
    projectNameMap.value = pm
    const vm = new Map<number, string>()
    for (const v of vRes.records as Version[]) {
      const label = `${v.versionNo}${v.name ? ' - ' + v.name : ''}`
      vm.set(v.id, label)
    }
    versionNameMap.value = vm
  } catch {
    /* 映射失败时回退 ID */
  }
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const [
      p,
      v,
      assets,
      tc,
      apiTc,
      uiCases,
      uiTasks,
      prFn,
      prApi,
      neFn,
      neApi,
      comp,
      fail,
      exQ,
      exR,
    ] = await Promise.all([
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
    // 把待启动和排队一起当作“待处理”进行展示
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

const primaryCards = computed(() => [
  {
    key: 'projects',
    label: '项目',
    hint: '项目管理',
    value: projectTotal.value,
    path: '/projects',
    icon: FolderOpened,
    accent: 'var(--el-color-primary)',
  },
  {
    key: 'versions',
    label: '版本',
    hint: '版本管理',
    value: versionTotal.value,
    path: '/versions',
    icon: Histogram,
    accent: 'var(--el-color-success)',
  },
  {
    key: 'assets',
    label: '需求资产',
    hint: '需求与文档',
    value: assetTotal.value,
    path: '/assets',
    icon: Files,
    accent: 'var(--el-color-info)',
  },
  {
    key: 'fn',
    label: '功能用例',
    hint: '功能测试用例',
    value: testCaseTotal.value,
    path: '/test-cases',
    icon: Document,
    accent: '#7c3aed',
  },
  {
    key: 'api',
    label: '接口用例',
    hint: '接口测试用例',
    value: apiCaseTotal.value,
    path: '/api-test-cases',
    icon: Link,
    accent: 'var(--el-color-warning)',
  },
  {
    key: 'uiNlCases',
    label: 'UI自然语言用例',
    hint: 'UI自然语言用例库',
    value: uiNlCaseTotal.value,
    path: '/ui-nl-cases',
    icon: Memo,
    accent: '#0ea5e9',
  },
  {
    key: 'uiNlTasks',
    label: 'UI自然语言任务',
    hint: 'UI自然语言任务中心',
    value: uiNlTaskTotal.value,
    path: '/ui-nl-tasks',
    icon: Cpu,
    accent: '#0891b2',
  },
])

const workQueueSummary = computed(() => {
  const parts = [
    `待启动 ${pendingTotal.value}`,
    `排队 ${queuedTotal.value}`,
    `运行中 ${runningTotal.value}`,
    `已完成 ${completedTaskTotal.value}`,
    `失败 ${failedTaskTotal.value}`,
  ]
  return parts.join(' · ')
})

const exportPulseSummary = computed(() => {
  if (exportQueuedTotal.value === 0 && exportRunningTotal.value === 0) return '无进行中的导出'
  return `导出排队 ${exportQueuedTotal.value} · 导出中 ${exportRunningTotal.value}`
})

const uiPulseSummary = computed(() => {
  return `UI生成排队 ${uiPlanQueuedTotal.value} · UI生成中 ${uiPlanPlanningTotal.value} · UI执行中 ${uiExecRunningTotal.value}`
})

const reviewBacklogTotal = computed(() => pendingReviewFn.value + pendingReviewApi.value)
const executionBacklogTotal = computed(() => notExecutedFn.value + notExecutedApi.value)
const activeTaskTotal = computed(() => pendingTotal.value + queuedTotal.value + runningTotal.value)
const uiActiveTotal = computed(() => uiPlanQueuedTotal.value + uiPlanPlanningTotal.value + uiExecRunningTotal.value)
const uiFailureTotal = computed(() => uiPlanFailedTotal.value + uiExecFailedTotal.value)

const generationSuccessRate = computed(() => {
  const finished = completedTaskTotal.value + failedTaskTotal.value
  if (finished <= 0) return '—'
  const ratio = (completedTaskTotal.value / finished) * 100
  return `${ratio.toFixed(1)}%`
})

const deliveryRiskMeta = computed(() => {
  const highRisk =
    failedTaskTotal.value >= 5 ||
    uiFailureTotal.value >= 3 ||
    reviewBacklogTotal.value >= 30 ||
    executionBacklogTotal.value >= 60
  const mediumRisk =
    failedTaskTotal.value > 0 ||
    uiFailureTotal.value > 0 ||
    activeTaskTotal.value >= 10 ||
    uiActiveTotal.value >= 10 ||
    exportRunningTotal.value > 0 ||
    exportQueuedTotal.value > 10

  if (highRisk) {
    return {
      label: '高风险',
      type: 'danger' as const,
      tip: '建议优先处理失败任务与评审积压',
      actionLabel: '处理失败任务',
      actionPath: '/generation-tasks',
      actionQuery: { status: 'FAILED' },
    }
  }
  if (mediumRisk) {
    return {
      label: '关注中',
      type: 'warning' as const,
      tip: '存在在途任务或导出，建议持续跟进',
      actionLabel: '查看在途任务',
      actionPath: '/generation-tasks',
      actionQuery: { status: 'RUNNING' },
    }
  }
  return {
    label: '健康',
    type: 'success' as const,
    tip: '当前流程整体平稳',
    actionLabel: '查看任务中心',
    actionPath: '/generation-tasks',
    actionQuery: {} as Record<string, string>,
  }
})

onMounted(async () => {
  await refreshAll()
  pollTimer = window.setInterval(() => {
    loadTasksPulse()
    loadUiNlPulse()
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div class="dashboard">
    <section class="dash-toolbar">
      <div class="dash-toolbar-left">
        <span v-if="lastRefreshAt" class="dash-updated">数据刷新于 {{ lastRefreshAt }}</span>
      </div>
      <div class="dash-header-actions">
        <el-button type="primary" :loading="overviewLoading" @click="refreshAll">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>
          刷新数据
        </el-button>
        <el-button @click="go('/generation-tasks')">
          <el-icon class="btn-icon"><Cpu /></el-icon>
          任务中心
        </el-button>
        <el-button @click="go('/exports')">
          <el-icon class="btn-icon"><TrendCharts /></el-icon>
          导出中心
        </el-button>
      </div>
    </section>

    <section v-loading="overviewLoading" class="kpi-section">
      <div class="kpi-grid">
        <button
          v-for="c in primaryCards"
          :key="c.key"
          type="button"
          class="kpi-card"
          @click="go(c.path)"
        >
          <div class="kpi-icon-wrap" :style="{ color: c.accent, borderColor: c.accent }">
            <el-icon :size="22"><component :is="c.icon" /></el-icon>
          </div>
          <div class="kpi-body">
            <div class="kpi-label">{{ c.label }}</div>
            <div class="kpi-value">{{ c.value }}</div>
            <div class="kpi-hint">{{ c.hint }}</div>
          </div>
        </button>
      </div>
    </section>

    <section class="insight-strip">
      <div class="insight-strip-inner">
        <div class="insight-chips">
          <button type="button" class="insight-chip insight-chip--warn" @click="go('/test-cases')">
            <el-icon><WarningFilled /></el-icon>
            <span>待评审 · 功能</span>
            <strong>{{ pendingReviewFn }}</strong>
          </button>
          <button type="button" class="insight-chip insight-chip--warn" @click="go('/api-test-cases')">
            <el-icon><WarningFilled /></el-icon>
            <span>待评审 · 接口</span>
            <strong>{{ pendingReviewApi }}</strong>
          </button>
          <button type="button" class="insight-chip" @click="go('/test-cases')">
            <el-icon><Timer /></el-icon>
            <span>未执行 · 功能</span>
            <strong>{{ notExecutedFn }}</strong>
          </button>
          <button type="button" class="insight-chip" @click="go('/api-test-cases')">
            <el-icon><Timer /></el-icon>
            <span>未执行 · 接口</span>
            <strong>{{ notExecutedApi }}</strong>
          </button>
        </div>
        <div class="insight-meta">
          <span class="insight-meta-line">{{ workQueueSummary }}</span>
          <span class="insight-meta-dot">|</span>
          <span class="insight-meta-line">{{ uiPulseSummary }}</span>
          <span class="insight-meta-dot">|</span>
          <span class="insight-meta-line">{{ exportPulseSummary }}</span>
        </div>
      </div>
    </section>

    <section class="focus-section">
      <div class="focus-grid">
        <el-card shadow="never" class="focus-card">
          <div class="focus-head">
            <span class="focus-title">质量门禁</span>
            <el-tag type="warning" effect="plain">优先处理</el-tag>
          </div>
          <div class="focus-metrics">
            <div class="focus-metric">
              <span>待评审</span>
              <strong>{{ reviewBacklogTotal }}</strong>
            </div>
            <div class="focus-metric">
              <span>未执行</span>
              <strong>{{ executionBacklogTotal }}</strong>
            </div>
            <div class="focus-metric">
              <span>生成成功率</span>
              <strong>{{ generationSuccessRate }}</strong>
            </div>
          </div>
          <div class="focus-actions">
            <el-button link type="primary" @click="go('/test-cases', { reviewStatus: 'PENDING' })">处理功能评审</el-button>
            <el-button link type="primary" @click="go('/api-test-cases', { reviewStatus: 'PENDING' })">处理接口评审</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="focus-card">
          <div class="focus-head">
            <span class="focus-title">交付节奏</span>
            <el-tag type="primary" effect="plain">在途监控</el-tag>
          </div>
          <div class="focus-metrics">
            <div class="focus-metric">
              <span>活动任务</span>
              <strong>{{ activeTaskTotal }}</strong>
            </div>
            <div class="focus-metric">
              <span>导出排队</span>
              <strong>{{ exportQueuedTotal }}</strong>
            </div>
            <div class="focus-metric">
              <span>导出进行中</span>
              <strong>{{ exportRunningTotal }}</strong>
            </div>
          </div>
          <div class="focus-actions">
            <el-button link type="primary" @click="go('/generation-tasks')">查看任务中心</el-button>
            <el-button link type="primary" @click="go('/exports')">查看导出中心</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="focus-card">
          <div class="focus-head">
            <span class="focus-title">UI 任务态势</span>
            <el-tag type="primary" effect="plain">UI自然语言</el-tag>
          </div>
          <div class="focus-metrics">
            <div class="focus-metric">
              <span>步骤生成在途</span>
              <strong>{{ uiPlanQueuedTotal + uiPlanPlanningTotal }}</strong>
            </div>
            <div class="focus-metric">
              <span>浏览器执行中</span>
              <strong>{{ uiExecRunningTotal }}</strong>
            </div>
            <div class="focus-metric">
              <span>UI失败待处理</span>
              <strong>{{ uiFailureTotal }}</strong>
            </div>
          </div>
          <div class="focus-actions">
            <el-button link type="primary" @click="go('/ui-nl-tasks', { status: 'PLANNING' })">查看生成中</el-button>
            <el-button link type="primary" @click="go('/ui-nl-steps')">步骤管理</el-button>
            <el-button link type="primary" @click="go('/ui-nl-reports')">测试报告</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="focus-card">
          <div class="focus-head">
            <span class="focus-title">风险预警</span>
            <el-tag :type="deliveryRiskMeta.type" effect="dark">{{ deliveryRiskMeta.label }}</el-tag>
          </div>
          <div class="focus-risk">
            <div class="risk-line">
              <span>失败任务</span>
              <strong>{{ failedTaskTotal }}</strong>
            </div>
            <div class="risk-line">
              <span>已完成任务</span>
              <strong>{{ completedTaskTotal }}</strong>
            </div>
            <p class="risk-tip">{{ deliveryRiskMeta.tip }}</p>
          </div>
          <div class="focus-actions">
            <el-button link type="primary" @click="go(deliveryRiskMeta.actionPath, deliveryRiskMeta.actionQuery)">
              {{ deliveryRiskMeta.actionLabel }}
            </el-button>
          </div>
        </el-card>
      </div>

      <div class="scene-actions">
        <el-button @click="go('/assets')">补充需求资产</el-button>
        <el-button type="primary" @click="go('/generation-tasks')">发起生成任务</el-button>
        <el-button @click="go('/ui-nl-tasks')">推进 UI 自然语言任务</el-button>
        <el-button @click="go('/operation-logs')">查看最近变更</el-button>
      </div>
    </section>

    <el-row :gutter="14" class="dash-main">
      <el-col :xl="16" :lg="16" :md="24" :sm="24" :xs="24">
        <el-card class="main-card" shadow="never">
          <el-tabs v-model="activeTab" class="dash-tabs">
            <el-tab-pane name="tasks">
              <template #label>
                <span class="tab-label">
                  活动任务
                  <el-badge
                    v-if="pendingTotal + queuedTotal + runningTotal > 0"
                    :value="pendingTotal + queuedTotal + runningTotal"
                    class="tab-badge"
                  />
                </span>
              </template>
              <p class="tab-desc">待启动、排队与运行中的用例生成任务，每 5 秒自动刷新；完整列表与操作请进入任务中心页。</p>
              <div v-loading="tasksLoading" class="table-wrap">
                <el-table v-if="recentTasks.length" :data="recentTasks" stripe size="small" class="dash-table">
                  <el-table-column label="状态" width="100">
                    <template #default="{ row }">
                      <el-tag size="small" :type="taskStatusTagType(row.status)">{{ taskStatusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="taskNo" label="任务号" min-width="150" show-overflow-tooltip />
                  <el-table-column label="类型" width="92">
                    <template #default="{ row }">
                      <el-tag size="small" effect="plain" :type="caseCategoryTagType(row.caseCategory)">
                        {{ caseCategoryLabel(row.caseCategory) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="项目 / 版本" min-width="200" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ projectNameMap.get(row.projectId) || `项目 #${row.projectId}` }}
                      ·
                      {{ versionNameMap.get(row.versionId) || `版本 #${row.versionId}` }}
                    </template>
                  </el-table-column>
                  <el-table-column label="需求资产" min-width="200" show-overflow-tooltip>
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
                  <el-table-column label="创建时间" min-width="168">
                    <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column label="更新时间" min-width="168">
                    <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
                  </el-table-column>
                  <el-table-column label="时间" min-width="168">
                    <template #default="{ row }">
                      {{
                        row.status === 'RUNNING'
                          ? formatDateTime(row.startedAt)
                          : formatDateTime(row.submittedAt)
                      }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="88" fixed="right">
                    <template #default>
                      <el-button link type="primary" @click="go('/generation-tasks')">查看</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else description="当前没有待启动、排队或运行中的任务" :image-size="72" />
              </div>
            </el-tab-pane>

            <el-tab-pane label="最近导出" name="exports">
              <p class="tab-desc">导出任务最近记录；失败任务可在导出中心重试。</p>
              <div v-loading="exportsLoading" class="table-wrap">
                <el-table v-if="recentExports.length" :data="recentExports" stripe size="small" class="dash-table">
                  <el-table-column label="状态" width="100">
                    <template #default="{ row }">
                      <el-tag size="small" :type="exportStatusType(row.status)">{{ exportStatusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="exportNo" label="导出单号" min-width="140" show-overflow-tooltip />
                  <el-table-column label="范围" min-width="120" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ row.exportContent || row.scope || '—' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="项目 / 版本" min-width="200" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ projectNameMap.get(row.projectId) || `项目 #${row.projectId}` }}
                      ·
                      {{ versionNameMap.get(row.versionId) || `版本 #${row.versionId}` }}
                    </template>
                  </el-table-column>
                  <el-table-column label="创建时间" min-width="168">
                    <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column label="更新时间" min-width="168">
                    <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="88" fixed="right">
                    <template #default>
                      <el-button link type="primary" @click="go('/exports')">查看</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else description="暂无导出记录" :image-size="72" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>

      <el-col :xl="8" :lg="8" :md="24" :sm="24" :xs="24">
        <el-card class="side-card" shadow="never">
          <template #header>
            <div class="side-card-head">
              <span class="side-card-title">最近操作</span>
              <el-button link type="primary" @click="go('/operation-logs')">全部日志</el-button>
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
                  <div class="log-item-inner">
                    <div class="log-line">
                      <el-tag size="small" effect="plain">{{ actionLabel(row.action) }}</el-tag>
                      <span class="log-object" :title="operationObjectDisplay(row)">{{ operationObjectDisplay(row) }}</span>
                    </div>
                    <div class="log-meta">{{ row.operatorName || 'unknown' }} · {{ formatDateTime(row.createdAt) }}</div>
                  </div>
                </el-tooltip>
              </div>
            </template>
            <el-empty v-else description="暂无操作记录" :image-size="64" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  width: 100%;
  max-width: none;
}

.dash-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 2px 2px 0;
}

.dash-toolbar-left {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
}

.dash-updated {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.dash-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.btn-icon {
  margin-right: 4px;
  vertical-align: middle;
}

.kpi-section {
  min-height: 120px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 12px;
}

.kpi-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  text-align: left;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.15s;
}

.kpi-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}

.kpi-icon-wrap {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid;
  background: var(--el-fill-color-blank);
}

.kpi-body {
  min-width: 0;
}

.kpi-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.kpi-value {
  margin-top: 4px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.kpi-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.insight-strip {
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(135deg, var(--el-fill-color-light) 0%, var(--el-bg-color) 100%);
  padding: 12px 16px;
}

.insight-strip-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.insight-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.insight-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.insight-chip:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-7);
}

.insight-chip strong {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.insight-chip--warn strong {
  color: var(--el-color-warning-dark-2);
}

.insight-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.insight-meta-dot {
  opacity: 0.35;
  user-select: none;
}

.focus-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.focus-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.focus-card :deep(.el-card__body) {
  padding: 14px;
}

.focus-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.focus-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.focus-metrics {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.focus-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.focus-metric span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.focus-metric strong {
  font-size: 17px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.focus-risk {
  margin-top: 12px;
}

.risk-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}

.risk-line strong {
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.risk-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.focus-actions {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.scene-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dash-main {
  width: 100%;
  align-items: stretch;
}

.main-card {
  min-height: 420px;
}

.main-card :deep(.el-card__body) {
  padding-top: 8px;
}

.dash-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge :deep(.el-badge__content) {
  transform: translateY(0) scale(0.85);
}

.tab-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.table-wrap {
  min-height: 200px;
}

.dash-table {
  width: 100%;
}

.side-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.side-card :deep(.el-card__body) {
  padding: 8px 12px 14px;
  max-height: 420px;
  overflow: auto;
}

.side-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.side-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.log-item {
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.log-item:last-child {
  border-bottom: none;
}

.log-item-inner {
  padding: 10px 4px;
  cursor: default;
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
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}

.log-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.dash-asset-tooltip {
  white-space: pre-line;
  max-width: 360px;
}

@media (max-width: 1280px) {
  .focus-grid {
    grid-template-columns: 1fr;
  }
}
</style>
