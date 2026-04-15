<script setup lang="ts">
import { computed, inject, onMounted, ref, watch, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { api, type Project, type TestCase, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'
import { priorityTagType } from '../utils/priorityTag'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const router = useRouter()
const route = useRoute()

function parseQueryId(q: unknown): number {
  const s = Array.isArray(q) ? q[0] : q
  if (typeof s !== 'string') return NaN
  const n = Number(s)
  return Number.isInteger(n) && n > 0 ? n : NaN
}

async function applyQueryFilters() {
  const p = parseQueryId(route.query.projectId)
  const v = parseQueryId(route.query.versionId)
  if (Number.isNaN(p)) return
  projectId.value = String(p)
  await loadVersionsForProject(p)
  if (!Number.isNaN(v)) {
    versionId.value = String(v)
  } else {
    versionId.value = ''
  }
  pageNo.value = 1
  await loadCases()
}

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const projectsLoading = ref(false)
const versionsLoading = ref(false)

const projectId = ref('')
const versionId = ref('')
const titleKeyword = ref('')
const executionStatus = ref<string | undefined>(undefined)
const reviewStatus = ref<string | undefined>(undefined)
const priority = ref<string | undefined>(undefined)

const loading = ref(false)
const records = ref<TestCase[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const selectedIds = ref<number[]>([])

// -------- 执行/评审弹窗（单个 + 批量复用）--------
const executeDialogVisible = ref(false)
const executeDialogMode = ref<'single' | 'batch'>('single')
const executeTargetIds = ref<number[]>([])
const executeResult = ref<'SUCCESS' | 'FAILURE'>('SUCCESS')
const executeReason = ref('')

const reviewDialogVisible = ref(false)
const reviewDialogMode = ref<'single' | 'batch'>('single')
const reviewTargetIds = ref<number[]>([])
const reviewResult = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reviewCommentInput = ref('')

const createVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  moduleName: '',
  featureName: '',
  title: '',
  precondition: '',
  steps: '',
  testData: '',
  expectedResult: '',
  priority: 'P2',
  remark: '',
})
const formProjectId = ref('')
const formVersionId = ref('')

const safeProjectId = computed(() => {
  const v = Number(projectId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
})
const safeVersionId = computed(() => {
  const v = Number(versionId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
})

const safeFormProjectId = computed(() => {
  const v = Number(formProjectId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
})
const safeFormVersionId = computed(() => {
  const v = Number(formVersionId.value)
  return Number.isInteger(v) && v > 0 ? v : 0
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

async function loadCases() {
  loading.value = true
  try {
    const data = await api.getTestCases({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: safeProjectId.value || undefined,
      versionId: safeVersionId.value || undefined,
      keyword: titleKeyword.value,
      executionStatus: executionStatus.value ?? '',
      reviewStatus: reviewStatus.value ?? '',
      priority: priority.value ?? '',
    })
    records.value = data.records
    total.value = data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载用例失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  loadCases()
}

async function onReset() {
  projectId.value = ''
  versionId.value = ''
  titleKeyword.value = ''
  executionStatus.value = undefined
  reviewStatus.value = undefined
  priority.value = undefined
  versions.value = []
  pageNo.value = 1
  await loadCases()
}

function onSelectionChange(rows: TestCase[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function openCreate() {
  isEditing.value = false
  editingId.value = null
  form.value = {
    moduleName: '',
    featureName: '',
    title: '',
    precondition: '',
    steps: '',
    testData: '',
    expectedResult: '',
    priority: 'P2',
    remark: '',
  }
  formProjectId.value = projectId.value
  formVersionId.value = versionId.value
  createVisible.value = true
}

async function openEdit(row: TestCase) {
  isEditing.value = true
  editingId.value = row.id
  form.value = {
    moduleName: row.moduleName,
    featureName: row.featureName,
    title: row.title,
    precondition: row.precondition || '',
    steps: row.steps,
    testData: row.testData || '',
    expectedResult: row.expectedResult,
    priority: row.priority || 'P2',
    remark: row.remark || '',
  }
  formProjectId.value = String(row.projectId)
  // 先拉取该项目版本列表，否则 el-select 无匹配 option 会只显示 versionId 数字
  await loadVersionsForProject(row.projectId)
  formVersionId.value = String(row.versionId)
  createVisible.value = true
}

async function submitForm() {
  if (!safeFormProjectId.value || !safeFormVersionId.value) {
    ElMessage.warning('请选择项目和版本')
    return
  }
  if (!form.value.moduleName.trim() || !form.value.featureName.trim() || !form.value.title.trim() || !form.value.steps.trim() || !form.value.expectedResult.trim()) {
    ElMessage.warning('请补全必填字段')
    return
  }
  try {
    if (isEditing.value && editingId.value) {
      await api.updateTestCase(editingId.value, {
        moduleName: form.value.moduleName.trim(),
        featureName: form.value.featureName.trim(),
        title: form.value.title.trim(),
        precondition: form.value.precondition || undefined,
        steps: form.value.steps,
        testData: form.value.testData || undefined,
        expectedResult: form.value.expectedResult,
        priority: form.value.priority,
        remark: form.value.remark || undefined,
      })
      ElMessage.success('已更新')
    } else {
      await api.createTestCase({
        projectId: safeFormProjectId.value,
        versionId: safeFormVersionId.value,
        moduleName: form.value.moduleName.trim(),
        featureName: form.value.featureName.trim(),
        title: form.value.title.trim(),
        precondition: form.value.precondition || undefined,
        steps: form.value.steps,
        testData: form.value.testData || undefined,
        expectedResult: form.value.expectedResult,
        priority: form.value.priority,
        remark: form.value.remark || undefined,
      })
      ElMessage.success('已创建')
    }
    createVisible.value = false
    loadCases()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  }
}

async function removeOne(row: TestCase) {
  const ok = await ElMessageBox.confirm(`确认删除用例「${row.caseNo}」吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.deleteTestCase(row.id)
    ElMessage.success('已删除')
    loadCases()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

function openExecuteDialog(ids: number[], mode: 'single' | 'batch') {
  if (ids.length === 0) return
  executeTargetIds.value = ids
  executeDialogMode.value = mode
  executeDialogVisible.value = true
  executeResult.value = 'SUCCESS'
  executeReason.value = ''
}

function openReviewDialog(ids: number[], mode: 'single' | 'batch', defaultResult: 'APPROVED' | 'REJECTED' = 'APPROVED') {
  if (ids.length === 0) return
  reviewTargetIds.value = ids
  reviewDialogMode.value = mode
  reviewDialogVisible.value = true
  reviewResult.value = defaultResult
  reviewCommentInput.value = ''
}

async function confirmExecuteDialog() {
  const ids = executeTargetIds.value
  if (ids.length === 0) return

  const executionStatus = executeResult.value === 'SUCCESS' ? 'EXECUTED' : 'FAILED'
  const failureReason = executeReason.value.trim()
  // 成功/通过时原因非必填：不填写就不要写入后端（避免默认文案）
  const reason = executeResult.value === 'FAILURE' ? failureReason : failureReason || undefined

  if (executeResult.value === 'FAILURE' && !reason) {
    ElMessage.warning('失败原因不能为空')
    return
  }

  try {
    if (executeDialogMode.value === 'single') {
      await api.updateTestCaseStatus(ids[0], { executionStatus, reason })
      ElMessage.success('执行完成')
    } else {
      await api.batchUpdateTestCases({ ids, fields: { executionStatus }, reason })
      ElMessage.success('批量执行完成')
      selectedIds.value = []
    }
    executeDialogVisible.value = false
    await loadCases()
  } catch (e: any) {
    ElMessage.error(e.message || '执行失败')
  }
}

async function confirmReviewDialog() {
  const ids = reviewTargetIds.value
  if (ids.length === 0) return

  const reviewStatus = reviewResult.value
  const comment = reviewCommentInput.value.trim()
  // 评审通过时原因非必填：不填写就不要写入后端（避免默认文案）
  const reason = comment || undefined

  if (reviewStatus === 'REJECTED' && !comment) {
    ElMessage.warning('驳回意见不能为空')
    return
  }

  try {
    const payload = {
      reviewStatus,
      reviewComment: comment || undefined,
      reason,
    }
    if (reviewDialogMode.value === 'single') {
      await api.updateTestCaseStatus(ids[0], payload)
      ElMessage.success('评审完成')
    } else {
      await api.batchUpdateTestCases({
        ids,
        fields: { reviewStatus },
        reviewComment: comment || undefined,
        reason,
      })
      ElMessage.success('批量评审完成')
      selectedIds.value = []
    }
    reviewDialogVisible.value = false
    await loadCases()
  } catch (e: any) {
    ElMessage.error(e.message || '评审失败')
  }
}

function batchUpdateReview(status: string) {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选用例')
    return
  }
  openReviewDialog(selectedIds.value, 'batch', status.toUpperCase() === 'REJECTED' ? 'REJECTED' : 'APPROVED')
}

function executionStatusLabel(s: string | undefined) {
  const v = (s || '').trim().toUpperCase()
  switch (v) {
    case 'NOT_EXECUTED':
      return '未执行'
    case 'EXECUTED':
      return '已执行'
    case 'FAILED':
      return '执行失败'
    default:
      return s ? String(s) : '-'
  }
}

function executionStatusTagType(s: string | undefined) {
  const v = (s || '').trim().toUpperCase()
  switch (v) {
    case 'NOT_EXECUTED':
      return 'info'
    case 'EXECUTED':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
}

function reviewStatusLabel(s: string | undefined) {
  const v = (s || '').trim().toUpperCase()
  switch (v) {
    case 'PENDING':
      return '待评审'
    case 'APPROVED':
      return '已通过'
    case 'REJECTED':
      return '已驳回'
    default:
      return s ? String(s) : '-'
  }
}

function reviewStatusTagType(s: string | undefined) {
  const v = (s || '').trim().toUpperCase()
  switch (v) {
    case 'PENDING':
      return 'info'
    case 'APPROVED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return 'info'
  }
}

function updateExecutionStatus(row: TestCase) {
  openExecuteDialog([row.id], 'single')
}

function updateReviewStatus(row: TestCase) {
  const st = (row.reviewStatus || '').trim().toUpperCase()
  openReviewDialog([row.id], 'single', st === 'REJECTED' ? 'REJECTED' : 'APPROVED')
}

function batchUpdateExecution() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选用例')
    return
  }
  openExecuteDialog(selectedIds.value, 'batch')
}

async function onBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选用例')
    return
  }
  const ok = await ElMessageBox.confirm(`确认批量删除 ${selectedIds.value.length} 条功能用例吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  try {
    await api.batchDeleteTestCases(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    await loadCases()
  } catch (e: any) {
    ElMessage.error(e.message || '批量删除失败')
  }
}

function projectDisplay(row: TestCase): string {
  const name = row.projectName?.trim()
  const code = row.projectCode?.trim()
  if (name || code) {
    return `${name || '—'}（${code || '—'}）`
  }
  return `项目#${row.projectId}`
}

function versionDisplay(row: TestCase): string {
  const no = row.versionNo?.trim()
  const name = row.versionName?.trim()
  if (no || name) {
    return `${no || '—'} · ${name || '未命名'}`
  }
  return `版本#${row.versionId}`
}

function requirementAssetLines(row: TestCase): string[] {
  const list = row.requirementAssets || []
  return list.map((a) => {
    const t = (a.title && String(a.title).trim()) || '—'
    return `${a.assetCode || '—'} · ${t}`
  })
}

function requirementAssetsTooltip(row: TestCase): string {
  return requirementAssetLines(row).join('\n')
}

function requirementAssetsSummary(row: TestCase): string {
  const list = row.requirementAssets || []
  if (!list.length) return '—'
  const lines = requirementAssetLines(row)
  if (lines.length === 1) return lines[0]
  if (lines.length === 2) return `${lines[0]}；${lines[1]}`
  return `${lines[0]} 等 ${list.length} 条`
}

function goDetail(row: TestCase) {
  router.push({ path: '/test-cases/detail', query: { caseId: String(row.id) } })
}

onMounted(async () => {
  await loadProjects()
  if (!Number.isNaN(parseQueryId(route.query.projectId))) {
    await applyQueryFilters()
  } else {
    await loadCases()
  }
})

watch(
  () => route.query,
  async () => {
    if (!Number.isNaN(parseQueryId(route.query.projectId))) {
      await applyQueryFilters()
    }
  },
  { deep: true },
)
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-select v-model="projectId" filterable clearable :loading="projectsLoading" placeholder="项目" style="width: 220px" @change="(v:any)=>{ versionId=''; loadVersionsForProject(Number(v||0)) }">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
          </el-select>
          <el-select v-model="versionId" filterable clearable :loading="versionsLoading" placeholder="版本" style="width: 200px">
            <el-option v-for="v in versions" :key="v.id" :label="`${v.name || '未命名版本'}（${v.versionNo}）`" :value="String(v.id)" />
          </el-select>
          <el-input v-model="titleKeyword" placeholder="标题" clearable style="width: 200px" />
          <el-select v-model="executionStatus" clearable placeholder="执行状态" style="width: 150px">
            <el-option label="未执行" value="NOT_EXECUTED" />
            <el-option label="已执行" value="EXECUTED" />
            <el-option label="执行失败" value="FAILED" />
          </el-select>
          <el-select v-model="reviewStatus" clearable placeholder="评审状态" style="width: 150px">
            <el-option label="待评审" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
          <el-select v-model="priority" clearable placeholder="优先级" style="width: 120px">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button type="success" @click="openCreate">新增用例</el-button>
          <el-button type="primary" plain :disabled="selectedIds.length===0" @click="batchUpdateReview('APPROVED')">批量评审</el-button>
          <el-button type="warning" plain :disabled="selectedIds.length===0" @click="batchUpdateExecution">批量执行</el-button>
          <el-button type="danger" plain :disabled="selectedIds.length===0" @click="onBatchDelete">批量删除</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-body">
        <el-table :data="records" :size="tableDensity" v-loading="loading" border stripe height="100%" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="caseNo" label="用例号" min-width="160" />
          <el-table-column prop="moduleName" label="模块" min-width="120" />
          <el-table-column prop="featureName" label="功能" min-width="140" />
          <el-table-column prop="title" label="标题" min-width="240" />
          <el-table-column label="项目 / 版本" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              {{ projectDisplay(row) }} · {{ versionDisplay(row) }}
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="dark" :type="priorityTagType(row.priority)">
                {{ row.priority || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="评审状态" min-width="130">
            <template #default="{ row }">
              <el-tag size="small" effect="dark" :type="reviewStatusTagType(row.reviewStatus)">
                {{ reviewStatusLabel(row.reviewStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="执行状态" min-width="130">
            <template #default="{ row }">
              <el-tag size="small" effect="dark" :type="executionStatusTagType(row.executionStatus)">
                {{ executionStatusLabel(row.executionStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="需求资产" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tooltip v-if="(row.requirementAssets || []).length > 2" placement="top">
                <template #content>
                  <div class="tc-asset-tooltip" style="white-space: pre-line">{{ requirementAssetsTooltip(row) }}</div>
                </template>
                <span>{{ requirementAssetsSummary(row) }}</span>
              </el-tooltip>
              <span v-else>{{ requirementAssetsSummary(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="goDetail(row)">详情</el-button>
              <el-divider direction="vertical" />
              <el-button link type="success" @click="updateReviewStatus(row)">
                评审
              </el-button>
              <el-divider direction="vertical" />
              <el-button link type="warning" @click="updateExecutionStatus(row)">
                执行
              </el-button>
              <el-divider direction="vertical" />
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-divider direction="vertical" />
              <el-button link type="danger" @click="removeOne(row)">删除</el-button>
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
          @current-change="loadCases"
          @size-change="onSearch"
        />
      </div>
    </el-card>
  </div>

  <el-dialog
    v-model="executeDialogVisible"
    title="执行用例"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="dialog-section">
      <div class="dialog-label">执行结果</div>
      <el-radio-group v-model="executeResult" class="dialog-radios">
        <el-radio-button label="SUCCESS">执行成功</el-radio-button>
        <el-radio-button label="FAILURE">执行失败</el-radio-button>
      </el-radio-group>
    </div>

    <div class="dialog-section">
      <div class="dialog-label">{{ executeResult === 'FAILURE' ? '失败原因' : '执行原因（可选）' }}</div>
      <el-input
        v-model="executeReason"
        type="textarea"
        :rows="4"
        :placeholder="executeResult === 'FAILURE' ? '请输入失败原因' : '执行原因（可选）'"
      />
    </div>

    <template #footer>
      <el-button @click="executeDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmExecuteDialog">确认</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="reviewDialogVisible"
    title="评审用例"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="dialog-section">
      <div class="dialog-label">评审结果</div>
      <el-radio-group v-model="reviewResult" class="dialog-radios">
        <el-radio-button label="APPROVED">评审通过</el-radio-button>
        <el-radio-button label="REJECTED">评审驳回</el-radio-button>
      </el-radio-group>
    </div>

    <div class="dialog-section">
      <div class="dialog-label">{{ reviewResult === 'REJECTED' ? '驳回意见' : '评审意见（可选）' }}</div>
      <el-input
        v-model="reviewCommentInput"
        type="textarea"
        :rows="4"
        :placeholder="reviewResult === 'REJECTED' ? '请输入驳回意见' : '评审意见（可选）'"
      />
    </div>

    <template #footer>
      <el-button @click="reviewDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmReviewDialog">确认</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="createVisible" :title="isEditing ? '编辑用例' : '新增用例'" width="860px" :close-on-click-modal="false">
    <el-form label-position="top">
      <div class="create-grid">
        <el-form-item label="项目（必选）">
          <el-select
            v-model="formProjectId"
            class="form-control-block"
            filterable
            clearable
            :loading="projectsLoading"
            placeholder="选择项目"
            @change="(v:any)=>{ formVersionId=''; loadVersionsForProject(Number(v||0)) }"
          >
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本（必选）">
          <el-select
            v-model="formVersionId"
            class="form-control-block"
            filterable
            clearable
            :loading="versionsLoading"
            placeholder="选择版本"
          >
            <el-option v-for="v in versions" :key="v.id" :label="`${v.name || '未命名版本'}（${v.versionNo}）`" :value="String(v.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块（必填）">
          <el-input v-model="form.moduleName" />
        </el-form-item>
        <el-form-item label="功能（必填）">
          <el-input v-model="form.featureName" />
        </el-form-item>
        <el-form-item label="标题（必填）" class="full-span">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="前置条件" class="full-span">
          <el-input v-model="form.precondition" />
        </el-form-item>
        <el-form-item label="步骤（必填，建议1/2/3编号）" class="full-span">
          <el-input v-model="form.steps" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="测试数据" class="full-span">
          <el-input v-model="form.testData" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="预期结果（必填）" class="full-span">
          <el-input v-model="form.expectedResult" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="createVisible=false">取消</el-button>
      <el-button type="primary" @click="submitForm">{{ isEditing ? '确认修改' : '确认新增' }}</el-button>
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

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-header span {
  color: #909399;
  font-size: 13px;
}

.create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.create-grid > * {
  min-width: 0;
}

.full-span {
  grid-column: 1 / -1;
}

.create-grid :deep(.form-control-block) {
  width: 100%;
}

.create-grid :deep(.el-form-item__content .el-input) {
  width: 100%;
}

/* 执行/评审弹窗的可读性样式（确保在 Teleport 的 dialog 中也生效） */
:deep(.el-dialog__body) {
  padding-top: 18px;
}

.dialog-section {
  margin-bottom: 16px;
}

.dialog-label {
  font-weight: 700;
  color: #303133;
  margin-bottom: 10px;
}

.dialog-radios {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

:deep(.el-radio-button__inner) {
  padding: 10px 18px;
  font-weight: 650;
}

:deep(.el-dialog__footer) {
  padding-top: 12px;
}

.tc-asset-tooltip {
  white-space: pre-line;
  max-width: 420px;
}
</style>

