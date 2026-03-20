<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { api, type Project, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'

const tableDensity = inject('tableDensity', ref<'default' | 'small'>('default'))
const router = useRouter()

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const projectsLoading = ref(false)
const versionsLoading = ref(false)

const projectIdInput = ref('')
const versionIdInput = ref('')
const keyword = ref('')
const loading = ref(false)
type AssetGroupRow = {
  /** 分组代表资产 ID：同批次多条时取组内最小 id，便于列表展示 */
  id: number
  /** 组内各条资产的 assetCode（去重） */
  assetCodes: string[]
  relationCode: string
  rawRelationCode?: string
  legacyAssetId?: number
  projectId: number
  versionId: number
  projectName?: string | null
  projectCode?: string | null
  versionName?: string | null
  versionNo?: string | null
  title: string
  hasText: boolean
  hasFile: boolean
  hasPrototype: boolean
  createdAt?: string
  updatedAt?: string
}
const allGroupRecords = ref<AssetGroupRow[]>([])
const records = ref<AssetGroupRow[]>([])
const selectedRelationCodes = ref<string[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)

const createVisible = ref(false)
const createDialogTitle = ref('新增需求资产')
const createDialogConfirmText = ref('确认创建')
const isEditing = ref(false)
const editingRow = ref<AssetGroupRow | null>(null)
const editingAssets = ref<any[]>([])
const createProjectId = ref('')
const createVersionId = ref('')
const createModeText = ref(true)
const createModeReqDoc = ref(true)
const createModePrototype = ref(true)
const createTitle = ref('')
const createContent = ref('')
const createReqFile = ref<File | null>(null)
const createProtoFile = ref<File | null>(null)
const creating = ref(false)

const safeProjectId = computed(() => {
  const value = Number(projectIdInput.value.trim())
  return Number.isInteger(value) && value > 0 ? value : 0
})

const safeVersionId = computed(() => {
  const value = Number(versionIdInput.value.trim())
  return Number.isInteger(value) && value > 0 ? value : 0
})

const safeCreateProjectId = computed(() => {
  const value = Number(createProjectId.value.trim())
  return Number.isInteger(value) && value > 0 ? value : 0
})

const safeCreateVersionId = computed(() => {
  const value = Number(createVersionId.value.trim())
  return Number.isInteger(value) && value > 0 ? value : 0
})

function projectDisplay(row: AssetGroupRow): string {
  if (row.projectName || row.projectCode) {
    const name = row.projectName?.trim() || '—'
    const code = row.projectCode?.trim() || '—'
    return `${name}（${code}）`
  }
  return `项目#${row.projectId}`
}

function versionDisplay(row: AssetGroupRow): string {
  if (row.versionNo || row.versionName) {
    const no = row.versionNo?.trim() || '—'
    const vn = row.versionName?.trim() || '未命名'
    return `${no} · ${vn}`
  }
  return `版本#${row.versionId}`
}

async function loadProjects() {
  projectsLoading.value = true
  try {
    const data = await api.getProjects({ pageNo: 1, pageSize: 100 })
    projects.value = data.records
  } catch (error: any) {
    ElMessage.error(error.message || '加载项目失败')
  } finally {
    projectsLoading.value = false
  }
}

async function loadVersionsForProject(resetVersionSelection = true) {
  versions.value = []
  if (resetVersionSelection) {
    versionIdInput.value = ''
  }
  versionsLoading.value = true
  try {
    const data = await api.getAllVersions({
      projectId: safeProjectId.value || undefined,
      pageNo: 1,
      pageSize: 200,
    })
    versions.value = data.records
  } catch (error: any) {
    ElMessage.error(error.message || '加载版本失败')
  } finally {
    versionsLoading.value = false
  }
}

async function loadVersionsForCreateProject(resetVersionSelection = true) {
  versions.value = []
  if (resetVersionSelection) {
    createVersionId.value = ''
  }
  if (!safeCreateProjectId.value) {
    return
  }
  versionsLoading.value = true
  try {
    const data = await api.getVersions(safeCreateProjectId.value, { pageNo: 1, pageSize: 100 })
    versions.value = data.records
  } catch (error: any) {
    ElMessage.error(error.message || '加载版本失败')
  } finally {
    versionsLoading.value = false
  }
}

async function loadAssets() {
  loading.value = true
  try {
    const data = await api.getAllAssets({
      pageNo: 1,
      pageSize: 1000,
      projectId: safeProjectId.value || undefined,
      versionId: safeVersionId.value || undefined,
      keyword: keyword.value,
    })
    const grouped = new Map<string, AssetGroupRow>()
    data.records.forEach((asset) => {
      const isBatch = asset.relationCode?.startsWith('RC-')
      const groupKey = isBatch ? asset.relationCode : `LEGACY-${asset.id}`
      const current = grouped.get(groupKey)
      if (!current) {
        grouped.set(groupKey, {
          id: asset.id,
          assetCodes: asset.assetCode ? [asset.assetCode] : [],
          relationCode: groupKey,
          rawRelationCode: asset.relationCode || undefined,
          legacyAssetId: isBatch ? undefined : asset.id,
          projectId: asset.projectId,
          versionId: asset.versionId,
          projectName: asset.projectName,
          projectCode: asset.projectCode,
          versionName: asset.versionName,
          versionNo: asset.versionNo,
          title: asset.assetType === 'TEXT' ? asset.title || '' : '',
          hasText: asset.assetType === 'TEXT',
          hasFile: asset.assetType === 'FILE',
          hasPrototype: asset.assetType === 'PROTOTYPE',
          createdAt: asset.createdAt,
          updatedAt: asset.updatedAt,
        })
        return
      }
      current.id = Math.min(current.id, asset.id)
      if (asset.assetCode) {
        const set = new Set(current.assetCodes)
        if (!set.has(asset.assetCode)) {
          current.assetCodes.push(asset.assetCode)
        }
      }
      current.hasText = current.hasText || asset.assetType === 'TEXT'
      current.hasFile = current.hasFile || asset.assetType === 'FILE'
      current.hasPrototype = current.hasPrototype || asset.assetType === 'PROTOTYPE'
      if (asset.assetType === 'TEXT' && asset.title) {
        current.title = asset.title
      } else if (!current.title && asset.title) {
        current.title = asset.title
      }
      if (
        (asset.createdAt || '') &&
        (!current.createdAt || (current.createdAt || '') === '' || (asset.createdAt || '') < (current.createdAt || ''))
      ) {
        current.createdAt = asset.createdAt
      }
      if ((asset.updatedAt || '') > (current.updatedAt || '')) {
        current.updatedAt = asset.updatedAt
      }
      if (!current.projectName && asset.projectName) {
        current.projectName = asset.projectName
        current.projectCode = asset.projectCode
      }
      if (!current.versionNo && !current.versionName && (asset.versionNo || asset.versionName)) {
        current.versionNo = asset.versionNo
        current.versionName = asset.versionName
      }
    })
    allGroupRecords.value = Array.from(grouped.values())
    total.value = allGroupRecords.value.length
    applyPage()
  } catch (error: any) {
    ElMessage.error(error.message || '加载资产失败')
  } finally {
    loading.value = false
  }
}

function applyPage() {
  const start = (pageNo.value - 1) * pageSize.value
  records.value = allGroupRecords.value.slice(start, start + pageSize.value)
}

function onSearch() {
  pageNo.value = 1
  loadAssets()
}

async function onResetFilters() {
  projectIdInput.value = ''
  versionIdInput.value = ''
  keyword.value = ''
  pageNo.value = 1
  await loadVersionsForProject(false)
  await loadAssets()
}

function onPageNoChange(value: number) {
  pageNo.value = value
  applyPage()
}

function onPageSizeChange(value: number) {
  pageSize.value = value
  pageNo.value = 1
  applyPage()
}

async function onProjectChange() {
  await loadVersionsForProject(true)
}

function onVersionChange() {
}

function openCreateDialog() {
  isEditing.value = false
  editingRow.value = null
  editingAssets.value = []
  createDialogTitle.value = '新增需求资产'
  createDialogConfirmText.value = '确认创建'
  createTitle.value = ''
  createContent.value = ''
  createReqFile.value = null
  createProtoFile.value = null
  createModeText.value = true
  createModeReqDoc.value = true
  createModePrototype.value = true
  createProjectId.value = projectIdInput.value
  createVersionId.value = versionIdInput.value
  createVisible.value = true
  if (safeCreateProjectId.value) {
    loadVersionsForCreateProject()
  }
}

async function loadAssetsForRow(row: AssetGroupRow) {
  if (row.legacyAssetId) {
    const item = await api.getAsset(row.legacyAssetId)
    return [item]
  }
  const code = row.rawRelationCode || row.relationCode
  const data = await api.getAllAssets({ relationCode: code, pageNo: 1, pageSize: 200 })
  return data.records
}

async function openEditDialog(row: AssetGroupRow) {
  isEditing.value = true
  editingRow.value = row
  createDialogTitle.value = '修改需求资产'
  createDialogConfirmText.value = '确认修改'
  createReqFile.value = null
  createProtoFile.value = null
  createProjectId.value = String(row.projectId)
  createVersionId.value = String(row.versionId)
  // 加载目标项目的版本列表，但保留当前版本号（确保下拉框展示版本号）
  await loadVersionsForCreateProject(false)
  const assets = await loadAssetsForRow(row)
  editingAssets.value = assets
  const textAsset = assets.find((a) => a.assetType === 'TEXT')
  const hasFile = assets.some((a) => a.assetType === 'FILE')
  const hasPrototype = assets.some((a) => a.assetType === 'PROTOTYPE')
  createModeText.value = Boolean(textAsset)
  createModeReqDoc.value = hasFile
  createModePrototype.value = hasPrototype
  createTitle.value = textAsset?.title || row.title || ''
  createContent.value = textAsset?.content || ''
  createVisible.value = true
}

function getBatchRelationCodeForEdit() {
  const first = editingAssets.value[0]
  if (first?.relationCode && first.relationCode.startsWith('RC-')) {
    return first.relationCode
  }
  return `RC-MANUAL-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`
}

async function submitEdit() {
  const versionId = safeCreateVersionId.value
  const currentAssets = editingAssets.value
  const textAsset = currentAssets.find((a) => a.assetType === 'TEXT')
  const fileAssets = currentAssets.filter((a) => a.assetType === 'FILE')
  const prototypeAssets = currentAssets.filter((a) => a.assetType === 'PROTOTYPE')
  let relationCode = getBatchRelationCodeForEdit()

  if (createModeText.value) {
    if (!createTitle.value.trim() || !createContent.value.trim()) {
      ElMessage.warning('需求描述：标题和内容不能为空')
      return
    }
    if (textAsset) {
      await api.updateAsset(textAsset.id, { title: createTitle.value.trim(), content: createContent.value })
      relationCode = textAsset.relationCode || relationCode
    } else {
      const newText = await api.createTextAsset(versionId, {
        title: createTitle.value.trim(),
        content: createContent.value,
        relationCode,
      })
      relationCode = newText.relationCode || relationCode
    }
  } else if (textAsset) {
    await api.deleteAsset(textAsset.id)
  }

  if (createModeReqDoc.value) {
    if (createReqFile.value) {
      for (const item of fileAssets) {
        await api.deleteAsset(item.id)
      }
      await api.uploadRequirementFile(versionId, createReqFile.value, relationCode)
    } else if (fileAssets.length === 0) {
      ElMessage.warning('请上传需求文档附件')
      return
    }
  } else {
    for (const item of fileAssets) {
      await api.deleteAsset(item.id)
    }
  }

  if (createModePrototype.value) {
    if (createProtoFile.value) {
      for (const item of prototypeAssets) {
        await api.deleteAsset(item.id)
      }
      await api.uploadPrototypeFile(versionId, createProtoFile.value, relationCode)
    } else if (prototypeAssets.length === 0) {
      ElMessage.warning('请上传原型图附件')
      return
    }
  } else {
    for (const item of prototypeAssets) {
      await api.deleteAsset(item.id)
    }
  }
}

async function submitCreate() {
  if (!safeCreateProjectId.value || !safeCreateVersionId.value) {
    ElMessage.warning('请选择项目和版本')
    return
  }
  if (!createModeText.value && !createModeReqDoc.value && !createModePrototype.value) {
    ElMessage.warning('请至少选择一种资产类型')
    return
  }
  if (createModeText.value) {
    if (!createTitle.value.trim() || !createContent.value.trim()) {
      ElMessage.warning('需求描述：标题和内容不能为空')
      return
    }
  }
  if (createModeReqDoc.value && !createReqFile.value) {
    ElMessage.warning('请上传需求文档附件')
    return
  }
  if (createModePrototype.value && !createProtoFile.value) {
    ElMessage.warning('请上传原型图附件')
    return
  }
  creating.value = true
  try {
    if (isEditing.value) {
      await submitEdit()
      ElMessage.success('修改成功')
      createVisible.value = false
      await loadAssets()
      return
    }

    const versionId = safeCreateVersionId.value
    let batchRelationCode = `RC-MANUAL-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`
    const actions: string[] = []
    if (createModeText.value) {
      const textAsset = await api.createTextAsset(versionId, {
        title: createTitle.value.trim(),
        content: createContent.value,
        relationCode: batchRelationCode,
      })
      if (!textAsset?.id) {
        throw new Error('后端未返回文本资产数据')
      }
      if (textAsset.relationCode) {
        batchRelationCode = textAsset.relationCode
      }
      actions.push('需求描述')
    }
    if (createModeReqDoc.value && createReqFile.value) {
      const reqAsset = await api.uploadRequirementFile(versionId, createReqFile.value, batchRelationCode)
      if (!reqAsset?.id) {
        throw new Error('后端未返回需求文档资产数据')
      }
      actions.push('需求文档')
    }
    if (createModePrototype.value && createProtoFile.value) {
      const protoAsset = await api.uploadPrototypeFile(versionId, createProtoFile.value, batchRelationCode)
      if (!protoAsset?.id) {
        throw new Error('后端未返回原型图资产数据')
      }
      actions.push('原型图')
    }
    ElMessage.success(`创建成功：${actions.join(' + ')}`)
    createVisible.value = false
    await loadAssets()
  } catch (error: any) {
    ElMessage.error(error.message || '新增失败')
  } finally {
    creating.value = false
  }
}

function onPickReqFile(file: File) {
  createReqFile.value = file
}

function onPickProtoFile(file: File) {
  createProtoFile.value = file
}

function openDetail(row: AssetGroupRow) {
  router.push({ path: '/assets/detail', query: { relationCode: row.relationCode } })
}

function onSelectionChange(rows: AssetGroupRow[]) {
  selectedRelationCodes.value = rows.map((r) => r.relationCode)
}

async function onDeleteOne(row: AssetGroupRow) {
  const tip = row.title?.trim() ? `「${row.title}」` : `批次 ${row.relationCode}`
  const ok = await ElMessageBox.confirm(`确认删除该需求资产吗？${tip}`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.batchDeleteAssets([row.relationCode])
    ElMessage.success('已删除')
    await loadAssets()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

async function onBatchDelete() {
  if (selectedRelationCodes.value.length === 0) {
    ElMessage.warning('请先勾选要删除的数据')
    return
  }
  const ok = await ElMessageBox.confirm(`确认批量删除 ${selectedRelationCodes.value.length} 条需求资产吗？`, '提示', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok) {
    return
  }
  try {
    await api.batchDeleteAssets(selectedRelationCodes.value)
    ElMessage.success('批量删除成功')
    selectedRelationCodes.value = []
    await loadAssets()
  } catch (error: any) {
    ElMessage.error(error.message || '批量删除失败')
  }
}

onMounted(async () => {
  projectIdInput.value = ''
  versionIdInput.value = ''
  await loadProjects()
  await loadVersionsForProject(false)
  await loadAssets()
})
</script>

<template>
  <div class="assets-page">
    <el-card shadow="never" class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-select
            v-model="projectIdInput"
            filterable
            clearable
            :loading="projectsLoading"
            placeholder="选择项目"
            style="width: 240px"
            @change="onProjectChange"
          >
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="String(p.id)" />
          </el-select>
          <el-select
            v-model="versionIdInput"
            filterable
            clearable
            :loading="versionsLoading"
            placeholder="选择版本"
            style="width: 240px"
            @change="onVersionChange"
          >
            <el-option
              v-for="v in versions"
              :key="v.id"
              :label="`${v.versionNo}${v.name ? ' - ' + v.name : ''}`"
              :value="String(v.id)"
            />
          </el-select>
          <el-input v-model="keyword" placeholder="标题" clearable style="width: 220px" />
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onResetFilters">重置</el-button>
          <el-button type="success" @click="openCreateDialog">新增资产</el-button>
          <el-button type="danger" plain :disabled="selectedRelationCodes.length === 0" @click="onBatchDelete">批量删除</el-button>
        </div>
      </div>
    </el-card>

    <el-table :data="records" :size="tableDensity" v-loading="loading" border class="table-area" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="ID" width="88" />
      <el-table-column label="资产编码" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ (row.assetCodes || []).join(' · ') || '—' }}</template>
      </el-table-column>
      <el-table-column prop="relationCode" label="关联批次码" min-width="260" />
      <el-table-column label="项目" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ projectDisplay(row) }}</template>
      </el-table-column>
      <el-table-column label="版本（版本号 · 名称）" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ versionDisplay(row) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="260" />
      <el-table-column label="需求描述" width="100">
        <template #default="{ row }">{{ row.hasText ? '有' : '无' }}</template>
      </el-table-column>
      <el-table-column label="需求文档" width="100">
        <template #default="{ row }">{{ row.hasFile ? '有' : '无' }}</template>
      </el-table-column>
      <el-table-column label="原型图" width="100">
        <template #default="{ row }">{{ row.hasPrototype ? '有' : '无' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">修改</el-button>
          <el-divider direction="vertical" />
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-divider direction="vertical" />
          <el-button link type="danger" @click="onDeleteOne(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageNoChange"
        @size-change="onPageSizeChange"
      />
    </div>

    <el-dialog v-model="createVisible" :title="createDialogTitle" width="860px" :close-on-click-modal="false">
      <el-form label-position="top">
        <div class="create-grid">
          <el-form-item label="项目（必选）">
            <el-select
              v-model="createProjectId"
              filterable
              :clearable="!isEditing"
              :loading="projectsLoading"
              placeholder="选择项目"
              :disabled="isEditing"
              @change="() => { if (!isEditing) loadVersionsForCreateProject() }"
            >
              <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="版本（必选）">
            <el-select
              v-model="createVersionId"
              filterable
              :clearable="!isEditing"
              :loading="versionsLoading"
              placeholder="选择版本"
              :disabled="isEditing"
            >
              <el-option
                v-for="v in versions"
                :key="v.id"
                :label="`${v.versionNo}${v.name ? ' - ' + v.name : ''}`"
                :value="String(v.id)"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-divider content-position="left">选择录入组合</el-divider>
        <el-checkbox v-model="createModeText">需求描述（文本）</el-checkbox>
        <el-checkbox v-model="createModeReqDoc">需求文档（附件）</el-checkbox>
        <el-checkbox v-model="createModePrototype">原型图（图片）</el-checkbox>

        <div v-if="createModeText" class="mode-block">
          <el-form-item label="标题">
            <el-input v-model="createTitle" maxlength="120" show-word-limit />
          </el-form-item>
          <el-form-item label="需求描述">
            <el-input v-model="createContent" type="textarea" :rows="8" />
          </el-form-item>
        </div>

        <div v-if="createModeReqDoc" class="mode-block">
          <el-form-item label="需求文档（doc/docx/pdf/txt/md 等）">
            <input
              class="file-input"
              type="file"
              @change="(e: Event) => { const f=(e.target as HTMLInputElement).files?.[0]; if (f) onPickReqFile(f) }"
            />
            <div v-if="createReqFile" class="file-meta">已选择：{{ createReqFile.name }}（{{ createReqFile.size }} bytes）</div>
          </el-form-item>
        </div>

        <div v-if="createModePrototype" class="mode-block">
          <el-form-item label="原型图（png/jpg/webp 等）">
            <input
              class="file-input"
              type="file"
              accept="image/*"
              @change="(e: Event) => { const f=(e.target as HTMLInputElement).files?.[0]; if (f) onPickProtoFile(f) }"
            />
            <div v-if="createProtoFile" class="file-meta">已选择：{{ createProtoFile.name }}（{{ createProtoFile.size }} bytes）</div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">{{ createDialogConfirmText }}</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.assets-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.query-card {
  margin-bottom: 12px;
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

.create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}

.mode-block {
  margin-top: 10px;
}

.file-input {
  display: block;
}

.file-meta {
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
}

.table-area {
  flex: 1;
  min-height: 0;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
