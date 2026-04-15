<script setup lang="ts">
import { computed, inject, onMounted, onUnmounted, ref, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, type GenerationTask, type Project, type Version } from '../api/api'
import { useRouter } from 'vue-router'
import { formatDateTime } from '../utils/formatDateTime'
import {
  GENERATION_TASK_STATUS,
  statusLabel as dictStatusLabel,
  statusTagType as dictStatusTagType,
} from '../utils/statusDictionary'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const router = useRouter()

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const allVersions = ref<Version[]>([])
const projectsLoading = ref(false)
const versionsLoading = ref(false)

const projectId = ref('')
const versionId = ref('')
const status = ref('')
const loading = ref(false)
const pollingTimer = ref<number | null>(null)
const loadingTasks = ref(false)
const records = ref<GenerationTask[]>([])
const selectedTaskIds = ref<number[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const createVisible = ref(false)
const createProjectId = ref('')
const createVersionId = ref('')
const modelConfigs = ref<Array<{ id: number; name: string; provider: string; modelKey: string; status: string }>>([])
const promptTemplates = ref<Array<{ id: number; name: string; scopeType: string; scopeId?: number; versionNo: number; status: string }>>([])
const createModelConfigId = ref('')
const createPromptTemplateId = ref('')
const createStrategy = ref('DEFAULT')
const createCaseLimit = ref('100')
const createCaseCategory = ref<'FUNCTIONAL' | 'API' | ''>('')
const createRequirementAssetsLoading = ref(false)
/** 新建任务：选择参与生成的需求资产批次 relation_code（可多选） */
const createReferenceAssetRelationCodes = ref<string[]>([])
const createRequirementAssetOptions = ref<Array<{ relationCode: string; label: string }>>([])
const creating = ref(false)

const editVisible = ref(false)
const editTaskId = ref(0)
const editTaskNo = ref('')
const editProjectId = ref('')
const editVersionId = ref('')
const editModelConfigId = ref('')
const editPromptTemplateId = ref('')
const editStrategy = ref('DEFAULT')
const editCaseLimit = ref('100')
const editCaseCategory = ref<'FUNCTIONAL' | 'API'>('FUNCTIONAL')
const editRequirementAssetsLoading = ref(false)
/** 编辑任务：选择参与生成的需求资产批次 relation_code（可多选） */
const editReferenceAssetRelationCodes = ref<string[]>([])
const editRequirementAssetOptions = ref<Array<{ relationCode: string; label: string }>>([])
const editing = ref(false)

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

const projectNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const p of projects.value) {
    map.set(p.id, `${p.name}（${p.code}）`)
  }
  return map
})

const versionNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const v of allVersions.value) {
    map.set(v.id, `${v.name || '未命名版本'}（${v.versionNo}）`)
  }
  return map
})

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

async function loadAllVersions() {
  try {
    const data = await api.getAllVersions({ pageNo: 1, pageSize: 500 })
    allVersions.value = data.records
  } catch (e: any) {
    // 不阻断页面展示
    console.warn('load all versions failed', e?.message || e)
  }
}

async function loadVersionsForProject(pid: number) {
  versions.value = []
  if (!pid) return
  versionsLoading.value = true
  try {
    const data = await api.getVersions(pid, { pageNo: 1, pageSize: 200 })
    versions.value = data.records
  } catch (e: any) {
    ElMessage.error(e.message || '加载版本失败')
  } finally {
    versionsLoading.value = false
  }
}

