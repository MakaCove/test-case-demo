<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, type Project, type UiNlCase, type UiNlTask, type Version } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const router = useRouter()

const projects = ref<Project[]>([])
const versions = ref<Version[]>([])
const cases = ref<UiNlCase[]>([])
const projectId = ref('')
const versionId = ref('')
const status = ref('')

const filteredVersions = computed(() => {
  const pid = Number(projectId.value)
  if (!pid) return versions.value
  return versions.value.filter((v) => v.projectId === pid)
})

const loading = ref(false)
const records = ref<UiNlTask[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const createVisible = ref(false)
const editVisible = ref(false)
const editingId = ref(0)
const formProjectId = ref('')
const formVersionId = ref('')
const formCaseId = ref('')
const formHeadless = ref(false)
const formBrowser = ref('chromium')
const formModelConfigId = ref('')
const formPromptTemplateId = ref('')
const formTimeout = ref(600)
const creating = ref(false)
const modelConfigs = ref<Array<{ id: number; name: string; provider: string; modelKey: string; status: string }>>([])
const promptTemplates = ref<Array<{ id: number; name: string; scopeType: string; scopeId?: number; versionNo: number; status: string }>>([])

const filteredFormVersions = computed(() => {
  const pid = Number(formProjectId.value)
  if (!pid) return versions.value
  return versions.value.filter((v) => v.projectId === pid)
})

async function loadProjects() {
  const data = await api.getProjects({ pageNo: 1, pageSize: 200 })
  projects.value = data.records
}

async function loadVersions() {
  const data = await api.getAllVersions({ pageNo: 1, pageSize: 500 })
  versions.value = data.records
}

async function loadModelAndPromptOptions() {
  const [models, prompts] = await Promise.all([
    api.getModelConfigs({}),
    api.getPromptTemplates({}),
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
}

function modelConfigLabel(id?: number) {
  if (!id) return '—'
  const m = modelConfigs.value.find((x) => x.id === id)
  return m ? m.name : `模型#${id}`
}

function promptTemplateLabel(id?: number) {
  if (!id) return '—'
  const p = promptTemplates.value.find((x) => x.id === id)
  return p ? p.name : `模板#${id}`
}

function statusLabel(status?: string) {
  if (!status) return '—'
  const s = status.trim().toUpperCase()
  if (s === 'PENDING') return '待启动'
  if (s === 'QUEUED') return '排队'
  if (s === 'PLANNING') return '生成中'
  if (s === 'READY') return '待执行'
  if (s === 'RUNNING') return '执行中'
  if (s === 'COMPLETED') return '完成'
  if (s === 'FAILED') return '失败'
  if (s === 'INTERRUPTED') return '中断'
  if (s === 'CANCELLED') return '取消'
  return status
}

watch(projectId, () => {
  versionId.value = ''
})

watch(formProjectId, () => {
  if (editingId.value) {
    return
  }
  formVersionId.value = ''
  formCaseId.value = ''
  cases.value = []
})

async function loadCasesForVersion() {
  if (!Number(formProjectId.value) || !Number(formVersionId.value)) {
    cases.value = []
    formCaseId.value = ''
    return
  }
  const data = await api.getUiNlCases({
    pageNo: 1,
    pageSize: 200,
    projectId: Number(formProjectId.value),
    versionId: Number(formVersionId.value),
  })
  cases.value = data.records.filter((c) => c.status === 'ENABLED')
}

async function loadRecords() {
  loading.value = true
  try {
    const data = await api.getUiNlTasks({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: Number(projectId.value) || undefined,
      versionId: Number(versionId.value) || undefined,
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

function openCreate() {
  editingId.value = 0
  formProjectId.value = ''
  formVersionId.value = ''
  formCaseId.value = ''
  formHeadless.value = false
  formBrowser.value = 'chromium'
  formModelConfigId.value = ''
  formPromptTemplateId.value = ''
  formTimeout.value = 600
  cases.value = []
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
      if (!formModelConfigId.value && modelConfigs.value.length) {
        formModelConfigId.value = String(modelConfigs.value[0].id)
      }
      if (!formPromptTemplateId.value && promptTemplates.value.length) {
        formPromptTemplateId.value = String(promptTemplates.value[0].id)
      }
    })
    .catch((e: any) => {
      ElMessage.error(e?.message || '加载模型/Prompt失败')
    })
}

async function openEdit(row: UiNlTask) {
  editingId.value = row.id
  formProjectId.value = String(row.projectId)
  formVersionId.value = String(row.versionId)
  await loadCasesForVersion()
  formCaseId.value = String(row.uiNlCaseId)
  formHeadless.value = !!row.headless
  formBrowser.value = row.browserName || 'chromium'
  formModelConfigId.value = row.modelConfigId ? String(row.modelConfigId) : ''
  formPromptTemplateId.value = row.promptTemplateId ? String(row.promptTemplateId) : ''
  formTimeout.value = row.timeoutSeconds || 600
  editVisible.value = true
}

async function submitCreate() {
  if (!Number(formProjectId.value) || !Number(formVersionId.value) || !Number(formCaseId.value)) {
    ElMessage.warning('请选择项目、版本和自然语言用例')
    return
  }
  if (!Number(formModelConfigId.value) || !Number(formPromptTemplateId.value)) {
    ElMessage.warning('请选择模型配置和 Prompt 模板')
    return
  }
  creating.value = true
  try {
    if (editingId.value) {
      await api.updateUiNlTask(editingId.value, {
        uiNlCaseId: Number(formCaseId.value),
        modelConfigId: Number(formModelConfigId.value),
        promptTemplateId: Number(formPromptTemplateId.value),
        headless: formHeadless.value,
        browserName: formBrowser.value,
        timeoutSeconds: formTimeout.value,
      })
      editVisible.value = false
      ElMessage.success('任务已更新')
    } else {
      await api.createUiNlTask({
        projectId: Number(formProjectId.value),
        versionId: Number(formVersionId.value),
        uiNlCaseId: Number(formCaseId.value),
        modelConfigId: Number(formModelConfigId.value),
        promptTemplateId: Number(formPromptTemplateId.value),
        headless: formHeadless.value,
        browserName: formBrowser.value,
        timeoutSeconds: formTimeout.value,
      })
      createVisible.value = false
      ElMessage.success('任务已创建')
    }
    await loadRecords()
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function onExecute(row: UiNlTask) {
  await api.executeUiNlTask(row.id)
  ElMessage.success('已进入生成队列')
  await loadRecords()
}

async function onInterrupt(row: UiNlTask) {
  const ok = await ElMessageBox.confirm(`确认中断任务 ${row.taskNo} 吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  await api.interruptUiNlTask(row.id, 'manual interrupt')
  ElMessage.success('已中断')
  await loadRecords()
}

async function onDelete(row: UiNlTask) {
  const ok = await ElMessageBox.confirm(`确认删除任务 ${row.taskNo} 吗？`, '提示', { type: 'warning' }).catch(() => false)
  if (!ok) return
  await api.deleteUiNlTask(row.id)
  ElMessage.success('已删除')
  await loadRecords()
}

function gotoSteps(row: UiNlTask) {
  router.push({ path: '/ui-nl-steps/detail', query: { taskId: String(row.id) } })
}

// 报告入口从“步骤管理/报告中心”进入，任务中心不再提供入口

onMounted(async () => {
  await loadProjects()
  await loadVersions()
  await loadModelAndPromptOptions()
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
        <el-select v-model="status" clearable placeholder="状态" style="width: 160px">
          <el-option label="待启动" value="PENDING" />
          <el-option label="排队" value="QUEUED" />
          <el-option label="生成中" value="PLANNING" />
          <el-option label="待执行" value="READY" />
          <el-option label="失败" value="FAILED" />
          <el-option label="中断" value="INTERRUPTED" />
        </el-select>
        <div class="query-actions">
          <el-button type="primary" @click="() => { pageNo = 1; loadRecords() }">查询</el-button>
          <el-button @click="() => { projectId=''; versionId=''; status=''; pageNo=1; loadRecords() }">重置</el-button>
          <el-button type="success" @click="openCreate">新建UI自然语言任务</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="records" :size="tableDensity" border stripe v-loading="loading" height="100%">
        <el-table-column prop="taskNo" label="任务号" min-width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="browserName" label="浏览器" width="100" />
        <el-table-column label="无头" width="80">
          <template #default="{ row }">{{ row.headless ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="模型配置" min-width="140">
          <template #default="{ row }">{{ modelConfigLabel(row.modelConfigId) }}</template>
        </el-table-column>
        <el-table-column label="Prompt模板" min-width="140">
          <template #default="{ row }">{{ promptTemplateLabel(row.promptTemplateId) }}</template>
        </el-table-column>
        <el-table-column prop="modelKey" label="模型Key" min-width="120" />
        <el-table-column prop="timeoutSeconds" label="超时(s)" width="90" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="success"
              :disabled="['QUEUED','PLANNING','RUNNING','COMPLETED'].includes(row.status)"
              @click="onExecute(row)"
            >
              {{ ['FAILED', 'INTERRUPTED'].includes(row.status) ? '重试' : '生成步骤' }}
            </el-button>
            <el-divider direction="vertical" />
            <el-button link type="warning" :disabled="!['QUEUED','PLANNING'].includes(row.status)" @click="onInterrupt(row)">中断</el-button>
            <el-divider direction="vertical" />
            <el-button link type="primary" :disabled="['QUEUED','PLANNING','RUNNING'].includes(row.status)" @click="openEdit(row)">编辑</el-button>
            <el-divider direction="vertical" />
            <el-button link type="danger" :disabled="['QUEUED','PLANNING','RUNNING'].includes(row.status)" @click="onDelete(row)">删除</el-button>
            <el-divider direction="vertical" />
            <el-button link @click="gotoSteps(row)">步骤</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="() => { pageNo = 1; loadRecords() }"
        />
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="新建 UI 自然语言任务" width="720px" :close-on-click-modal="false">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="项目">
            <el-select v-model="formProjectId" filterable @change="loadCasesForVersion">
              <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="版本">
            <el-select v-model="formVersionId" filterable @change="loadCasesForVersion">
              <el-option
                v-for="v in filteredFormVersions"
                :key="v.id"
                :label="`${v.versionNo}${v.name ? ` - ${v.name}` : ''}`"
                :value="String(v.id)"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="自然语言用例">
          <el-select v-model="formCaseId" filterable placeholder="选择自然语言用例">
            <el-option v-for="c in cases" :key="c.id" :label="`${c.caseNo} · ${c.title}`" :value="String(c.id)" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="浏览器">
            <el-select v-model="formBrowser">
              <el-option label="chromium" value="chromium" />
              <el-option label="chrome" value="chrome" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型配置">
            <el-select v-model="formModelConfigId" filterable placeholder="选择模型配置">
              <el-option
                v-for="m in modelConfigs"
                :key="m.id"
                :label="`${m.name}（${m.provider}/${m.modelKey}）`"
                :value="String(m.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Prompt模板">
            <el-select v-model="formPromptTemplateId" filterable placeholder="选择Prompt模板">
              <el-option
                v-for="p in promptTemplates"
                :key="p.id"
                :label="`${p.name}（${p.scopeType} v${p.versionNo}）`"
                :value="String(p.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="超时时间（秒）">
            <el-input-number v-model="formTimeout" :min="30" :max="3600" controls-position="right" />
          </el-form-item>
          <el-form-item label="执行模式">
            <el-switch v-model="formHeadless" inline-prompt active-text="无头" inactive-text="有界面" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑 UI 自然语言任务" width="720px" :close-on-click-modal="false">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="项目">
            <el-select v-model="formProjectId" disabled>
              <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.code}）`" :value="String(p.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="版本">
            <el-select v-model="formVersionId" disabled>
              <el-option
                v-for="v in filteredFormVersions"
                :key="v.id"
                :label="`${v.versionNo}${v.name ? ` - ${v.name}` : ''}`"
                :value="String(v.id)"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="自然语言用例">
          <el-select v-model="formCaseId" filterable placeholder="选择自然语言用例">
            <el-option v-for="c in cases" :key="c.id" :label="`${c.caseNo} · ${c.title}`" :value="String(c.id)" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="浏览器">
            <el-select v-model="formBrowser">
              <el-option label="chromium" value="chromium" />
              <el-option label="chrome" value="chrome" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型配置">
            <el-select v-model="formModelConfigId" filterable placeholder="选择模型配置">
              <el-option
                v-for="m in modelConfigs"
                :key="m.id"
                :label="`${m.name}（${m.provider}/${m.modelKey}）`"
                :value="String(m.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Prompt模板">
            <el-select v-model="formPromptTemplateId" filterable placeholder="选择Prompt模板">
              <el-option
                v-for="p in promptTemplates"
                :key="p.id"
                :label="`${p.name}（${p.scopeType} v${p.versionNo}）`"
                :value="String(p.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="超时时间（秒）">
            <el-input-number v-model="formTimeout" :min="30" :max="3600" controls-position="right" />
          </el-form-item>
          <el-form-item label="执行模式">
            <el-switch v-model="formHeadless" inline-prompt active-text="无头" inactive-text="有界面" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell { height: 100%; display: grid; grid-template-rows: auto 1fr; gap: 12px; }
.query-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.query-actions { margin-left: auto; display: inline-flex; gap: 10px; align-items: center; }
.table-card { min-height: 0; display: flex; flex-direction: column; }
.table-card :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.pager { display: flex; justify-content: flex-end; padding-top: 10px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(220px, 1fr)); gap: 12px; }
</style>
