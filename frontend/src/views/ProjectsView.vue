<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue'
import { api, type Project } from '../api/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '../utils/formatDateTime'

const loading = ref(false)
const filterName = ref('')
const filterCode = ref('')
const projects = ref<Project[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const selectedIds = ref<number[]>([])
const createVisible = ref(false)
const editVisible = ref(false)
const createForm = ref({
  name: '',
  code: '',
  description: '',
  owner: 'admin',
})
const editForm = ref({
  id: 0,
  name: '',
  description: '',
  owner: 'admin',
})

async function loadProjects() {
  loading.value = true
  try {
    const data = await api.getProjects({
      name: filterName.value,
      code: filterCode.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    projects.value = data.records
    total.value = data.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  try {
    await api.createProject(createForm.value)
    ElMessage.success('项目创建成功')
    createForm.value = { name: '', code: '', description: '', owner: 'admin' }
    createVisible.value = false
    await loadProjects()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function openEdit(row: Project) {
  editForm.value = {
    id: row.id,
    name: row.name,
    description: row.description || '',
    owner: row.owner || 'admin',
  }
  editVisible.value = true
}

async function onEdit() {
  try {
    await api.updateProject(editForm.value.id, {
      name: editForm.value.name,
      description: editForm.value.description,
      owner: editForm.value.owner,
    })
    ElMessage.success('项目更新成功')
    editVisible.value = false
    await loadProjects()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function onDelete(row: Project) {
  const confirmed = await ElMessageBox.confirm(`确认删除项目「${row.name}」吗？`, '提示', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await api.deleteProject(row.id)
    ElMessage.success('项目删除成功')
    await loadProjects()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function onSelectionChange(rows: Project[]) {
  selectedIds.value = rows.map((r) => r.id)
}

async function onBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选要删除的项目')
    return
  }
  const confirmed = await ElMessageBox.confirm(`确认批量删除 ${selectedIds.value.length} 个项目吗？`, '提示', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await api.batchDeleteProjects(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    await loadProjects()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function onSearch() {
  pageNo.value = 1
  loadProjects()
}

function onResetFilters() {
  filterName.value = ''
  filterCode.value = ''
  onSearch()
}

function onPageNoChange(value: number) {
  pageNo.value = value
  loadProjects()
}

function onPageSizeChange(value: number) {
  pageSize.value = value
  pageNo.value = 1
  loadProjects()
}

onMounted(loadProjects)
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-input v-model="filterName" placeholder="项目名称" clearable style="width: 200px" />
          <el-input v-model="filterCode" placeholder="项目编码" clearable style="width: 200px" />
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onResetFilters">重置</el-button>
          <el-button type="success" @click="createVisible = true">新增项目</el-button>
          <el-button type="danger" plain :disabled="selectedIds.length === 0" @click="onBatchDelete">批量删除</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-body">
        <el-table :data="projects" :size="tableDensity" v-loading="loading" border stripe height="100%" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="code" label="编码" min-width="180" />
          <el-table-column prop="owner" label="负责人" width="160" />
          <el-table-column prop="description" label="描述" min-width="320">
            <template #default="{ row }">{{ row.description || '-' }}</template>
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

  <el-dialog v-model="createVisible" title="新增项目" width="620" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="项目名">
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item label="项目编码">
          <el-input v-model="createForm.code" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="createForm.owner" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" @click="onCreate">确认新增</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editVisible" title="编辑项目" width="620" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="项目名">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="editForm.owner" />
        </el-form-item>
        <el-form-item label="描述" class="full-span">
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

@media (max-width: 1200px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