async function loadRequirementAssetsForCreateVersion(pid: number, vid: number) {
  createRequirementAssetsLoading.value = true
  try {
    if (!pid || !vid) {
      createRequirementAssetOptions.value = []
      createReferenceAssetRelationCodes.value = []
      return
    }

    const data = await api.getAllAssets({
      pageNo: 1,
      pageSize: 1000,
      projectId: pid,
      versionId: vid,
      keyword: '',
    })

    // 按 relationCode 分组（一个批次可能包含 TEXT/FILE/PROTOTYPE 多条记录）
    const bestLabelByRelation = new Map<
      string,
      { relationCode: string; bestAssetCode: string; bestTitle: string; hasText: boolean }
    >()

    for (const a of data.records) {
      const rc = a.relationCode
      if (!rc) continue
      const title = (a.title && String(a.title).trim()) || (a.fileName && String(a.fileName).trim()) || ''
      const assetCode = a.assetCode || ''
      if (!assetCode) continue
      const isText = a.assetType === 'TEXT'

      const cur = bestLabelByRelation.get(rc)
      if (!cur) {
        bestLabelByRelation.set(rc, {
          relationCode: rc,
          bestAssetCode: assetCode,
          bestTitle: title,
          hasText: isText,
        })
        continue
      }

      // TEXT 优先：如果已有 TEXT，就不再被 FILE/PROTOTYPE 覆盖；否则用更新到的 TEXT
      if (isText && !cur.hasText) {
        cur.bestAssetCode = assetCode
        cur.bestTitle = title
        cur.hasText = true
      } else if (!cur.hasText && !isText) {
        // 仍然没有 TEXT：保留第一条即可（无则可选）
      }
    }

    const options = Array.from(bestLabelByRelation.values()).map((x) => {
      const t = x.bestTitle ? x.bestTitle : '—'
      return {
        relationCode: x.relationCode,
        label: `${x.bestAssetCode} · ${t}`,
      }
    })

    createRequirementAssetOptions.value = options
    // 不默认勾选：由用户手动选择要参与生成的需求资产
    createReferenceAssetRelationCodes.value = []
  } catch (e: any) {
    ElMessage.error(e.message || '加载需求资产失败')
    createRequirementAssetOptions.value = []
    createReferenceAssetRelationCodes.value = []
  } finally {
    createRequirementAssetsLoading.value = false
  }
}

async function loadRequirementAssetsForEditVersion(pid: number, vid: number, selectedRelationCodes?: string[]) {
  editRequirementAssetsLoading.value = true
  try {
    if (!pid || !vid) {
      editRequirementAssetOptions.value = []
      editReferenceAssetRelationCodes.value = []
      return
    }

    const data = await api.getAllAssets({
      pageNo: 1,
      pageSize: 1000,
      projectId: pid,
      versionId: vid,
      keyword: '',
    })

    // 按 relationCode 分组（一个批次可能包含 TEXT/FILE/PROTOTYPE 多条记录）
    const bestLabelByRelation = new Map<
      string,
      { relationCode: string; bestAssetCode: string; bestTitle: string; hasText: boolean }
    >()

    for (const a of data.records) {
      const rc = a.relationCode
      if (!rc) continue
      const title = (a.title && String(a.title).trim()) || (a.fileName && String(a.fileName).trim()) || ''
      const assetCode = a.assetCode || ''
      if (!assetCode) continue
      const isText = a.assetType === 'TEXT'

      const cur = bestLabelByRelation.get(rc)
      if (!cur) {
        bestLabelByRelation.set(rc, {
          relationCode: rc,
          bestAssetCode: assetCode,
          bestTitle: title,
          hasText: isText,
        })
        continue
      }

      // TEXT 优先：如果已有 TEXT，就不再被 FILE/PROTOTYPE 覆盖；否则用更新到的 TEXT
      if (isText && !cur.hasText) {
        cur.bestAssetCode = assetCode
        cur.bestTitle = title
        cur.hasText = true
      } else if (!cur.hasText && !isText) {
        // 仍然没有 TEXT：保留第一条即可（无则可选）
      }
    }

    const options = Array.from(bestLabelByRelation.values()).map((x) => {
      const t = x.bestTitle ? x.bestTitle : '—'
      return {
        relationCode: x.relationCode,
        label: `${x.bestAssetCode} · ${t}`,
      }
    })

    editRequirementAssetOptions.value = options
    const normalized = Array.isArray(selectedRelationCodes) ? selectedRelationCodes.filter((s) => typeof s === 'string' && s.trim()) : []
    editReferenceAssetRelationCodes.value = normalized
  } catch (e: any) {
    ElMessage.error(e.message || '加载需求资产失败')
    editRequirementAssetOptions.value = []
    editReferenceAssetRelationCodes.value = []
  } finally {
    editRequirementAssetsLoading.value = false
  }
}

