<script setup lang="ts">
import { inject, nextTick, onMounted, ref, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))

/** 本项目模型调用统一走 OpenAI 兼容协议，与后端 ModelConnectivityService 默认分支一致 */
const DEFAULT_PROVIDER = 'openai'

type ModelConfig = {
  id: number
  name: string
  provider: string
  baseUrl: string
  modelKey: string
  status: string
  apiKeyEncrypted?: string
  temperature?: number
  maxTokens?: number
  createdAt?: string
  updatedAt?: string
}

const loading = ref(false)
const records = ref<ModelConfig[]>([])
const filterName = ref('')
const selectedIds = ref<number[]>([])
const tableRef = ref<{ clearSelection: () => void } | null>(null)
const testingConnectionId = ref<number | null>(null)

const createVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  name: '',
  provider: '',
  baseUrl: '',
  modelKey: '',
  apiKeyEncrypted: '',
  temperature: 0.3,
  maxTokens: 4096,
})

async function loadList() {
  loading.value = true
  try {
    records.value = await api.getModelConfigs({
      name: filterName.value.trim() || undefined,
    })
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
    nextTick(() => tableRef.value?.clearSelection())
  }
}

function onSelectionChange(rows: ModelConfig[]) {
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
  form.value = { name: '', provider: DEFAULT_PROVIDER, baseUrl: '', modelKey: '', apiKeyEncrypted: '', temperature: 0.3, maxTokens: 4096 }
  createVisible.value = true
}

function openEdit(row: ModelConfig) {
  isEditing.value = true
  editingId.value = row.id
  form.value = {
    name: row.name,
    provider: DEFAULT_PROVIDER,
    baseUrl: row.baseUrl,
    modelKey: row.modelKey,
    apiKeyEncrypted: row.apiKeyEncrypted || '',
    temperature: row.temperature ?? 0.3,
    maxTokens: row.maxTokens ?? 4096,
  }
  createVisible.value = true
}

async function submit() {
  if (!form.value.name.trim() || !form.value.provider.trim() || !form.value.baseUrl.trim() || !form.value.modelKey.trim() || !form.value.apiKeyEncrypted.trim()) {
    ElMessage.warning('请补全必填字段')
    return
  }
  try {
    if (isEditing.value && editingId.value) {
      await api.updateModelConfig(editingId.value, { ...form.value })
      ElMessage.success('已更新')
    } else {
      await api.createModelConfig({ ...form.value })
      ElMessage.success('已创建')
    }
    createVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  }
}

async function toggleStatus(row: ModelConfig) {
  try {
    if (row.status === 'ENABLED') {
      await api.disableModelConfig(row.id)
      ElMessage.success('已停用')
    } else {
      await api.enableModelConfig(row.id)
      ElMessage.success('已启用')
    }
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function testConnection(row: ModelConfig) {
  if (testingConnectionId.value !== null) {
    return
  }
  testingConnectionId.value = row.id
  try {
    const result = await api.testModelConnection(row.id, 'ping')
    ElMessage.success(result)
  } catch (e: any) {
    ElMessage.error(e.message || '测试失败')
  } finally {
    testingConnectionId.value = null
  }
}

async function removeOne(row: ModelConfig) {
  const ok = await ElMessageBox.confirm(`确认删除模型配置「${row.name}」吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.deleteModelConfig(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadList)
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-input
            v-model="filterName"
            placeholder="请输入配置名称"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onResetFilters">重置</el-button>
          <el-button @click="loadList">刷新</el-button>
          <el-button type="success" @click="openCreate">新增模型配置</el-button>
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
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="provider" label="Provider" width="140" />
          <el-table-column prop="modelKey" label="Model" min-width="180" />
          <el-table-column prop="baseUrl" label="Base URL" min-width="220" />
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" min-width="300" width="300" fixed="right" align="center">
            <template #default="{ row }">
              <div class="table-ops">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-divider direction="vertical" />
                <el-button link type="warning" @click="toggleStatus(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
                <el-divider direction="vertical" />
                <el-button
                  link
                  type="success"
                  :loading="testingConnectionId === row.id"
                  :disabled="testingConnectionId !== null && testingConnectionId !== row.id"
                  @click="testConnection(row)"
                  >连通性测试</el-button
                >
                <el-divider direction="vertical" />
                <el-button link type="danger" @click="removeOne(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>

  <el-dialog v-model="createVisible" :title="isEditing ? '编辑模型配置' : '新增模型配置'" width="860px" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="名称（必填）">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="Provider">
          <el-input v-model="form.provider" disabled />
          <div class="field-hint">本项目统一为 OpenAI 兼容接口，固定为 <code>openai</code></div>
        </el-form-item>
        <el-form-item label="Base URL（必填）">
          <el-input v-model="form.baseUrl" placeholder="https://api.xxx.com" />
        </el-form-item>
        <el-form-item label="Model Key（必填）">
          <el-input v-model="form.modelKey" placeholder="gpt-4.1/..." />
        </el-form-item>
        <el-form-item label="API Key（必填）" class="full-span">
          <el-input v-model="form.apiKeyEncrypted" show-password />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-input v-model.number="form.temperature" />
        </el-form-item>
        <el-form-item label="Max Tokens">
          <el-input v-model.number="form.maxTokens" />
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

.table-ops {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  gap: 0;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}
.full-span {
  grid-column: 1 / -1;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.field-hint code {
  font-size: 12px;
  padding: 0 4px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>

