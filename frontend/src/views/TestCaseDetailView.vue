<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, type TestCaseDetail } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'
import { priorityTagType } from '../utils/priorityTag'
import {
  executionStatusLabel,
  executionStatusTagType,
  reviewStatusLabel,
  reviewStatusTagType,
} from '../utils/caseStatusDisplay'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<TestCaseDetail | null>(null)

/** 状态日志按 id 倒序，首条为最新 */
const executionRemarkDisplay = computed(() => {
  const logs = detail.value?.statusLogs || []
  const st = (detail.value?.testCase.executionStatus || '').trim().toUpperCase()
  if (st === 'FAILED') {
    const item = logs.find((l) => l.fieldName === 'execution_status' && (l.newValue || '').trim().toUpperCase() === 'FAILED')
    const r = item?.reason?.trim()
    return r || '-'
  }
  if (st === 'EXECUTED') {
    const item = logs.find(
      (l) => l.fieldName === 'execution_status' && (l.newValue || '').trim().toUpperCase() === 'EXECUTED' && (l.reason || '').trim(),
    )
    return item?.reason?.trim() || '-'
  }
  return '-'
})

const reviewRemarkDisplay = computed(() => {
  const tc = detail.value?.testCase
  if (!tc) return '-'
  const st = (tc.reviewStatus || '').trim().toUpperCase()
  const fromEntity = (tc.reviewComment || '').trim()
  if (fromEntity) return fromEntity
  const logs = detail.value?.statusLogs || []
  const item = logs.find((l) => l.fieldName === 'review_status' && (l.newValue || '').trim().toUpperCase() === st && (l.reason || '').trim())
  return item?.reason?.trim() || '-'
})

const executionTimeDisplay = computed(() => {
  if (detail.value?.testCase.lastExecutedAt) return detail.value.testCase.lastExecutedAt
  const logs = detail.value?.statusLogs || []
  const item = logs.find((l) => l.fieldName === 'execution_status')
  return item?.changedAt
})

const reviewTimeDisplay = computed(() => {
  if (detail.value?.testCase.reviewedAt) return detail.value.testCase.reviewedAt
  const logs = detail.value?.statusLogs || []
  const item = logs.find((l) => l.fieldName === 'review_status')
  return item?.changedAt
})

const requirementAssetsLines = computed(() => {
  const list = detail.value?.testCase.requirementAssets || []
  return list.map((a) => {
    const t = (a.title && String(a.title).trim()) || '—'
    return `${a.assetCode || '—'} · ${t}`
  })
})

const requirementAssetsTooltipBody = computed(() => requirementAssetsLines.value.join('\n'))

const requirementAssetsSummary = computed(() => {
  const list = detail.value?.testCase.requirementAssets || []
  if (!list.length) return '—'
  const lines = requirementAssetsLines.value
  if (lines.length === 1) return lines[0]
  if (lines.length === 2) return `${lines[0]}；${lines[1]}`
  return `${lines[0]} 等 ${list.length} 条`
})

const executeDialogVisible = ref(false)
const executeResult = ref<'SUCCESS' | 'FAILURE'>('SUCCESS')
const executeReason = ref('')

const reviewDialogVisible = ref(false)
const reviewResult = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reviewCommentInput = ref('')