async function loadTasks() {
  if (loadingTasks.value) {
    return
  }
  loadingTasks.value = true
  loading.value = true
  try {
    const data = await api.getGenerationTasks({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: safeProjectId.value || undefined,
      versionId: safeVersionId.value || undefined,
      status: status.value || undefined,
    })
    records.value = data.records
    total.value = data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载任务失败')
  } finally {
    loading.value = false
    loadingTasks.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  loadTasks()
}

async function onResetFilters() {
  projectId.value = ''
  versionId.value = ''
  status.value = ''
  versions.value = []
  pageNo.value = 1
  await loadTasks()
}

function openCreate() {
  createProjectId.value = ''
  createVersionId.value = ''
  versions.value = []
  createModelConfigId.value = ''
  createPromptTemplateId.value = ''
  createStrategy.value = 'DEFAULT'
  createCaseLimit.value = '100'
  createCaseCategory.value = ''
  createRequirementAssetsLoading.value = false
  createRequirementAssetOptions.value = []
  createReferenceAssetRelationCodes.value = []
  createVisible.value = true

  Promise.all([
    api.getModelConfigs({ status: 'ENABLED' }),
    api.getPromptTemplates({ status: 'ENABLED', scopeType: 'GLOBAL' }),
  ])
    .then(([models, prompts]) => {
      modelConfigs.value = models.map((m) => ({
        id: m.id,
        name: m.name,
        provider: m.provider,
        modelKey: m.modelKey,
        status: m.status,
      }))
      promptTemplates.value = prompts.map((p) => ({
        id: p.id,
        name: p.name,
        scopeType: p.scopeType,
        scopeId: p.scopeId,
        versionNo: p.versionNo,
        status: p.status,
      }))
      if (!createModelConfigId.value && modelConfigs.value.length) {
        createModelConfigId.value = String(modelConfigs.value[0].id)
      }
      if (!createModelConfigId.value) {
        createModelConfigId.value = '1'
      }
    })
    .catch((e: any) => {
      ElMessage.error(e?.message || '加载模型/Prompt失败')
    })
}

async function submitCreate() {
  if (!safeCreateProjectId.value || !safeCreateVersionId.value) {
    ElMessage.warning('请选择项目和版本')
    return
  }
  if (!createCaseCategory.value) {
    ElMessage.warning('请选择用例类型')
    return
  }
  if (!createPromptTemplateId.value) {
    ElMessage.warning('请选择 Prompt 模板')
    return
  }
  if (!createReferenceAssetRelationCodes.value.length) {
    ElMessage.warning('请选择需求资产（可多选）')
    return
  }
  const modelId = Number(createModelConfigId.value)
  const promptId = Number(createPromptTemplateId.value)
  const caseLimit = Number(createCaseLimit.value)
  if (!Number.isInteger(modelId) || modelId <= 0 || !Number.isInteger(promptId) || promptId <= 0) {
    ElMessage.warning('模型ID/PromptID不合法（当前MVP用数字）')
    return
  }
  creating.value = true
  try {
    await api.submitGenerationTask({
      projectId: safeCreateProjectId.value,
      versionId: safeCreateVersionId.value,
      modelConfigId: modelId,
      promptTemplateId: promptId,
      strategy: createStrategy.value,
      caseLimit: Number.isInteger(caseLimit) && caseLimit > 0 ? caseLimit : undefined,
      referenceVersionIds: [],
      referenceAssetRelationCodes: createReferenceAssetRelationCodes.value,
      caseCategory: createCaseCategory.value,
    })
    ElMessage.success('已提交，请点击启动进入队列')
    createVisible.value = false
    onSearch()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    creating.value = false
  }
}

async function onInterrupt(row: GenerationTask) {
  const ok = await ElMessageBox.confirm(`确认中断任务 ${row.taskNo} 吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.interruptGenerationTask(row.id, 'manual interrupt')
    ElMessage.success('已中断')
    loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '中断失败')
  }
}

/** 启动 / 重试 / 再次生成 合并为一个按钮；运行中仍展示为「生成中」且禁用 */
function primaryActionLabel(row: GenerationTask): string {
  if (row.status === 'RUNNING') return ''
  if (row.status === 'PENDING') return '启动'
  if (row.status === 'QUEUED') return '启动'
  if (row.status === 'COMPLETED') return '再次生成'
  if (row.status === 'FAILED' || row.status === 'CANCELLED') return '重试'
  return ''
}

function primaryActionDisabled(row: GenerationTask) {
  // 当系统正在自动推进队列时，排队中的任务仅展示按钮但置灰不可点
  return row.status === 'QUEUED' && row.queueAutoEnabled
}

function canEditTask(row: GenerationTask) {
  return row.status !== 'RUNNING' && row.status !== 'QUEUED'
}

async function onPrimaryAction(row: GenerationTask) {
  if (row.status === 'RUNNING') return
  if (row.status === 'PENDING') {
    await onStart(row)
    return
  }
  if (row.status === 'QUEUED') {
    if (row.queueAutoEnabled) return
    await onStart(row)
    return
  }
  if (['FAILED', 'COMPLETED', 'CANCELLED'].includes(row.status)) {
    await onRetry(row)
  }
}

async function onRetry(row: GenerationTask) {
  const title = row.status === 'COMPLETED' ? '再次生成' : '重试'
  const msg =
    row.status === 'COMPLETED'
      ? `确认对任务 ${row.taskNo} 再次生成吗？已加入队列，将自动在排到该任务时开始生成。`
      : `确认${title}任务 ${row.taskNo} 吗？已加入队列，将自动在排到该任务时开始生成。`
  const ok = await ElMessageBox.confirm(msg, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.retryGenerationTask(row.id)
    ElMessage.success('已重新排队，将自动在排到该任务时开始生成')
    loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '重试失败')
  }
}

async function onStart(row: GenerationTask) {
  const ok = await ElMessageBox.confirm(
    `确认启动任务 ${row.taskNo} 吗？系统将按队列推进，排到该任务时自动开始生成。`,
    '提示',
    { type: 'warning' },
  ).catch(() => false)
  if (!ok) return
  try {
    await api.startGenerationTask(row.id)
    ElMessage.success('已启动排队，将自动在排到该任务时开始生成')
    loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '启动失败')
  }
}

async function openEdit(row: GenerationTask) {
  if (row.status === 'RUNNING') {
    ElMessage.warning('运行中的任务不可编辑')
    return
  }
  if (row.status === 'QUEUED') {
    ElMessage.warning('排队中的任务不可编辑')
    return
  }
  editTaskId.value = row.id
  editTaskNo.value = row.taskNo
  editProjectId.value = String(row.projectId)
  editVersionId.value = String(row.versionId)
  editModelConfigId.value = String(row.modelConfigId ?? '')
  editPromptTemplateId.value = String(row.promptTemplateId ?? '')
  editStrategy.value = 'DEFAULT'
  editCaseLimit.value = '100'
  editCaseCategory.value = (row.caseCategory || 'FUNCTIONAL') === 'API' ? 'API' : 'FUNCTIONAL'
  editRequirementAssetsLoading.value = false
  editRequirementAssetOptions.value = []
  editReferenceAssetRelationCodes.value = []
  editVisible.value = true

  try {
    const [models, prompts, detail] = await Promise.all([
      api.getModelConfigs({ status: 'ENABLED' }),
      api.getPromptTemplates({ status: 'ENABLED', scopeType: 'GLOBAL' }),
      api.getGenerationTaskDetail(row.id),
    ])

    modelConfigs.value = models.map((m) => ({
      id: m.id,
      name: m.name,
      provider: m.provider,
      modelKey: m.modelKey,
      status: m.status,
    }))
    promptTemplates.value = prompts.map((p) => ({
      id: p.id,
      name: p.name,
      scopeType: p.scopeType,
      scopeId: p.scopeId,
      versionNo: p.versionNo,
      status: p.status,
    }))

    const t = detail.task
    editModelConfigId.value = String(t.modelConfigId ?? '')
    editPromptTemplateId.value = String(t.promptTemplateId ?? '')
    editCaseCategory.value = (t.caseCategory || 'FUNCTIONAL') === 'API' ? 'API' : 'FUNCTIONAL'

    let selectedRelationCodes: string[] = []
    if (detail.payloadJson) {
      try {
        const p = JSON.parse(detail.payloadJson) as { strategy?: string; caseLimit?: number; referenceAssetRelationCodes?: string[] }
        if (typeof p.strategy === 'string' && p.strategy) editStrategy.value = p.strategy
        if (p.caseLimit != null && Number.isFinite(Number(p.caseLimit))) editCaseLimit.value = String(p.caseLimit)
        if (Array.isArray(p.referenceAssetRelationCodes)) {
          selectedRelationCodes = p.referenceAssetRelationCodes.filter((x) => typeof x === 'string' && x.trim())
        }
      } catch {
        /* ignore */
      }
    }

    await loadRequirementAssetsForEditVersion(Number(row.projectId), Number(row.versionId), selectedRelationCodes)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务详情失败')
  }
}

async function submitEdit() {
  const modelId = Number(editModelConfigId.value)
  const promptId = Number(editPromptTemplateId.value)
  const caseLimit = Number(editCaseLimit.value)
  if (!Number.isInteger(modelId) || modelId <= 0 || !Number.isInteger(promptId) || promptId <= 0) {
    ElMessage.warning('请选择模型配置与 Prompt 模板')
    return
  }
  if (!editReferenceAssetRelationCodes.value.length) {
    ElMessage.warning('请选择需求资产（可多选）')
    return
  }
  editing.value = true
  try {
    await api.updateGenerationTask(editTaskId.value, {
      modelConfigId: modelId,
      promptTemplateId: promptId,
      strategy: editStrategy.value || undefined,
      caseLimit: Number.isInteger(caseLimit) && caseLimit > 0 ? caseLimit : undefined,
      caseCategory: editCaseCategory.value,
      referenceAssetRelationCodes: editReferenceAssetRelationCodes.value,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    editing.value = false
  }
}

function goFunctionalCases(row: GenerationTask) {
  router.push({
    path: '/test-cases',
    query: { projectId: String(row.projectId), versionId: String(row.versionId) },
  })
}

function goApiCases(row: GenerationTask) {
  router.push({
    path: '/api-test-cases',
    query: { projectId: String(row.projectId), versionId: String(row.versionId) },
  })
}

/** 功能测试任务只显示「功能用例」；接口测试任务只显示「接口用例」 */
function showFunctionalCasesButton(row: GenerationTask) {
  return (row.caseCategory || 'FUNCTIONAL') !== 'API'
}

function showApiCasesButton(row: GenerationTask) {
  return (row.caseCategory || 'FUNCTIONAL') === 'API'
}

function onSelectionChange(rows: GenerationTask[]) {
  selectedTaskIds.value = rows.map((r) => r.id)
}

async function onDeleteOne(row: GenerationTask) {
  const ok = await ElMessageBox.confirm(`确认删除任务「${row.taskNo}」吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.batchDeleteGenerationTasks([row.id])
    ElMessage.success('已删除')
    selectedTaskIds.value = selectedTaskIds.value.filter((id) => id !== row.id)
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

async function onBatchDelete() {
  if (selectedTaskIds.value.length === 0) {
    ElMessage.warning('请先勾选要删除的任务')
    return
  }
  const ok = await ElMessageBox.confirm(`确认批量删除 ${selectedTaskIds.value.length} 个任务吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.batchDeleteGenerationTasks(selectedTaskIds.value)
    ElMessage.success('批量删除成功')
    selectedTaskIds.value = []
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '批量删除失败')
  }
}

function requirementAssetLines(row: GenerationTask): string[] {
  const list = row.requirementAssets || []
  return list.map((a) => {
    const t = (a.title && String(a.title).trim()) || '—'
    return `${a.assetCode} · ${t}`
  })
}

function requirementAssetsTooltip(row: GenerationTask): string {
  return requirementAssetLines(row).join('\n')
}

/** 与新建「用例类型」一致：列表展示中文 */
function caseCategoryLabel(caseCategory: string | undefined) {
  const c = (caseCategory || 'FUNCTIONAL').trim().toUpperCase()
  if (c === 'API') return '接口测试'
  return '功能测试'
}

/** 用例类型列标签色（浅色描边，与「状态」列深色实心区分） */
function caseCategoryTagType(caseCategory: string | undefined) {
  const c = (caseCategory || 'FUNCTIONAL').trim().toUpperCase()
  return c === 'API' ? 'warning' : 'primary'
}

/** 任务状态中文展示：统一走状态词典，存储值仍为英文枚举。 */
function taskStatusLabel(s: GenerationTask['status'] | string | undefined) {
  return dictStatusLabel(GENERATION_TASK_STATUS, s, '-')
}

/** 失败态补充说明（悬停查看：含用户中断原因） */
function taskStatusTooltip(row: GenerationTask) {
  if (row.status === 'FAILED' && row.interruptReason) {
    return `已中断：${row.interruptReason}`
  }
  if (row.status === 'FAILED' && row.errorMessage) {
    return row.errorMessage
  }
  return ''
}

/** 任务状态列标签色（深色实心，与「用例类型」浅色描边区分） */
function statusTagType(s: GenerationTask['status']) {
  return dictStatusTagType(GENERATION_TASK_STATUS, s, 'info')
}

onMounted(async () => {
  await loadProjects()
  await loadAllVersions()
  if (safeProjectId.value) {
    await loadVersionsForProject(safeProjectId.value)
  }
  await loadTasks()
  pollingTimer.value = window.setInterval(() => {
    loadTasks()
  }, 5000)
})

onUnmounted(() => {
  if (pollingTimer.value !== null) {
    window.clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
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
            placeholder="项目"
            style="width: 240px"
            @change="(v: string | number | null) => { versionId = ''; loadVersionsForProject(Number(v || 0)) }"
          >
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
          </el-select>
          <el-select v-model="versionId" filterable clearable :loading="versionsLoading" placeholder="版本" style="width: 240px">
            <el-option v-for="v in versions" :key="v.id" :label="`${v.versionNo}${v.name ? ' - ' + v.name : ''}`" :value="String(v.id)" />
          </el-select>
          <el-select v-model="status" clearable placeholder="状态" style="width: 160px">
            <el-option label="待启动" value="PENDING" />
            <el-option label="排队中" value="QUEUED" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onResetFilters">重置</el-button>
          <el-button type="success" @click="openCreate">新建任务</el-button>
          <el-button type="danger" plain :disabled="selectedTaskIds.length === 0" @click="onBatchDelete">批量删除</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-body">
        <el-table :data="records" :size="tableDensity" v-loading="loading" border stripe height="100%" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column label="用例类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="caseCategoryTagType(row.caseCategory)">
                {{ caseCategoryLabel(row.caseCategory) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="taskNo" label="任务号" min-width="180" />
          <el-table-column label="项目名称" min-width="180">
            <template #default="{ row }">
              {{ projectNameMap.get(row.projectId) || `项目#${row.projectId}` }}
            </template>
          </el-table-column>
          <el-table-column label="版本名称" min-width="200">
            <template #default="{ row }">
              {{ versionNameMap.get(row.versionId) || `版本#${row.versionId}` }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tooltip v-if="taskStatusTooltip(row)" :content="taskStatusTooltip(row)" placement="top">
                <el-tag size="small" effect="dark" :type="statusTagType(row.status)">
                  {{ taskStatusLabel(row.status) }}
                </el-tag>
              </el-tooltip>
              <el-tag v-else size="small" effect="dark" :type="statusTagType(row.status)">
                {{ taskStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="需求资产（编码 · 标题）" min-width="300">
            <template #default="{ row }">
              <template v-if="row.requirementAssets?.length">
                <el-tooltip v-if="row.requirementAssets.length > 2" placement="top">
                  <template #content>
                    <div class="task-asset-tooltip">{{ requirementAssetsTooltip(row) }}</div>
                  </template>
                  <div class="task-asset-cell">
                    <div v-for="(line, i) in requirementAssetLines(row).slice(0, 2)" :key="i" class="task-asset-line">
                      {{ line }}
                    </div>
                    <span class="task-asset-more">…共 {{ row.requirementAssets.length }} 条，悬停查看全部</span>
                  </div>
                </el-tooltip>
                <div v-else class="task-asset-cell">
                  <div v-for="(line, i) in requirementAssetLines(row)" :key="i" class="task-asset-line">{{ line }}</div>
                </div>
              </template>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误" min-width="220" />
          <el-table-column label="操作" width="340" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="danger"
                :disabled="row.status !== 'QUEUED'"
                @click="onInterrupt(row)"
              >
                中断
              </el-button>
              <template v-if="primaryActionLabel(row)">
                <el-divider direction="vertical" />
                <el-button
                  link
                  type="primary"
                  :disabled="primaryActionDisabled(row)"
                  @click="onPrimaryAction(row)"
                >
                  {{ primaryActionLabel(row) }}
                </el-button>
              </template>
              <el-divider direction="vertical" />
              <el-button link type="warning" :disabled="!canEditTask(row)" @click="openEdit(row)">编辑</el-button>
              <el-divider direction="vertical" />
              <el-button
                link
                type="danger"
                :disabled="row.status === 'QUEUED' || row.status === 'RUNNING'"
                @click="onDeleteOne(row)"
              >
                删除
              </el-button>
              <template v-if="showFunctionalCasesButton(row)">
                <el-divider direction="vertical" />
                <el-button link type="success" @click="goFunctionalCases(row)">功能用例</el-button>
              </template>
              <template v-if="showApiCasesButton(row)">
                <el-divider direction="vertical" />
                <el-button link type="success" @click="goApiCases(row)">接口用例</el-button>
              </template>
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
          @current-change="loadTasks"
          @size-change="onSearch"
        />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="新建任务" width="760px" :close-on-click-modal="false">
      <el-form label-position="top">
        <div class="create-grid">
          <el-form-item label="项目">
            <el-select
              v-model="createProjectId"
              filterable
              clearable
              :loading="projectsLoading"
              placeholder="选择项目"
              @change="(v: string | number | null) => loadVersionsForProject(Number(v || 0))"
            >
              <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="版本">
            <el-select
              v-model="createVersionId"
              filterable
              clearable
              :loading="versionsLoading"
              placeholder="选择版本"
              @change="(v: string | number | null) => loadRequirementAssetsForCreateVersion(safeCreateProjectId, Number(v || 0))"
            >
              <el-option
                v-for="v in versions"
                :key="v.id"
                :label="`${v.name || '未命名版本'}（${v.versionNo}）`"
                :value="String(v.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="需求资产（可多选）">
            <el-select
              v-model="createReferenceAssetRelationCodes"
              multiple
              collapse-tags
              filterable
              :loading="createRequirementAssetsLoading"
              placeholder="请选择要生成的需求资产批次"
              style="width: 100%"
            >
              <el-option
                v-for="o in createRequirementAssetOptions"
                :key="o.relationCode"
                :label="o.label"
                :value="o.relationCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模型配置">
            <el-select v-if="modelConfigs.length > 0" v-model="createModelConfigId" filterable clearable placeholder="选择模型配置">
              <el-option
                v-for="m in modelConfigs"
                :key="m.id"
                :label="`${m.name}（${m.provider}/${m.modelKey}）`"
                :value="String(m.id)"
              />
            </el-select>
            <el-input v-else v-model="createModelConfigId" placeholder="模型配置ID（数字，例如 1）" />
          </el-form-item>
          <el-form-item label="用例类型">
            <el-select v-model="createCaseCategory" style="width: 100%" placeholder="请选择用例类型" clearable>
              <el-option label="请选择用例类型" value="" disabled />
              <el-option label="功能测试（写入功能用例表）" value="FUNCTIONAL" />
              <el-option label="接口测试（写入接口用例表）" value="API" />
            </el-select>
          </el-form-item>
          <el-form-item label="Prompt模板">
            <el-select v-if="promptTemplates.length > 0" v-model="createPromptTemplateId" filterable clearable placeholder="选择Prompt模板">
              <el-option
                v-for="p in promptTemplates"
                :key="p.id"
                :label="`${p.name}（${p.scopeType} v${p.versionNo}）`"
                :value="String(p.id)"
              />
            </el-select>
            <el-input v-else v-model="createPromptTemplateId" placeholder="Prompt模板ID（数字，例如 1）" />
          </el-form-item>
          <el-form-item label="策略">
            <el-input v-model="createStrategy" placeholder="DEFAULT" />
          </el-form-item>
          <el-form-item label="用例数量上限">
            <el-input v-model="createCaseLimit" placeholder="100" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑任务" width="760px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
          任务号 {{ editTaskNo }}（仅可编辑模型、Prompt、策略与用例类型；运行中不可编辑）
        </el-alert>
        <div class="create-grid">
          <el-form-item label="项目">
            <el-input :model-value="projectNameMap.get(Number(editProjectId)) || `项目#${editProjectId}`" disabled />
          </el-form-item>
          <el-form-item label="版本">
            <el-input :model-value="versionNameMap.get(Number(editVersionId)) || `版本#${editVersionId}`" disabled />
          </el-form-item>
          <el-form-item label="需求资产（可多选）">
            <el-select
              v-model="editReferenceAssetRelationCodes"
              multiple
              collapse-tags
              filterable
              :loading="editRequirementAssetsLoading"
              placeholder="请选择要生成的需求资产批次"
              style="width: 100%"
            >
              <el-option
                v-for="o in editRequirementAssetOptions"
                :key="o.relationCode"
                :label="o.label"
                :value="o.relationCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模型配置">
            <el-select v-if="modelConfigs.length > 0" v-model="editModelConfigId" filterable clearable placeholder="选择模型配置">
              <el-option
                v-for="m in modelConfigs"
                :key="m.id"
                :label="`${m.name}（${m.provider}/${m.modelKey}）`"
                :value="String(m.id)"
              />
            </el-select>
            <el-input v-else v-model="editModelConfigId" placeholder="模型配置ID" />
          </el-form-item>
          <el-form-item label="用例类型">
            <el-select v-model="editCaseCategory" style="width: 100%">
              <el-option label="功能测试（写入功能用例表）" value="FUNCTIONAL" />
              <el-option label="接口测试（写入接口用例表）" value="API" />
            </el-select>
          </el-form-item>
          <el-form-item label="Prompt模板">
            <el-select v-if="promptTemplates.length > 0" v-model="editPromptTemplateId" filterable clearable placeholder="选择Prompt模板">
              <el-option
                v-for="p in promptTemplates"
                :key="p.id"
                :label="`${p.name}（${p.scopeType} v${p.versionNo}）`"
                :value="String(p.id)"
              />
            </el-select>
            <el-input v-else v-model="editPromptTemplateId" placeholder="Prompt模板ID" />
          </el-form-item>
          <el-form-item label="策略">
            <el-input v-model="editStrategy" placeholder="DEFAULT" />
          </el-form-item>
          <el-form-item label="用例数量上限">
            <el-input v-model="editCaseLimit" placeholder="100" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
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

.create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}

.task-asset-cell {
  font-size: 12px;
  line-height: 1.45;
}

.task-asset-line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}

.task-asset-more {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.task-asset-tooltip {
  white-space: pre-line;
  max-width: 400px;
}

.text-muted {
  color: var(--el-text-color-secondary);
}
</style>

