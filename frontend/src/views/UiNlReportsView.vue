<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, type UiNlReport } from '../api/api'
import { formatDateTime } from '../utils/formatDateTime'

const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))
const route = useRoute()

const projectId = ref('')
const versionId = ref('')
const status = ref('')
const loading = ref(false)
const records = ref<UiNlReport[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const current = ref<UiNlReport | null>(null)

async function loadReports() {
  loading.value = true
  try {
    const data = await api.getUiNlReports({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: Number(projectId.value) || undefined,
      versionId: Number(versionId.value) || undefined,
      status: status.value || undefined,
    })
    let rows = data.records
    const qTaskId = Number(route.query.taskId || 0)
    if (qTaskId) {
      rows = rows.filter((r) => r.taskId === qTaskId)
    }
    records.value = rows
    total.value = qTaskId ? rows.length : data.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载报告失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(row: UiNlReport) {
  current.value = await api.getUiNlReport(row.id)
  detailVisible.value = true
}

onMounted(loadReports)
</script>

<template>
  <div class="page-shell">
    <el-card>
      <div class="query-row">
        <el-input v-model="projectId" placeholder="项目ID" style="width: 140px" />
        <el-input v-model="versionId" placeholder="版本ID" style="width: 140px" />
        <el-select v-model="status" clearable placeholder="状态" style="width: 140px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="取消" value="CANCELLED" />
        </el-select>
        <el-button type="primary" @click="() => { pageNo = 1; loadReports() }">查询</el-button>
        <el-button @click="() => { projectId=''; versionId=''; status=''; pageNo=1; loadReports() }">重置</el-button>
      </div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="records" :size="tableDensity" border stripe v-loading="loading" height="100%">
        <el-table-column prop="reportNo" label="报告编号" min-width="140" />
        <el-table-column prop="taskId" label="任务ID" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="totalSteps" label="总步数" width="90" />
        <el-table-column prop="passedSteps" label="成功" width="80" />
        <el-table-column prop="failedSteps" label="失败" width="80" />
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="开始" width="170"><template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template></el-table-column>
        <el-table-column label="结束" width="170"><template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template></el-table-column>
        <el-table-column label="创建" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadReports"
          @size-change="() => { pageNo = 1; loadReports() }"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="报告详情" size="720px">
      <div v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="报告编号">{{ current.reportNo }}</el-descriptions-item>
          <el-descriptions-item label="任务ID">{{ current.taskId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ current.status }}</el-descriptions-item>
          <el-descriptions-item label="步数">{{ current.totalSteps }} / 成功 {{ current.passedSteps }} / 失败 {{ current.failedSteps }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(current.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatDateTime(current.finishedAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <el-form label-position="top">
          <el-form-item label="摘要"><el-input :model-value="current.summary || ''" type="textarea" :rows="3" readonly /></el-form-item>
          <el-form-item label="reportJson"><el-input :model-value="current.reportJson || ''" type="textarea" :rows="8" readonly /></el-form-item>
          <el-form-item label="artifactsJson"><el-input :model-value="current.artifactsJson || ''" type="textarea" :rows="6" readonly /></el-form-item>
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-shell { height: 100%; display: grid; grid-template-rows: auto 1fr; gap: 12px; }
.query-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.table-card { min-height: 0; display: flex; flex-direction: column; }
.table-card :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.pager { display: flex; justify-content: flex-end; padding-top: 10px; }
</style>
