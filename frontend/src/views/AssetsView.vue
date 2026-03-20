<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadProps } from 'element-plus'
import { Document, Picture, Upload } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { api, type Project, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'
import { formatBytes } from '../utils/formatBytes'

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
/** 需求描述（手动）与需求文档（上传提取）二选一 */
const requirementInputMode = ref<'TEXT' | 'FILE'>('TEXT')
const createModePrototype = ref(false)
const createTitle = ref('')
const createContent = ref('')
const createReqFile = ref<File | null>(null)
const createProtoFile = ref<File | null>(null)
const creating = ref(false)

const reqUploadRef = ref<UploadInstance>()
const protoUploadRef = ref<UploadInstance>()

/** 编辑态：当前已入库的需求文档资产（用于展示文件名，非新选文件） */
const editingFileAsset = computed(() => {
  if (!isEditing.value) return null
  return editingAssets.value.find((a: { assetType?: string }) => a.assetType === 'FILE') ?? null
})

function clearDialogUploads() {
  reqUploadRef.value?.clearFiles()
  protoUploadRef.value?.clearFiles()
}

const onReqFileChange: UploadProps['onChange'] = (uploadFile) => {
  createReqFile.value = (uploadFile.raw as File) || null
}

const onReqFileRemove: UploadProps['onRemove'] = () => {
  createReqFile.value = null
}

const onReqExceed: UploadProps['onExceed'] = () => {
  ElMessage.warning('请先移除已选文件，再重新选择')
}

const onProtoFileChange: UploadProps['onChange'] = (uploadFile) => {
  createProtoFile.value = (uploadFile.raw as File) || null
}

const onProtoFileRemove: UploadProps['onRemove'] = () => {
  createProtoFile.value = null
}

const onProtoExceed: UploadProps['onExceed'] = () => {
  ElMessage.warning('请先移除已选图片，再重新选择')
}

/** 切回「手动描述」时清掉已选文档，避免误以为是双录入 */
watch(requirementInputMode, (m) => {
  if (m === 'TEXT') {
    createReqFile.value = null
    void nextTick(() => reqUploadRef.value?.clearFiles())
  }
})

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

/** 与新建弹窗「手动描述 / 文档提取」二选一逻辑对齐；混合为历史批次 */
function requirementEntryLabel(row: AssetGroupRow): string {
  if (row.hasText && row.hasFile) return '混合'
  if (row.hasText) return '手动'
  if (row.hasFile) return '文档'
  return '—'
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
          title: asset.assetType === 'TEXT' || asset.assetType === 'FILE' ? asset.title || '' : '',
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
      } else if (asset.assetType === 'FILE' && asset.title && !current.title) {
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
  requirementInputMode.value = 'TEXT'
  createModePrototype.value = false
  createProjectId.value = projectIdInput.value
  createVersionId.value = versionIdInput.value
  createVisible.value = true
  void nextTick(() => clearDialogUploads())
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
  const fileAsset = assets.find((a) => a.assetType === 'FILE')
  const hasPrototype = assets.some((a) => a.assetType === 'PROTOTYPE')
  if (fileAsset && !textAsset) {
    requirementInputMode.value = 'FILE'
    createTitle.value = fileAsset.title || row.title || ''
    createContent.value = fileAsset.content || ''
  } else {
    requirementInputMode.value = 'TEXT'
    createTitle.value = textAsset?.title || row.title || ''
    createContent.value = textAsset?.content || ''
  }
  createModePrototype.value = hasPrototype
  createVisible.value = true
  void nextTick(() => clearDialogUploads())
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

  if (!createTitle.value.trim()) {
    ElMessage.warning('请填写标题')
    return
  }

  if (requirementInputMode.value === 'TEXT') {
    if (!createContent.value.trim()) {
      ElMessage.warning('需求描述内容不能为空')
      return
    }
    for (const item of fileAssets) {
      await api.deleteAsset(item.id)
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
  } else {
    if (textAsset) {
      await api.deleteAsset(textAsset.id)
    }
    if (createReqFile.value) {
      for (const item of fileAssets) {
        await api.deleteAsset(item.id)
      }
      await api.uploadRequirementFile(versionId, createReqFile.value, relationCode, createTitle.value.trim())
    } else if (fileAssets.length === 0) {
      ElMessage.warning('请上传需求文档，或切换到「需求描述」模式')
      return
    } else {
      for (const item of fileAssets) {
        await api.updateAsset(item.id, { title: createTitle.value.trim() })
      }
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
  if (!createTitle.value.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (requirementInputMode.value === 'TEXT') {
    if (!createContent.value.trim()) {
      ElMessage.warning('需求描述内容不能为空')
      return
    }
  } else {
    if (!createReqFile.value) {
      ElMessage.warning('请上传需求文档（系统将提取正文入库）')
      return
    }
  }
  if (createModePrototype.value && !createProtoFile.value) {
    ElMessage.warning('已勾选附带原型图，请选择图片文件')
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
    if (requirementInputMode.value === 'TEXT') {
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
    } else {
      const reqAsset = await api.uploadRequirementFile(
        versionId,
        createReqFile.value!,
        batchRelationCode,
        createTitle.value.trim(),
      )
      if (!reqAsset?.id) {
        throw new Error('后端未返回需求文档资产数据')
      }
      if (reqAsset.relationCode) {
        batchRelationCode = reqAsset.relationCode
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
          <el-input v-model="keyword" placeholder="标题关键词" clearable style="width: 220px" />
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
      <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
      <el-table-column label="录入方式" width="92" align="center">
        <template #default="{ row }">
          <span v-if="!row.hasText && !row.hasFile">—</span>
          <el-tag
            v-else
            :type="row.hasText && row.hasFile ? 'warning' : row.hasText ? 'success' : 'info'"
            effect="plain"
            size="small"
          >
            {{ requirementEntryLabel(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="手动描述" width="92" align="center">
        <template #default="{ row }">{{ row.hasText ? '有' : '无' }}</template>
      </el-table-column>
      <el-table-column label="文档录入" width="92" align="center">
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

    <el-dialog
      v-model="createVisible"
      :title="createDialogTitle"
      width="720px"
      :close-on-click-modal="false"
      class="assets-create-dialog"
      destroy-on-close
    >
      <el-form label-position="top" class="asset-dialog-form">
        <el-card shadow="never" class="dialog-section-card">
          <template #header>
            <div class="section-card-header">
              <span class="section-title">基础信息</span>
              <el-text v-if="isEditing" type="info" size="small">项目、版本不可修改</el-text>
            </div>
          </template>
          <div class="create-grid">
            <el-form-item label="项目" required>
              <el-select
                v-model="createProjectId"
                filterable
                :clearable="!isEditing"
                :loading="projectsLoading"
                placeholder="请选择项目"
                :disabled="isEditing"
                @change="() => { if (!isEditing) loadVersionsForCreateProject() }"
              >
                <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
              </el-select>
            </el-form-item>
            <el-form-item label="版本" required>
              <el-select
                v-model="createVersionId"
                filterable
                :clearable="!isEditing"
                :loading="versionsLoading"
                placeholder="请选择版本"
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
          <el-form-item label="标题" required>
            <el-input
              v-model="createTitle"
              maxlength="120"
              show-word-limit
              clearable
              placeholder="列表展示用，与下方录入方式无关"
            />
          </el-form-item>
        </el-card>

        <el-card shadow="never" class="dialog-section-card">
          <template #header>
            <div class="section-card-header">
              <span class="section-title">
                <el-icon class="section-title-icon"><Document /></el-icon>
                需求内容
              </span>
              <el-text type="info" size="small">描述与文档二选一</el-text>
            </div>
          </template>

          <div class="mode-toggle-wrap">
            <el-radio-group v-model="requirementInputMode" class="mode-radio-group" size="default">
              <el-radio-button value="TEXT">手动输入描述</el-radio-button>
              <el-radio-button value="FILE">上传文档（自动提取正文）</el-radio-button>
            </el-radio-group>
          </div>

          <div v-if="requirementInputMode === 'TEXT'" class="mode-block-inner">
            <el-form-item label="需求描述正文" class="mb-0">
              <el-input
                v-model="createContent"
                type="textarea"
                :autosize="{ minRows: 7, maxRows: 16 }"
                placeholder="请输入需求说明、验收要点等"
              />
            </el-form-item>
          </div>

          <div v-else class="mode-block-inner">
            <el-alert
              v-if="isEditing && editingFileAsset && !createReqFile"
              type="info"
              :closable="false"
              show-icon
              class="current-asset-alert"
            >
              <template #title>已关联文档</template>
              <div class="current-file-line">
                <span class="current-file-name">{{ editingFileAsset.fileName || editingFileAsset.title || '未命名' }}</span>
                <el-text v-if="editingFileAsset.fileSize" type="info" size="small" class="file-size-tag">
                  {{ formatBytes(Number(editingFileAsset.fileSize)) }}
                </el-text>
              </div>
              <el-text type="info" size="small">不选新文件则保留当前文档；选择新文件将替换并重新提取正文。</el-text>
            </el-alert>

            <el-form-item label="需求文档" class="upload-form-item mb-0">
              <el-upload
                ref="reqUploadRef"
                class="asset-upload"
                :auto-upload="false"
                :limit="1"
                :on-change="onReqFileChange"
                :on-remove="onReqFileRemove"
                :on-exceed="onReqExceed"
              >
                <el-button type="primary" plain>
                  <el-icon class="btn-icon"><Upload /></el-icon>
                  {{ isEditing ? '更换文档' : '选择文档' }}
                </el-button>
                <template #tip>
                  <div class="upload-tip">
                    支持 doc、docx、pdf、txt、md 等；保存后仅保留提取的正文，服务器不长期保留原件。
                  </div>
                </template>
              </el-upload>
              <div v-if="createReqFile" class="picked-file-chip">
                <el-tag type="success" effect="plain" size="large">
                  新选：{{ createReqFile.name }} · {{ formatBytes(createReqFile.size) }}
                </el-tag>
              </div>
            </el-form-item>

            <el-form-item
              v-if="isEditing && createContent.trim()"
              label="已提取正文（只读）"
              class="mb-0"
            >
              <el-input
                v-model="createContent"
                type="textarea"
                :autosize="{ minRows: 5, maxRows: 12 }"
                readonly
                class="readonly-extract"
              />
            </el-form-item>
          </div>
        </el-card>

        <el-card shadow="never" class="dialog-section-card">
          <template #header>
            <div class="section-card-header section-card-header--switch">
              <span class="section-title">
                <el-icon class="section-title-icon"><Picture /></el-icon>
                原型图
              </span>
              <el-switch
                v-model="createModePrototype"
                inline-prompt
                active-text="附带"
                inactive-text="不附带"
              />
            </div>
          </template>
          <el-text v-if="!createModePrototype" type="info" size="small" class="proto-off-hint">
            关闭时不保存原型图；若编辑时关闭，将删除已有原型图资产。
          </el-text>
          <div v-else class="mode-block-inner">
            <el-form-item label="原型图附件" class="upload-form-item mb-0">
              <el-upload
                ref="protoUploadRef"
                class="asset-upload"
                :auto-upload="false"
                :limit="1"
                accept="image/*"
                :on-change="onProtoFileChange"
                :on-remove="onProtoFileRemove"
                :on-exceed="onProtoExceed"
              >
                <el-button type="primary" plain>
                  <el-icon class="btn-icon"><Upload /></el-icon>
                  {{ isEditing ? '更换图片' : '选择图片' }}
                </el-button>
                <template #tip>
                  <div class="upload-tip">支持 png、jpg、webp 等常见图片格式。</div>
                </template>
              </el-upload>
              <div v-if="createProtoFile" class="picked-file-chip">
                <el-tag type="success" effect="plain" size="large">
                  新选：{{ createProtoFile.name }} · {{ formatBytes(createProtoFile.size) }}
                </el-tag>
              </div>
              <el-alert
                v-if="isEditing && createModePrototype && !createProtoFile"
                type="info"
                :closable="false"
                show-icon
                class="proto-edit-hint"
              >
                未选择新图片则保留当前原型图；若需删除请关闭上方「附带」开关后保存。
              </el-alert>
            </el-form-item>
          </div>
        </el-card>
      </el-form>
      <template #footer>
        <div class="dialog-footer-bar">
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="submitCreate">{{ createDialogConfirmText }}</el-button>
        </div>
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.asset-dialog-form {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.dialog-section-card {
  margin-bottom: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.dialog-section-card:last-of-type {
  margin-bottom: 0;
}

.dialog-section-card :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.dialog-section-card :deep(.el-card__body) {
  padding: 16px;
}

.section-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.section-card-header--switch {
  align-items: center;
}

.section-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.section-title-icon {
  font-size: 18px;
  color: var(--el-color-primary);
}

.mode-toggle-wrap {
  margin-bottom: 16px;
}

.mode-radio-group {
  display: flex;
  flex-wrap: wrap;
  width: 100%;
}

.mode-radio-group :deep(.el-radio-button) {
  flex: 1;
  min-width: 0;
}

.mode-radio-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.mode-block-inner {
  padding-top: 4px;
}

.current-asset-alert {
  margin-bottom: 16px;
}

.current-file-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.current-file-name {
  font-weight: 500;
  word-break: break-all;
}

.file-size-tag {
  flex-shrink: 0;
}

.upload-form-item {
  margin-bottom: 0;
}

.asset-upload :deep(.el-upload) {
  width: auto;
}

.upload-tip {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.55;
  max-width: 560px;
}

.picked-file-chip {
  margin-top: 12px;
}

.btn-icon {
  margin-right: 6px;
  vertical-align: middle;
}

.proto-off-hint {
  display: block;
  line-height: 1.5;
}

.proto-edit-hint {
  margin-top: 12px;
}

.readonly-extract :deep(textarea) {
  background: var(--el-fill-color-light);
}

.dialog-footer-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.asset-dialog-form :deep(.mb-0.el-form-item) {
  margin-bottom: 0;
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

/* 新增/编辑“需求资产”弹窗：body 滚动，footer（取消/保存）固定可见 */
:deep(.assets-create-dialog .el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 40px);
}

:deep(.assets-create-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

:deep(.assets-create-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  background: #fff;
  border-top: 1px solid #f0f2f5;
}
</style>