const editDialogVisible = ref(false)
const editForm = ref({
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

const caseId = computed(() => {
  const val = route.query.caseId
  const id = typeof val === 'string' ? Number(val) : 0
  return Number.isInteger(id) && id > 0 ? id : 0
})

async function loadDetail() {
  if (!caseId.value) {
    ElMessage.warning('缺少 caseId')
    return
  }
  loading.value = true
  try {
    detail.value = await api.getTestCaseDetail(caseId.value)
  } catch (e: any) {
    ElMessage.error(e.message || '加载详情失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/test-cases')
}

function openEditDialog() {
  const tc = detail.value?.testCase
  if (!tc) return
  editForm.value = {
    moduleName: tc.moduleName,
    featureName: tc.featureName,
    title: tc.title,
    precondition: tc.precondition || '',
    steps: tc.steps,
    testData: tc.testData || '',
    expectedResult: tc.expectedResult,
    priority: tc.priority || 'P2',
    remark: tc.remark || '',
  }
  editDialogVisible.value = true
}

async function submitEditDialog() {
  const tc = detail.value?.testCase
  if (!tc?.id) return
  const f = editForm.value
  if (!f.moduleName.trim() || !f.featureName.trim() || !f.title.trim() || !f.steps.trim() || !f.expectedResult.trim()) {
    ElMessage.warning('请补全必填字段')
    return
  }
  try {
    await api.updateTestCase(tc.id, {
      moduleName: f.moduleName.trim(),
      featureName: f.featureName.trim(),
      title: f.title.trim(),
      precondition: f.precondition || undefined,
      steps: f.steps,
      testData: f.testData || undefined,
      expectedResult: f.expectedResult,
      priority: f.priority,
      remark: f.remark || undefined,
    })
    editDialogVisible.value = false
    await loadDetail()
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

function openExecuteDialog() {
  executeDialogVisible.value = true
  executeResult.value = 'SUCCESS'
  executeReason.value = ''
}

function openReviewDialog() {
  reviewDialogVisible.value = true
  reviewResult.value = 'APPROVED'
  reviewCommentInput.value = ''
}

async function confirmExecuteDialog() {
  if (!detail.value) return
  const id = detail.value.testCase.id
  const executionStatus = executeResult.value === 'SUCCESS' ? 'EXECUTED' : 'FAILED'
  const reason = executeReason.value.trim()
  if (executeResult.value === 'FAILURE' && !reason) {
    ElMessage.warning('失败原因不能为空')
    return
  }
  try {
    // 成功时原因非必填：不填写就不要写入后端
    await api.updateTestCaseStatus(id, { executionStatus, reason: reason || undefined })
    executeDialogVisible.value = false
    await loadDetail()
    ElMessage.success('执行完成')
  } catch (e: any) {
    ElMessage.error(e.message || '执行失败')
  }
}

async function confirmReviewDialog() {
  if (!detail.value) return
  const id = detail.value.testCase.id
  const reviewStatus = reviewResult.value
  const comment = reviewCommentInput.value.trim()
  if (reviewStatus === 'REJECTED' && !comment) {
    ElMessage.warning('驳回意见不能为空')
    return
  }
  try {
    await api.updateTestCaseStatus(id, {
      reviewStatus,
      reviewComment: comment || undefined,
      // 通过时原因非必填：不填写就不要写入后端
      reason: comment || undefined,
    })
    reviewDialogVisible.value = false
    await loadDetail()
    ElMessage.success('评审完成')
  } catch (e: any) {
    ElMessage.error(e.message || '评审失败')
  }
}

onMounted(loadDetail)
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-card shadow="never" class="hero-card">
      <div class="hero-layout">
        <div class="hero-main">
          <h1 class="hero-title">{{ detail?.testCase.title || '—' }}</h1>
          <div class="hero-meta">
            <span class="hero-meta-item">
              <span class="hero-meta-key">用例号</span>
              {{ detail?.testCase.caseNo || '—' }}
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">模块</span>
              {{ detail?.testCase.moduleName || '—' }}
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">功能</span>
              {{ detail?.testCase.featureName || '—' }}
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">项目</span>
              {{ detail?.testCase.projectName || `项目#${detail?.testCase.projectId}` }}（{{ detail?.testCase.projectCode || '-' }}）
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">版本</span>
              {{ detail?.testCase.versionNo || `版本#${detail?.testCase.versionId}` }} · {{ detail?.testCase.versionName || '未命名' }}
            </span>
          </div>
          <div class="hero-requirements">
            <span class="hero-meta-key">需求资产</span>
            <el-tooltip
              v-if="(detail?.testCase.requirementAssets || []).length"
              placement="top"
              raw-content
            >
              <template #content>
                <div class="tc-asset-tooltip" style="white-space: pre-line">{{ requirementAssetsTooltipBody }}</div>
              </template>
              <span>{{ requirementAssetsSummary }}</span>
            </el-tooltip>
            <span v-else>—</span>
          </div>
          <div class="hero-tags">
            <el-tag
              v-if="detail?.testCase.priority"
              class="hero-priority-tag"
              :type="priorityTagType(detail.testCase.priority)"
              effect="dark"
            >
              {{ detail.testCase.priority }} · 优先级
            </el-tag>
            <el-tag v-else type="info" effect="plain" size="small">优先级 —</el-tag>
            <el-tag size="small" effect="dark" :type="reviewStatusTagType(detail?.testCase.reviewStatus)">
              评审 · {{ reviewStatusLabel(detail?.testCase.reviewStatus) }}
            </el-tag>
            <el-tag size="small" effect="dark" :type="executionStatusTagType(detail?.testCase.executionStatus)">
              执行 · {{ executionStatusLabel(detail?.testCase.executionStatus) }}
            </el-tag>
          </div>
          <div class="hero-times">
            <span>创建 {{ formatDateTime(detail?.testCase.createdAt) }}</span>
            <span class="hero-times-dot">·</span>
            <span>更新 {{ formatDateTime(detail?.testCase.updatedAt) }}</span>
          </div>
        </div>
        <div class="hero-actions">
          <el-button type="primary" plain @click="openEditDialog">编辑</el-button>
          <el-button type="success" @click="openReviewDialog">评审</el-button>
          <el-button type="warning" @click="openExecuteDialog">执行</el-button>
          <el-button @click="goBack">返回列表</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>评审与执行</template>
      <el-row :gutter="12" class="status-grid">
        <el-col :span="12">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="评审状态">
              <el-tag size="small" :type="reviewStatusTagType(detail?.testCase.reviewStatus)">
                {{ reviewStatusLabel(detail?.testCase.reviewStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="评审时间">{{ formatDateTime(reviewTimeDisplay) }}</el-descriptions-item>
            <el-descriptions-item label="评审意见">{{ reviewRemarkDisplay }}</el-descriptions-item>
          </el-descriptions>
        </el-col>

        <el-col :span="12">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="执行状态">
              <el-tag size="small" :type="executionStatusTagType(detail?.testCase.executionStatus)">
                {{ executionStatusLabel(detail?.testCase.executionStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="执行时间">{{ formatDateTime(executionTimeDisplay) }}</el-descriptions-item>
            <el-descriptions-item label="执行备注">
              {{ executionRemarkDisplay }}
            </el-descriptions-item>
          </el-descriptions>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>内容</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="前置条件">{{ detail?.testCase.precondition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="步骤">
          <div class="block-scroll">
            <pre class="block">{{ detail?.testCase.steps }}</pre>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="测试数据">
          <div class="block-scroll">
            <pre class="block">{{ detail?.testCase.testData || '-' }}</pre>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="预期结果">
          <div class="block-scroll">
            <pre class="block">{{ detail?.testCase.expectedResult }}</pre>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail?.testCase.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>状态变更日志（最近）</template>
      <el-table :data="detail?.statusLogs || []" border size="small" height="220">
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.changedAt) }}</template>
        </el-table-column>
        <el-table-column prop="fieldName" label="字段" width="140" />
        <el-table-column prop="oldValue" label="旧值" min-width="140" />
        <el-table-column prop="newValue" label="新值" min-width="140" />
        <el-table-column prop="reason" label="原因" min-width="220" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>历史快照（最近）</template>
      <el-table :data="detail?.histories || []" border size="small" height="220">
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.changedAt) }}</template>
        </el-table-column>
        <el-table-column prop="changeType" label="类型" width="140" />
        <el-table-column prop="snapshotJson" label="快照" min-width="420" />
      </el-table>
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

  <el-dialog
    v-model="editDialogVisible"
    title="编辑用例"
    width="860px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form label-position="top">
      <div class="edit-form-grid">
        <el-form-item label="模块（必填）">
          <el-input v-model="editForm.moduleName" />
        </el-form-item>
        <el-form-item label="功能（必填）">
          <el-input v-model="editForm.featureName" />
        </el-form-item>
        <el-form-item label="标题（必填）" class="full-span">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="前置条件" class="full-span">
          <el-input v-model="editForm.precondition" />
        </el-form-item>
        <el-form-item label="步骤（必填，建议1/2/3编号）" class="full-span">
          <el-input v-model="editForm.steps" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="测试数据" class="full-span">
          <el-input v-model="editForm.testData" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="预期结果（必填）" class="full-span">
          <el-input v-model="editForm.expectedResult" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="editForm.priority">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submitEditDialog">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero-card :deep(.el-card__body) {
  padding: 20px 22px;
}

.hero-layout {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.hero-title {
  margin: 0 0 12px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  row-gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
}

.hero-meta-key {
  color: var(--el-text-color-secondary);
  margin-right: 4px;
}

.hero-meta-sep {
  color: var(--el-border-color);
  margin: 0 10px;
  user-select: none;
  font-weight: 300;
}

.hero-requirements {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.tc-asset-tooltip {
  white-space: pre-line;
  max-width: 520px;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.hero-priority-tag {
  font-weight: 600;
}

.hero-times {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hero-times-dot {
  margin: 0 8px;
  opacity: 0.7;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
  flex-shrink: 0;
}

.block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.block-scroll {
  max-height: 240px;
  overflow: auto;
  padding-right: 6px;
}

.status-grid {
  margin-top: 0;
}

.dialog-section {
  margin-bottom: 12px;
}

.dialog-label {
  font-weight: 650;
  margin-bottom: 10px;
  color: #606266;
}

.dialog-radios :deep(.el-radio-button__inner) {
  padding: 10px 16px;
}

.edit-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}

.edit-form-grid .full-span {
  grid-column: 1 / -1;
}

:deep(.el-dialog__body) {
  padding-top: 18px;
}
</style>

