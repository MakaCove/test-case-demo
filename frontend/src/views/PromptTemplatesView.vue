<script setup lang="ts">
import { inject, nextTick, onMounted, ref, watch, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))

type PromptTemplate = {
  id: number
  name: string
  scopeType: string
  scopeId?: number
  versionNo: number
  status: string
  content?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

type ProjectRow = { id: number; name: string; code: string }

const loading = ref(false)
const records = ref<PromptTemplate[]>([])
const selectedIds = ref<number[]>([])
const tableRef = ref<{ clearSelection: () => void } | null>(null)

const filterName = ref('')

const projects = ref<ProjectRow[]>([])
const projectsLoading = ref(false)

const createVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)

const SCOPE_TYPE_LABELS: Record<string, string> = {
  GLOBAL: '全局',
  PROJECT: '项目',
}

const STATUS_LABELS: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

function scopeTypeLabel(s: string) {
  return SCOPE_TYPE_LABELS[s] || s
}

function statusLabel(s: string) {
  return STATUS_LABELS[s] || s
}

function projectLabel(id?: number) {
  if (id == null) return ''
  const p = projects.value.find((x) => x.id === id)
  return p ? `${p.name}（${p.code}）` : ''
}

const form = ref({
  name: '',
  scopeType: 'GLOBAL',
  scopeId: '' as string,
  content: '',
  remark: '',
})

async function loadProjects() {
  projectsLoading.value = true
  try {
    const res = await api.getProjects({ pageNo: 1, pageSize: 500, sortBy: 'id', sortOrder: 'asc' })
    projects.value = res.records ?? []
  } catch (e: any) {
    ElMessage.error(e.message || '加载项目列表失败')
  } finally {
    projectsLoading.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    records.value = await api.getPromptTemplates({
      name: filterName.value.trim() || undefined,
    })
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
    nextTick(() => tableRef.value?.clearSelection())
  }
}

function onSelectionChange(rows: PromptTemplate[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function onSearch() {
  void loadList()
}

function onResetFilters() {
  filterName.value = ''
  void loadList()
}

function openCreate() {
  isEditing.value = false
  editingId.value = null
  form.value = { name: '', scopeType: 'GLOBAL', scopeId: '', content: '', remark: '' }
  void loadProjects()
  createVisible.value = true
}

function openEdit(row: PromptTemplate) {
  isEditing.value = true
  editingId.value = row.id
  form.value = {
    name: row.name,
    scopeType: row.scopeType || 'GLOBAL',
    scopeId: row.scopeId ? String(row.scopeId) : '',
    content: row.content || '',
    remark: row.remark || '',
  }
  void loadProjects()
  createVisible.value = true
}

watch(
  () => form.value.scopeType,
  (t) => {
    if (t !== 'PROJECT') {
      form.value.scopeId = ''
    }
  },
)

async function submit() {
  if (!form.value.name.trim() || !form.value.content.trim()) {
    ElMessage.warning('请补全必填字段')
    return
  }
  if (!isEditing.value && form.value.scopeType === 'PROJECT') {
    const pid = form.value.scopeId ? Number(form.value.scopeId) : 0
    if (!pid || pid <= 0) {
      ElMessage.warning('请选择关联项目')
      return
    }
  }
  const scopeIdNum = form.value.scopeType === 'PROJECT' && form.value.scopeId ? Number(form.value.scopeId) : undefined
  try {
    if (isEditing.value && editingId.value) {
      await api.updatePromptTemplate(editingId.value, { name: form.value.name.trim(), content: form.value.content, remark: form.value.remark || undefined })
      ElMessage.success('已更新')
    } else {
      await api.createPromptTemplate({
        name: form.value.name.trim(),
        scopeType: form.value.scopeType,
        scopeId: scopeIdNum && scopeIdNum > 0 ? scopeIdNum : undefined,
        content: form.value.content,
        remark: form.value.remark || undefined,
      })
      ElMessage.success('已创建')
    }
    createVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  }
}

async function toggleStatus(row: PromptTemplate) {
  try {
    if (row.status === 'ENABLED') {
      await api.disablePromptTemplate(row.id)
      ElMessage.success('已停用')
    } else {
      await api.enablePromptTemplate(row.id)
      ElMessage.success('已启用')
    }
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function removeOne(row: PromptTemplate) {
  const ok = await ElMessageBox.confirm(`确认删除模板「${row.name}」吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.deletePromptTemplate(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(() => {
  void loadProjects()
  void loadList()
})
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-input v-model="filterName" placeholder="请输入Prompt模板名称" clearable style="width: 240px" @keyup.enter="onSearch" />
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onResetFilters">重置</el-button>
          <el-button @click="loadList">刷新</el-button>
          <el-button type="success" @click="openCreate">新增Prompt模板</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-body">
        <el-table
          ref="tableRef"
          :data="records"
          :size="tableDensity"
          v-loading="loading"
          border
          stripe
          height="100%"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="name" label="Prompt模板名称" min-width="200" />
          <el-table-column prop="scopeType" label="范围" width="120">
            <template #default="{ row }">{{ scopeTypeLabel(row.scopeType) }}</template>
          </el-table-column>
          <el-table-column label="关联项目" min-width="200">
            <template #default="{ row }">
              <span v-if="row.scopeType === 'PROJECT' && row.scopeId">{{ projectLabel(row.scopeId) || row.scopeId }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="versionNo" label="版本" width="110" />
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-divider direction="vertical" />
              <el-button link type="warning" @click="toggleStatus(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
              <el-divider direction="vertical" />
              <el-button link type="danger" @click="removeOne(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>

  <el-dialog v-model="createVisible" :title="isEditing ? '编辑Prompt模板' : '新增Prompt模板'" width="980px" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="Prompt模板名称">
          <el-input v-model="form.name" placeholder="请输入Prompt模板名称" />
        </el-form-item>
        <el-form-item label="范围">
          <el-select v-model="form.scopeType" placeholder="请选择范围" style="width: 100%" :disabled="isEditing">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目">
          <el-select
            v-model="form.scopeId"
            filterable
            clearable
            :loading="projectsLoading"
            :placeholder="form.scopeType === 'PROJECT' ? '请选择项目（项目范围必选）' : '全局范围无需选择项目'"
            style="width: 100%"
            :disabled="isEditing || form.scopeType !== 'PROJECT'"
          >
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
        <el-form-item label="模板内容（必填）" class="full-span">
          <el-input v-model="form.content" type="textarea" :rows="14" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">{{ isEditing ? '确认修改' : '确认新增' }}</el-button>
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
}
.table-body {
  flex: 1;
  min-height: 0;
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
