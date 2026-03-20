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
  RefreshRight,
  Timer,
  TrendCharts,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, type ExportRecord, type GenerationTask, type Project, type Version } from '../api/api'
import { actionLabel, operationObjectDisplay, type OperationLogRow } from '../utils/operationLogDisplay'
import { formatDateTime } from '../utils/formatDateTime'

const router = useRouter()

const overviewLoading = ref(false)
const lastRefreshAt = ref<string>('')

const projectTotal = ref(0)
const versionTotal = ref(0)
const assetTotal = ref(0)
const testCaseTotal = ref(0)
const apiCaseTotal = ref(0)

const pendingReviewFn = ref(0)
const pendingReviewApi = ref(0)
const notExecutedFn = ref(0)
const notExecutedApi = ref(0)

const queuedTotal = ref(0)
const pendingTotal = ref(0)
const runningTotal = ref(0)
const completedTaskTotal = ref(0)
const failedTaskTotal = ref(0)

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
  const u = (s || '').toUpperCase()
  if (u === 'QUEUED') return 'info'
  if (u === 'PENDING') return 'info'
  if (u === 'RUNNING') return 'warning'
  if (u === 'COMPLETED') return 'success'
  if (u === 'FAILED') return 'danger'
  return 'info'
}

function taskStatusLabel(s: string) {
  const u = (s || '').toUpperCase()
  const map: Record<string, string> = {
    QUEUED: '排队',
    PENDING: '待启动',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[u] || s || '—'
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
  const u = (s || '').toUpperCase()
  const map: Record<string, string> = {
    QUEUED: '排队',
    RUNNING: '导出中',
    SUCCESS: '成功',
    FAILED: '失败',
  }
  return map[u] || s || '—'
}

function exportStatusType(s: string | undefined) {
  const u = (s || '').toUpperCase()
  if (u === 'SUCCESS') return 'success'
  if (u === 'FAILED') return 'danger'
  if (u === 'RUNNING') return 'warning'
  return 'info'
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
  await Promise.all([loadOverview(), loadProjectVersionMaps(), loadRecentChanges(), loadTasksPulse(), loadRecentExports()])
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

onMounted(async () => {
  await refreshAll()
  pollTimer = window.setInterval(() => {
    loadTasksPulse()
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
    <header class="dash-header">
      <div class="dash-header-text">
        <h1 class="dash-title">工作台</h1>
        <p class="dash-desc">
          汇总项目、版本、资产与用例规模，跟踪生成任务与导出动态；点击数据卡片可跳转到对应模块。
        </p>
        <p v-if="lastRefreshAt" class="dash-updated">数据刷新于 {{ lastRefreshAt }}</p>
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
    </header>

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
          <span class="insight-meta-line">{{ exportPulseSummary }}</span>
        </div>
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
  gap: 16px;
  min-height: 0;
  width: 100%;
  max-width: none;
}

.dash-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 2px 8px;
}

.dash-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}

.dash-desc {
  margin: 0;
  max-width: 640px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}

.dash-updated {
  margin: 10px 0 0;
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
</style>
