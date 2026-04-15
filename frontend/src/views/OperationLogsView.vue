<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue'
import { api } from '../api/api'
import { ElMessage } from 'element-plus'
import {
  ACTION_LABELS,
  OBJECT_TYPE_LABELS,
  actionLabel,
  objectTypeLabel,
  operationObjectResolvedName,
  type OperationLogRow,
} from '../utils/operationLogDisplay'
import { formatDateTime } from '../utils/formatDateTime'

type LogItem = OperationLogRow & { operatorId: number }

const loading = ref(false)
const objectType = ref('')
const action = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const logs = ref<LogItem[]>([])
const tableDensity = inject<Ref<'default' | 'small'>>('tableDensity', ref('default'))

const objectTypeOptions = Object.entries(OBJECT_TYPE_LABELS).map(([value, label]) => ({ value, label }))
const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))

async function loadLogs() {
  loading.value = true
  try {
    const data = await api.getOperationLogs({
      objectType: objectType.value,
      action: action.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    logs.value = data.records
    total.value = data.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  loadLogs()
}

function onPageNoChange(value: number) {
  pageNo.value = value
  loadLogs()
}

function onPageSizeChange(value: number) {
  pageSize.value = value
  pageNo.value = 1
  loadLogs()
}

onMounted(loadLogs)
</script>

<template>
  <div class="page-shell">
    <el-card class="query-card">
      <div class="query-row">
        <div class="query-filters">
          <el-select v-model="objectType" filterable clearable placeholder="对象类型" style="width: 220px">
            <el-option v-for="item in objectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="action" filterable clearable placeholder="操作" style="width: 220px">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="query-actions">
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="objectType = ''; action = ''; onSearch()">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-body">
        <el-table :data="logs" :size="tableDensity" v-loading="loading" border stripe height="100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="对象类型" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tooltip placement="top" :show-after="400">
                <template #content>
                  <div>代码 {{ row.objectType }}</div>
                </template>
                <span>{{ objectTypeLabel(row.objectType) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="名称 / 标识" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ operationObjectResolvedName(row) || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="对象 ID" width="100" align="right">
            <template #default="{ row }">{{ row.objectId != null ? row.objectId : '—' }}</template>
          </el-table-column>
          <el-table-column prop="action" label="操作" width="160">
            <template #default="{ row }">
              <el-tooltip :content="row.action" placement="top">
                <span>{{ actionLabel(row.action) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="120" />
          <el-table-column prop="remark" label="备注" min-width="160">
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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
          @current-change="onPageNoChange"
          @size-change="onPageSizeChange"
        />
      </div>
    </el-card>
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

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-header span {
  color: #909399;
  font-size: 13px;
}
</style>
