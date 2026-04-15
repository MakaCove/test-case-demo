<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue'
import { ElMessage, ElTag } from 'element-plus'
import { api, type ExportRecord, type Project, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'
import { EXPORT_STATUS, statusLabel as dictStatusLabel, statusTagType as dictStatusTagType } from '../utils/statusDictionary'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const projectsLoading = ref(false)
const versionsLoading = ref(false)

const projectId = ref('')
const versionId = ref('')
const status = ref('')

const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const records = ref<ExportRecord[]>([])

const createVisible = ref(false)
const createProjectId = ref('')
const createVersionId = ref('')
const createScope = ref<'all'>('all')
/** 与后端 ExportRequestOptions：FUNCTIONAL / API */
const createTargets = ref<string[]>(['FUNCTIONAL', 'API'])

const safeProjectId = computed(() => {
  const v = Number(projectId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
})
const safeVersionId = computed(() => {
  const v = Number(versionId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
})
const safeCreateProjectId = computed(() => {
  const v = Number(createProjectId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
})
const safeCreateVersionId = computed(() => {
  const v = Number(createVersionId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
})

function exportStatusLabel(v?: string) {
  return dictStatusLabel(EXPORT_STATUS, v, '—')
}

function exportStatusTagType(v?: string) {
  return dictStatusTagType(EXPORT_STATUS, v, 'info')
}

async function loadProjects() {
  projectsLoading.value = true
  try {
    const data = await api.getProjects({ pageNo: 1, pageSize: 200 })
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
    const data = await api.getAllVersions({ projectId: pid, pageNo: 1, pageSize: 200 })
    versions.value = data.records
  } catch (e: any) {
    ElMessage.error(e.message || '加载版本失败')
  } finally {
    versionsLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const data = await api.getExportRecords({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: safeProjectId.value || undefined,
      versionId: safeVersionId.value || undefined,
      status: status.value,
    })
    records.value = data.records
    total.value = data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载导出记录失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  loadRecords()
}

async function onReset() {
  projectId.value = ''
  versionId.value = ''
  status.value = ''
  versions.value = []
  pageNo.value = 1
  await loadRecords()
}

function openCreate() {
  createProjectId.value = ''
  createVersionId.value = ''
  createScope.value = 'all'
  createTargets.value = ['FUNCTIONAL', 'API']
  createVisible.value = true
}

async function submitCreate() {
  if (!safeCreateProjectId.value || !safeCreateVersionId.value) {
    ElMessage.warning('请选择项目和版本')
    return
  }
  if (!createTargets.value.length) {
    ElMessage.warning('请至少选择一种导出内容（功能用例 / 接口用例）')
    return
  }
  const requestJson = JSON.stringify({
    targets: createTargets.value,
    scope: createScope.value,
  })
  try {
    await api.createExport({
      projectId: safeCreateProjectId.value,
      versionId: safeCreateVersionId.value,
      format: 'md',
      scope: createScope.value,
      requestJson,
    })
    ElMessage.success('已发起导出（MVP：同步生成）')
    createVisible.value = false
    loadRecords()
  } catch (e: any) {
    ElMessage.error(e.message || '发起导出失败')
  }
}

async function retry(row: ExportRecord) {
  try {
    await api.retryExport(row.id)
    ElMessage.success('已重试')
    loadRecords()
  } catch (e: any) {
    ElMessage.error(e.message || '重试失败')
  }
}

function download(row: ExportRecord) {
  api
    .downloadExport(row.id)
    .then((blob) => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${row.exportNo || 'export'}-${row.id}.md`
      a.click()
      URL.revokeObjectURL(url)
    })
    .catch((e: any) => {
      ElMessage.error(e.message || '下载失败')
    })
}

onMounted(async () => {
  await loadProjects()
  await loadRecords()
})
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-select v-model="projectId" filterable clearable :loading="projectsLoading" placeholder="项目" style="width: 240px" @change="(v:any)=>{ versionId=''; loadVersionsForProject(Number(v||0)) }">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
          </el-select>
          <el-select v-model="versionId" filterable clearable :loading="versionsLoading" placeholder="版本" style="width: 260px">
            <el-option v-for="v in versions" :key="v.id" :label="`${v.name || '未命名版本'}（${v.versionNo}）`" :value="String(v.id)" />
          </el-select>
          <el-select v-model="status" clearable placeholder="状态" style="width: 160px">
            <el-option label="导出中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button type="success" @click="openCreate">发起导出</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-body">
        <el-table :data="records" :size="tableDensity" v-loading="loading" border stripe height="100%">
          <el-table-column prop="exportNo" label="编码" min-width="170" show-overflow-tooltip />
          <el-table-column prop="format" label="格式" width="90" />
          <el-table-column prop="exportContent" label="导出内容" min-width="140" show-overflow-tooltip />
          <el-table-column prop="scope" label="范围" width="90" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="exportStatusTagType(row.status)">{{ exportStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fileSize" label="大小" width="120" />
          <el-table-column prop="errorMessage" label="错误" min-width="220" />
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="row.status !== 'SUCCESS'" @click="download(row)">下载</el-button>
              <el-divider direction="vertical" />
              <el-button link type="warning" :disabled="row.status !== 'FAILED'" @click="retry(row)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="table-footer">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadRecords"
          @size-change="onSearch"
        />
      </div>
    </el-card>
  </div>

  <el-dialog v-model="createVisible" title="发起导出" width="760px" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="项目（必选）">
          <el-select v-model="createProjectId" filterable clearable :loading="projectsLoading" placeholder="选择项目" @change="(v:any)=>{ createVersionId=''; loadVersionsForProject(Number(v||0)) }">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本（必选）">
          <el-select v-model="createVersionId" filterable clearable :loading="versionsLoading" placeholder="选择版本">
            <el-option v-for="v in versions" :key="v.id" :label="`${v.name || '未命名版本'}（${v.versionNo}）`" :value="String(v.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="导出内容（至少选一项）" class="full-span">
          <el-checkbox-group v-model="createTargets">
            <el-checkbox label="FUNCTIONAL">功能用例</el-checkbox>
            <el-checkbox label="API">接口用例</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="格式">
          <el-input model-value="Markdown（.md）" disabled />
        </el-form-item>
        <el-form-item label="范围">
          <el-select v-model="createScope" placeholder="请选择范围" style="width: 100%">
            <el-option label="全部（当前版本下所选类型的全部用例）" value="all" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="createVisible=false">取消</el-button>
      <el-button type="primary" @click="submitCreate">确认导出</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.page-shell {
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 12px;
}
.query-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}

.query-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.query-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}
.table-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.table-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding-bottom: 10px;
}
.table-body {
  flex: 1;
  min-height: 0;
}
.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
  border-top: 1px solid #f0f2f5;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}
.full-span {
  grid-column: 1 / -1;
}
</style>

