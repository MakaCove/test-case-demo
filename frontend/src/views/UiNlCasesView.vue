<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, type Project, type UiNlCase, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'
import { SWITCH_STATUS, statusLabel as dictStatusLabel, statusTagType as dictStatusTagType } from '../utils/statusDictionary'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const projectId = ref('')
const versionId = ref('')
const keyword = ref('')
const status = ref('')

const filteredVersions = computed(() => {
  const pid = Number(projectId.value)
  if (!pid) return versions.value
  return versions.value.filter((v) => v.projectId === pid)
})

const filteredFormVersions = computed(() => {
  const pid = Number(formProjectId.value)
  if (!pid) return versions.value
  return versions.value.filter((v) => v.projectId === pid)
})

const loading = ref(false)
const records = ref<UiNlCase[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const editVisible = ref(false)
const editingId = ref(0)
const formProjectId = ref('')
const formVersionId = ref('')
const formTitle = ref('')
const formNlText = ref('')
const formPrecondition = ref('')
const formTargetEnv = ref('')
const formBaseUrl = ref('')
const formCredentialRef = ref('')
const formStatus = ref('ENABLED')
const saving = ref(false)

async function loadProjects() {
  const data = await api.getProjects({ pageNo: 1, pageSize: 200 })
  projects.value = data.records
}

async function loadVersions() {
  const data = await api.getAllVersions({ pageNo: 1, pageSize: 500 })
  versions.value = data.records
}

async function loadRecords() {
  loading.value = true
  try {
    const data = await api.getUiNlCases({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: Number(projectId.value) || undefined,
      versionId: Number(versionId.value) || undefined,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
    })
    records.value = data.records
    total.value = data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

watch(projectId, () => {
  versionId.value = ''
})

watch(formProjectId, () => {
  if (editingId.value) {
    return
  }
  formVersionId.value = ''
})

function resetForm() {
  editingId.value = 0
  formProjectId.value = ''
  formVersionId.value = ''
  formTitle.value = ''
  formNlText.value = ''
  formPrecondition.value = ''
  formTargetEnv.value = ''
  formBaseUrl.value = ''
  formCredentialRef.value = ''
  formStatus.value = 'ENABLED'
}

function openCreate() {
  resetForm()
  editVisible.value = true
}

function openEdit(row: UiNlCase) {
  editingId.value = row.id
  formProjectId.value = String(row.projectId)
  formVersionId.value = String(row.versionId)
  formTitle.value = row.title
  formNlText.value = row.nlText
  formPrecondition.value = row.precondition || ''
  formTargetEnv.value = row.targetEnv || ''
  formBaseUrl.value = row.baseUrl || ''
  formCredentialRef.value = row.credentialRef || ''
  formStatus.value = row.status || 'ENABLED'
  editVisible.value = true
}

async function submit() {
  if (!Number(formProjectId.value) || !Number(formVersionId.value)) {
    ElMessage.warning('请选择项目和版本')
    return
  }
  if (!formTitle.value.trim() || !formNlText.value.trim()) {
    ElMessage.warning('标题和自然语言描述必填')
    return
  }
  saving.value = true
  try {
    const payload = {
      projectId: Number(formProjectId.value),
      versionId: Number(formVersionId.value),
      title: formTitle.value.trim(),
      nlText: formNlText.value.trim(),
      precondition: formPrecondition.value || undefined,
      targetEnv: formTargetEnv.value || undefined,
      baseUrl: formBaseUrl.value || undefined,
      credentialRef: formCredentialRef.value || undefined,
      status: formStatus.value,
    }
    if (editingId.value) {
      await api.updateUiNlCase(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await api.createUiNlCase(payload)
      ElMessage.success('创建成功')
    }
    editVisible.value = false
    await loadRecords()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: UiNlCase) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const actionText = next === 'ENABLED' ? '启用' : '停用'
  const ok = await ElMessageBox.confirm(`确认${actionText}「${row.title}」吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  await api.updateUiNlCase(row.id, {
    title: row.title,
    nlText: row.nlText,
    precondition: row.precondition,
    targetEnv: row.targetEnv,
    baseUrl: row.baseUrl,
    credentialRef: row.credentialRef,
    status: next,
    tagsJson: row.tagsJson,
  })
  ElMessage.success(`${actionText}成功`)
  await loadRecords()
}

async function onDelete(row: UiNlCase) {
  const ok = await ElMessageBox.confirm(`确认删除「${row.title}」吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  await api.deleteUiNlCase(row.id)
  ElMessage.success('已删除')
  await loadRecords()
}

function projectLabel(id: number) {
  const p = projects.value.find((x) => x.id === id)
  return p ? `${p.name}（${p.code}）` : `项目#${id}`
}

function versionLabel(id: number) {
  const v = versions.value.find((x) => x.id === id)
  return v ? `${v.versionNo}${v.name ? ` - ${v.name}` : ''}` : `版本#${id}`
}

function statusLabel(status?: string) {
  return dictStatusLabel(SWITCH_STATUS, status, '—')
}

function statusTagType(status?: string) {
  return dictStatusTagType(SWITCH_STATUS, status, 'info')
}

onMounted(async () => {
  await loadProjects()
  await loadVersions()
  await loadRecords()
})
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
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
        <el-select v-model="status" clearable placeholder="状态" style="width: 140px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="标题/内容关键词" style="width: 240px" />
        <el-button type="primary" @click="() => { pageNo = 1; loadRecords() }">查询</el-button>
        <el-button @click="() => { projectId=''; versionId=''; status=''; keyword=''; pageNo=1; loadRecords() }">重置</el-button>
        <el-button type="success" @click="openCreate">新增自然语言用例</el-button>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="records" :size="tableDensity" border stripe v-loading="loading" height="100%">
        <el-table-column prop="caseNo" label="编号" width="130" />
        <el-table-column label="项目" min-width="180">
          <template #default="{ row }">{{ projectLabel(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="版本" min-width="180">
          <template #default="{ row }">{{ versionLabel(row.versionId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="自然语言描述" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nlText }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-divider direction="vertical" />
              <el-button link type="warning" @click="toggleStatus(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
              <el-divider direction="vertical" />
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </div>
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
          @current-change="loadRecords"
          @size-change="() => { pageNo = 1; loadRecords() }"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="editVisible"
      :title="editingId ? '编辑自然语言用例' : '新增自然语言用例'"
      width="760px"
      :close-on-click-modal="false"
      class="fixed-footer-dialog"
      align-center
    >
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="项目">
            <el-select v-model="formProjectId" filterable placeholder="项目" :disabled="Boolean(editingId)">
              <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="版本">
            <el-select v-model="formVersionId" filterable placeholder="版本" :disabled="Boolean(editingId)">
              <el-option
                v-for="v in filteredFormVersions"
                :key="v.id"
                :label="`${v.versionNo}${v.name ? ` - ${v.name}` : ''}`"
                :value="String(v.id)"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标题">
          <el-input v-model="formTitle" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="自然语言描述">
          <el-input v-model="formNlText" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formStatus" style="width: 220px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="前置条件">
            <el-input v-model="formPrecondition" />
          </el-form-item>
          <el-form-item label="目标环境">
            <el-input v-model="formTargetEnv" placeholder="例如 SIT/UAT" />
          </el-form-item>
          <el-form-item label="目标地址">
            <el-input v-model="formBaseUrl" placeholder="https://example.com" />
          </el-form-item>
          <el-form-item label="凭据引用">
            <el-input v-model="formCredentialRef" placeholder="credential-key" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell { height: 100%; display: grid; grid-template-rows: auto 1fr; gap: 12px; }
.query-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.table-card { min-height: 0; display: flex; flex-direction: column; }
.table-card :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.pager { display: flex; justify-content: flex-end; padding-top: 10px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(220px, 1fr)); gap: 12px; }
.op-cell { display: inline-flex; align-items: center; justify-content: flex-start; width: 100%; }
</style>
