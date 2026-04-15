<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, type Project, type UiNlReport, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'
import {
  UI_NL_REPORT_STATUS,
  statusLabel as dictStatusLabel,
  statusTagType as dictStatusTagType,
} from '../utils/statusDictionary'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const route = useRoute()

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const projectsLoading = ref(false)
const versionsLoading = ref(false)
const projectId = ref('')
const versionId = ref('')
const status = ref('')
const loading = ref(false)
const records = ref<UiNlReport[]>([])
const taskNoMap = ref<Record<number, string>>({})
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const current = ref<UiNlReport | null>(null)

function reportStatusLabel(v?: string) {
  return dictStatusLabel(UI_NL_REPORT_STATUS, v, '—')
}

function reportStatusTagType(v?: string) {
  return dictStatusTagType(UI_NL_REPORT_STATUS, v, 'info')
}

async function loadProjects() {
  projectsLoading.value = true
  try {
    const data = await api.getProjects({ pageNo: 1, pageSize: 300 })
    projects.value = data.records
  } catch (e: any) {
    ElMessage.error(e.message || '加载项目失败')
  } finally {
    projectsLoading.value = false
  }
}

async function loadVersionsForProject(pid: number) {
  versions.value = []
  if (!pid) return
  versionsLoading.value = true
  try {
    const data = await api.getAllVersions({ projectId: pid, pageNo: 1, pageSize: 500 })
    versions.value = data.records
  } catch (e: any) {
    ElMessage.error(e.message || '加载版本失败')
  } finally {
    versionsLoading.value = false
  }
}

async function loadReports() {
  loading.value = true
  try {
    const data = await api.getUiNlReports({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: Number(projectId.value) || undefined,
      versionId: Number(versionId.value) || undefined,
      status: status.value || undefined,
    })
    let rows = data.records
    const qTaskId = Number(route.query.taskId || 0)
    if (qTaskId) {
      rows = rows.filter((r) => r.taskId === qTaskId)
    }
    records.value = rows
    await loadTaskNos(rows)
    total.value = qTaskId ? rows.length : data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载报告失败')
  } finally {
    loading.value = false
  }
}

async function loadTaskNos(rows: UiNlReport[]) {
  const missingTaskIds = Array.from(new Set(rows.map((r) => r.taskId))).filter((id) => id && !taskNoMap.value[id])
  if (!missingTaskIds.length) return
  const pairs = await Promise.all(
    missingTaskIds.map(async (taskId) => {
      try {
        const task = await api.getUiNlTask(taskId)
        return { taskId, taskNo: task.taskNo || `任务#${taskId}` }
      } catch {
        return { taskId, taskNo: `任务#${taskId}` }
      }
    }),
  )
  const next = { ...taskNoMap.value }
  for (const p of pairs) next[p.taskId] = p.taskNo
  taskNoMap.value = next
}

function taskNoDisplay(taskId: number) {
  return taskNoMap.value[taskId] || `任务#${taskId}`
}

async function openDetail(row: UiNlReport) {
  current.value = await api.getUiNlReport(row.id)
  detailVisible.value = true
}

async function openHtmlReport(row: UiNlReport) {
  try {
    const blob = await api.downloadUiNlReportHtml(row.id)
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 30_000)
  } catch (e: any) {
    ElMessage.error(e.message || '打开 HTML 报告失败')
  }
}

onMounted(async () => {
  await loadProjects()
  await loadReports()
})
</script>

<template>
  <div class="page-shell">
    <el-card>
      <div class="query-row">
        <el-select
          v-model="projectId"
          filterable
          clearable
          :loading="projectsLoading"
          placeholder="项目"
          style="width: 240px"
          @change="(v:any)=>{ versionId=''; loadVersionsForProject(Number(v || 0)) }"
        >
          <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
        </el-select>
        <el-select v-model="versionId" filterable clearable :loading="versionsLoading" placeholder="版本" style="width: 260px">
          <el-option v-for="v in versions" :key="v.id" :label="`${v.versionNo}${v.name ? ' - ' + v.name : ''}`" :value="String(v.id)" />
        </el-select>
        <el-select v-model="status" clearable placeholder="状态" style="width: 140px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="取消" value="CANCELLED" />
        </el-select>
        <div class="query-actions">
          <el-button type="primary" @click="() => { pageNo = 1; loadReports() }">查询</el-button>
          <el-button @click="() => { projectId=''; versionId=''; status=''; pageNo=1; loadReports() }">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="records" :size="tableDensity" border stripe v-loading="loading" height="100%">
        <el-table-column prop="reportNo" label="报告编号" min-width="140" />
        <el-table-column label="任务号" min-width="140">
          <template #default="{ row }">{{ taskNoDisplay(row.taskId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="reportStatusTagType(row.status)">{{ reportStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalSteps" label="总步数" width="90" />
        <el-table-column prop="passedSteps" label="成功" width="80" />
        <el-table-column prop="failedSteps" label="失败" width="80" />
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="执行开始" width="170"><template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template></el-table-column>
        <el-table-column label="执行结束" width="170"><template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template></el-table-column>
        <el-table-column label="创建" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="success" :disabled="!row.reportFilePath" @click="openHtmlReport(row)">HTML</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadReports"
          @size-change="() => { pageNo = 1; loadReports() }"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="报告详情" size="960px">
      <div v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="报告编号">{{ current.reportNo }}</el-descriptions-item>
          <el-descriptions-item label="任务ID">{{ current.taskId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ reportStatusLabel(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="步数">{{ current.totalSteps }} / 成功 {{ current.passedSteps }} / 失败 {{ current.failedSteps }}</el-descriptions-item>
          <el-descriptions-item label="执行开始">{{ formatDateTime(current.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="执行结束">{{ formatDateTime(current.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="HTML 生成时间">{{ formatDateTime(current.reportGeneratedAt) }}</el-descriptions-item>
          <el-descriptions-item label="HTML 路径">{{ current.reportFilePath || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <el-form label-position="top">
          <el-form-item label="摘要">
            <el-input
              :model-value="current.summary || ''"
              type="textarea"
              :autosize="{ minRows: 8, maxRows: 24 }"
              readonly
            />
          </el-form-item>
          <el-form-item label="artifactsJson"><el-input :model-value="current.artifactsJson || ''" type="textarea" :rows="6" readonly /></el-form-item>
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-shell { height: 100%; display: grid; grid-template-rows: auto 1fr; gap: 12px; }
.query-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.query-actions { margin-left: auto; display: inline-flex; gap: 10px; align-items: center; }
.table-card { min-height: 0; display: flex; flex-direction: column; }
.table-card :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.pager { display: flex; justify-content: flex-end; padding-top: 10px; }
</style>
