<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue'
import { api, type Project, type Version } from '../api/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '../utils/formatDateTime'
import { VERSION_STATUS, statusLabel as dictStatusLabel, statusTagType as dictStatusTagType } from '../utils/statusDictionary'

const projectId = ref('')
const projects = ref<Project[]>([])
const projectsLoading = ref(false)
const keyword = ref('')
const versions = ref<Version[]>([])
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref('')
const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const selectedIds = ref<number[]>([])
const createVisible = ref(false)
const editVisible = ref(false)
const createProjectId = ref('')
const form = ref({
  versionNo: '',
  name: '',
  description: '',
})
const editForm = ref({
  id: 0,
  versionNo: '',
  name: '',
  description: '',
  status: 'DRAFT',
})
const message = ref('')
const projectNameMap = ref<Record<number, string>>({})

function versionStatusLabel(status: string | undefined) {
  return dictStatusLabel(VERSION_STATUS, status, '-')
}

function versionStatusTagType(status: string | undefined) {
  return dictStatusTagType(VERSION_STATUS, status, 'info')
}

function rememberProjectId(value: string) {
  projectId.value = value
}

function getProjectId(): number | null {
  const value = Number(projectId.value)
  if (!Number.isInteger(value) || value <= 0) {
    return null
  }
  return value
}

async function loadProjects() {
  projectsLoading.value = true
  try {
    const data = await api.getProjects({ pageNo: 1, pageSize: 100 })
    projects.value = data.records
    projectNameMap.value = Object.fromEntries(data.records.map((p) => [p.id, p.name]))
  } catch (error: any) {
    ElMessage.error(error.message || '加载项目失败')
  } finally {
    projectsLoading.value = false
  }
}

async function loadVersions(projectIdOverride?: number | null) {
  loading.value = true
  try {
    const data = await api.getAllVersions({
      projectId: (projectIdOverride ?? getProjectId()) || undefined,
      keyword: keyword.value,
      status: statusFilter.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    versions.value = data.records
    total.value = data.total
    message.value = ''
  } catch (error) {
    message.value = (error as Error).message
    ElMessage.error(message.value)
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  const validProjectId = Number(createProjectId.value || projectId.value)
  if (!Number.isInteger(validProjectId) || validProjectId <= 0) {
    ElMessage.warning('请选择项目')
    return
  }
  try {
    await api.createVersion(validProjectId, form.value)
    ElMessage.success('版本创建成功')
    form.value = { versionNo: '', name: '', description: '' }
    createVisible.value = false
    // 创建完成后：展示所有数据（不按创建时项目过滤）
    projectId.value = ''
    pageNo.value = 1
    await loadVersions()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function openEdit(row: Version) {
  editForm.value = {
    id: row.id,
    versionNo: row.versionNo,
    name: row.name || '',
    description: row.description || '',
    status: row.status,
  }
  editVisible.value = true
}

async function onEdit() {
  try {
    await api.updateVersion(editForm.value.id, {
      versionNo: editForm.value.versionNo,
      name: editForm.value.name,
      description: editForm.value.description,
      status: editForm.value.status,
    })
    ElMessage.success('版本更新成功')
    editVisible.value = false
    await loadVersions()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function onDelete(row: Version) {
  const confirmed = await ElMessageBox.confirm(`确认删除版本「${row.versionNo}」吗？`, '提示', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await api.deleteVersion(row.id)
    ElMessage.success('版本删除成功')
    await loadVersions()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function onSelectionChange(rows: Version[]) {
  selectedIds.value = rows.map((r) => r.id)
}

async function onBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选要删除的版本')
    return
  }
  const confirmed = await ElMessageBox.confirm(`确认批量删除 ${selectedIds.value.length} 个版本吗？`, '提示', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    for (const id of selectedIds.value) {
      await api.deleteVersion(id)
    }
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    await loadVersions()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function onSearchFilter() {
  pageNo.value = 1
  loadVersions()
}

function onResetFilter() {
  projectId.value = ''
  keyword.value = ''
  statusFilter.value = ''
  onSearchFilter()
}

function onPageNoChange(value: number) {
  pageNo.value = value
  loadVersions()
}

function onPageSizeChange(value: number) {
  pageSize.value = value
  pageNo.value = 1
  loadVersions()
}

onMounted(async () => {
  projectId.value = ''
  createProjectId.value = ''
  await loadProjects()
  await loadVersions()
})
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-select
            v-model="projectId"
            filterable
            clearable
            :loading="projectsLoading"
            placeholder="选择项目"
            style="width: 240px"
            @change="(v: string | number | null) => rememberProjectId(String(v || ''))"
          >
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="String(p.id)" />
          </el-select>
          <el-input v-model="keyword" placeholder="版本号/版本名" style="width: 220px" clearable />
          <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 160px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearchFilter">查询</el-button>
          <el-button @click="onResetFilter">重置</el-button>
          <el-button type="success" @click="createProjectId = projectId; createVisible = true">新增版本</el-button>
          <el-button type="danger" plain :disabled="selectedIds.length === 0" @click="onBatchDelete">批量删除</el-button>
        </div>
      </div>
      <p v-if="message" class="error">{{ message }}</p>
    </el-card>

    <el-card class="table-card">
      <div class="table-body">
        <el-table :data="versions" :size="tableDensity" v-loading="loading" border stripe height="100%" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column label="项目名称" min-width="180">
            <template #default="{ row }">{{ projectNameMap[row.projectId] || '-' }}</template>
          </el-table-column>
          <el-table-column prop="versionNo" label="版本号" min-width="180" />
          <el-table-column prop="name" label="版本名" min-width="180">
            <template #default="{ row }">{{ row.name || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="versionStatusTagType(row.status)">{{ versionStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-divider direction="vertical" />
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
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
          @current-change="onPageNoChange"
          @size-change="onPageSizeChange"
        />
      </div>
    </el-card>
  </div>

  <el-dialog v-model="createVisible" title="新增版本" width="620" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="所属项目" class="full-span">
          <el-select v-model="createProjectId" filterable clearable :loading="projectsLoading" placeholder="选择项目">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="form.versionNo" placeholder="如 v1.0.1" />
        </el-form-item>
        <el-form-item label="版本名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="版本描述" class="full-span">
          <el-input v-model="form.description" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" @click="onCreate">确认新增</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editVisible" title="编辑版本" width="620" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="版本号">
          <el-input v-model="editForm.versionNo" />
        </el-form-item>
        <el-form-item label="版本名">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本描述" class="full-span">
          <el-input v-model="editForm.description" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="onEdit">确认保存</el-button>
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
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  gap: 0 12px;
}

.full-span {
  grid-column: 1 / -1;
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

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-header span {
  color: #909399;
  font-size: 13px;
}

.error {
  color: #dc2626;
  margin-top: 8px;
}

@media (max-width: 1200px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
