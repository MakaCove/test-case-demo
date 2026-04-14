<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, type Project, type UiNlTask, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const router = useRouter()

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const tasksLoading = ref(false)
const tasks = ref<UiNlTask[]>([])
const projectId = ref('')
const versionId = ref('')
const status = ref('')
const caseTitleKeyword = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const caseTitleMap = ref<Record<number, string>>({})

const filteredVersions = computed(() => {
  const pid = Number(projectId.value)
  if (!pid) return versions.value
  return versions.value.filter((v) => v.projectId === pid)
})

// 本页只做“任务列表”；任务步骤详情改为独立页面

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

async function loadTasks() {
  tasksLoading.value = true
  try {
    const data = await api.getUiNlTasks({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: Number(projectId.value) || undefined,
      versionId: Number(versionId.value) || undefined,
      status: status.value || undefined,
      caseTitle: caseTitleKeyword.value || undefined,
    })
    tasks.value = data.records
    total.value = data.total
    await loadCaseTitles(data.records)
  } finally {
    tasksLoading.value = false
  }
}

function openTaskDetail(row: UiNlTask) {
  router.push({ path: '/ui-nl-steps/detail', query: { taskId: String(row.id) } })
}

async function loadCaseTitles(rows: UiNlTask[]) {
  const missingIds = Array.from(new Set(rows.map((r) => r.uiNlCaseId))).filter((id) => id && !caseTitleMap.value[id])
  if (!missingIds.length) return
  const results = await Promise.all(
    missingIds.map(async (id) => {
      try {
        const c = await api.getUiNlCase(id)
        return { id, title: c.title || `用例#${id}` }
      } catch {
        return { id, title: `用例#${id}` }
      }
    }),
  )
  const next = { ...caseTitleMap.value }
  for (const item of results) next[item.id] = item.title
  caseTitleMap.value = next
}

function caseTitle(caseId: number) {
  return caseTitleMap.value[caseId] || `用例#${caseId}`
}

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

watch(projectId, () => {
  versionId.value = ''
})

onMounted(async () => {
  await loadProjects()
  await loadVersions()
  await loadTasks()
})
</script>

<template>
  <div class="page-shell">
    <el-card>
      <div class="query-row">
        <el-select v-model="projectId" clearable filterable placeholder="项目" style="width: 220px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
        </el-select>
        <el-select v-model="versionId" clearable filterable placeholder="版本" style="width: 220px">
          <el-option
            v-for="v in filteredVersions"
            :key="v.id"
            :label="`${v.versionNo}${v.name ? ` - ${v.name}` : ''}`"
            :value="String(v.id)"
          />
        </el-select>
        <el-select v-model="status" clearable placeholder="状态" style="width: 160px">
          <el-option label="待启动" value="PENDING" />
          <el-option label="排队" value="QUEUED" />
          <el-option label="生成中" value="PLANNING" />
          <el-option label="待执行" value="READY" />
          <el-option label="失败" value="FAILED" />
          <el-option label="中断" value="INTERRUPTED" />
        </el-select>
        <el-input v-model="caseTitleKeyword" clearable placeholder="用例标题关键词" style="width: 220px" />
        <div class="query-actions">
          <el-button type="primary" @click="() => { pageNo = 1; loadTasks() }">查询</el-button>
          <el-button @click="() => { projectId=''; versionId=''; status=''; caseTitleKeyword=''; pageNo=1; loadTasks() }">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="tasks" :size="tableDensity" border stripe v-loading="tasksLoading" height="100%">
        <el-table-column prop="taskNo" label="任务号" min-width="140" />
        <el-table-column label="项目" min-width="180">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="版本" min-width="180">
          <template #default="{ row }">{{ versionLabel(row.versionId) }}</template>
        </el-table-column>
        <el-table-column label="用例标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ caseTitle(row.uiNlCaseId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="生成时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTaskDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTasks"
          @size-change="() => { pageNo = 1; loadTasks() }"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-shell { height: 100%; display: grid; grid-template-rows: auto 1fr; gap: 12px; }
.query-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.query-actions { margin-left: auto; display: inline-flex; gap: 10px; align-items: center; }
.table-card { min-height: 0; display: flex; flex-direction: column; }
.table-card :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.detail-wrap { display: flex; flex-direction: column; gap: 10px; }
.pager { display: flex; justify-content: flex-end; padding-top: 10px; }
</style>
