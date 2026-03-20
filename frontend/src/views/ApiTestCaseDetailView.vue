<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, type ApiTestCaseDetail } from '../api/api'
import { priorityTagType } from '../utils/priorityTag'
import { formatDateTime } from '../utils/formatDateTime'
import {
  executionStatusLabel,
  executionStatusTagType,
  reviewStatusLabel,
  reviewStatusTagType,
} from '../utils/caseStatusDisplay'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<ApiTestCaseDetail | null>(null)

const executionRemarkDisplay = computed(() => {
  // 接口用例未持久化执行备注（与功能用例状态日志不同），此处占位与功能页布局一致
  return '—'
})

const reviewRemarkDisplay = computed(() => {
  const t = (detail.value?.testCase.reviewComment || '').trim()
  return t || '—'
})

const executionTimeDisplay = computed(() => detail.value?.testCase.lastExecutedAt)

const reviewTimeDisplay = computed(() => detail.value?.testCase.reviewedAt)

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
  requestJson: '{}',
  expectedJson: '{}',
  assertionsJson: '[]',
  priority: 'P2',
  remark: '',
})

const caseId = computed(() => {
  const val = route.query.caseId
  const id = typeof val === 'string' ? Number(val) : 0
  return Number.isInteger(id) && id > 0 ? id : 0
})

function prettyJson(s: string) {
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

async function loadDetail() {
  if (!caseId.value) {
    ElMessage.warning('缺少 caseId')
    return
  }
  loading.value = true
  try {
    detail.value = await api.getApiTestCaseDetail(caseId.value)
  } catch (e: any) {
    ElMessage.error(e.message || '加载详情失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/api-test-cases')
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
    await api.updateApiTestCaseStatus(id, { executionStatus, reason: reason || undefined })
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
  const nextReviewStatus = reviewResult.value
  const comment = reviewCommentInput.value.trim()
  if (nextReviewStatus === 'REJECTED' && !comment) {
    ElMessage.warning('驳回意见不能为空')
    return
  }
  try {
    await api.updateApiTestCaseStatus(id, {
      reviewStatus: nextReviewStatus,
      reviewComment: comment || undefined,
      reason: comment || undefined,
    })
    reviewDialogVisible.value = false
    await loadDetail()
    ElMessage.success('评审完成')
  } catch (e: any) {
    ElMessage.error(e.message || '评审失败')
  }
}

function openEditDialog() {
  const tc = detail.value?.testCase
  if (!tc) return
  editForm.value = {
    moduleName: tc.moduleName,
    featureName: tc.featureName,
    title: tc.title,
    requestJson: tc.requestJson,
    expectedJson: tc.expectedJson,
    assertionsJson: tc.assertionsJson,
    priority: tc.priority || 'P2',
    remark: tc.remark || '',
  }
  editDialogVisible.value = true
}

function validateJsonField(label: string, raw: string) {
  try {
    JSON.parse(raw)
    return true
  } catch {
    ElMessage.error(`${label} 不是合法 JSON`)
    return false
  }
}

async function submitEditDialog() {
  const tc = detail.value?.testCase
  if (!tc?.id) return
  const f = editForm.value
  if (!f.moduleName.trim() || !f.featureName.trim() || !f.title.trim()) {
    ElMessage.warning('请补全模块/功能/标题')
    return
  }
  if (!validateJsonField('请求数据', f.requestJson)) return
  if (!validateJsonField('预期结果', f.expectedJson)) return
  if (!validateJsonField('断言', f.assertionsJson)) return
  try {
    await api.updateApiTestCase(tc.id, {
      moduleName: f.moduleName.trim(),
      featureName: f.featureName.trim(),
      title: f.title.trim(),
      requestJson: f.requestJson.trim(),
      expectedJson: f.expectedJson.trim(),
      assertionsJson: f.assertionsJson.trim(),
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
            <el-descriptions-item label="执行备注">{{ executionRemarkDisplay }}</el-descriptions-item>
          </el-descriptions>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>备注</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="备注">
          <div class="remark-block">{{ detail?.testCase.remark || '—' }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>请求数据（JSON）</template>
      <pre class="json-block">{{ detail ? prettyJson(detail.testCase.requestJson) : '' }}</pre>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>预期结果（JSON）</template>
      <pre class="json-block">{{ detail ? prettyJson(detail.testCase.expectedJson) : '' }}</pre>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>断言（JSON）</template>
      <pre class="json-block">{{ detail ? prettyJson(detail.testCase.assertionsJson) : '' }}</pre>
    </el-card>

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
      title="编辑接口用例"
      width="900px"
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
          <el-form-item label="请求数据 requestJson（JSON）" class="full-span">
            <el-input v-model="editForm.requestJson" type="textarea" :rows="6" class="mono-input" />
          </el-form-item>
          <el-form-item label="预期结果 expectedJson（JSON）" class="full-span">
            <el-input v-model="editForm.expectedJson" type="textarea" :rows="5" class="mono-input" />
          </el-form-item>
          <el-form-item label="断言 assertionsJson（JSON）" class="full-span">
            <el-input v-model="editForm.assertionsJson" type="textarea" :rows="5" class="mono-input" />
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
  </div>
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

.section {
  margin-top: 0;
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

.remark-block {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.json-block {
  margin: 0;
  padding: 12px;
  background: #0d1117;
  color: #e6edf3;
  border-radius: 8px;
  overflow: auto;
  max-height: 480px;
  font-size: 0.85rem;
  line-height: 1.45;
}

.edit-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}

.edit-form-grid .full-span {
  grid-column: 1 / -1;
}

.mono-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

:deep(.el-dialog__body) {
  padding-top: 18px;
}
</style>
