<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, type Asset } from '../api/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const assets = ref<Asset[]>([])

const relationCode = computed(() => {
  const val = route.query.relationCode
  return typeof val === 'string' ? val : ''
})

const textAsset = computed(() => assets.value.find((a) => a.assetType === 'TEXT') || null)
const fileAssets = computed(() => assets.value.filter((a) => a.assetType === 'FILE'))
const prototypeAssets = computed(() => assets.value.filter((a) => a.assetType === 'PROTOTYPE'))

/** 同批资产同属一项目/版本，取首条上的展示字段 */
const scopeLine = computed(() => {
  const a = assets.value[0]
  if (!a) {
    return ''
  }
  const pName = a.projectName?.trim() || '—'
  const pCode = a.projectCode?.trim() || '—'
  const vNo = a.versionNo?.trim() || '—'
  const vName = a.versionName?.trim() || '未命名'
  return `项目：${pName}（${pCode}） · 版本：${vNo} · ${vName}`
})

const assetCodesSummary = computed(() => {
  const set = new Set<string>()
  for (const a of assets.value) {
    if (a.assetCode) {
      set.add(a.assetCode.trim())
    }
  }
  const list = Array.from(set).filter(Boolean)
  if (list.length === 0) return '—'
  if (list.length === 1) return list[0]
  return `${list[0]} · 等 ${list.length} 条`
})

const titleSummary = computed(() => {
  const t0 = textAsset.value?.title?.trim()
  if (t0) return t0
  const anyTitle = assets.value.find((a) => a.title && String(a.title).trim())?.title
  return anyTitle ? String(anyTitle).trim() : '—'
})

const assetTypeSummary = computed(() => {
  const parts: string[] = []
  if (textAsset.value) parts.push('文本')
  if (fileAssets.value.length) parts.push('需求文档')
  if (prototypeAssets.value.length) parts.push('原型图')
  return parts.length ? parts.join('、') : '—'
})

async function loadDetail() {
  if (!relationCode.value) {
    ElMessage.warning('缺少 relationCode')
    return
  }
  loading.value = true
  try {
    if (relationCode.value.startsWith('LEGACY-')) {
      const id = Number(relationCode.value.replace('LEGACY-', ''))
      if (!Number.isInteger(id) || id <= 0) {
        throw new Error('legacy relationCode 无效')
      }
      const item = await api.getAsset(id)
      assets.value = [item]
    } else {
      const data = await api.getAllAssets({ relationCode: relationCode.value, pageNo: 1, pageSize: 200 })
      assets.value = data.records
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载详情失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/assets')
}

onMounted(loadDetail)
</script>

<template>
  <div class="asset-detail-page" v-loading="loading">
    <el-card shadow="never" class="asset-hero-card">
      <div class="hero-layout">
        <div class="hero-main">
          <h1 class="hero-title">{{ titleSummary }}</h1>
          <div class="hero-meta">
            <span class="hero-meta-item">
              <span class="hero-meta-key">关联批次</span>
              {{ relationCode || '-' }}
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">资产编码</span>
              {{ assetCodesSummary }}
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">项目/版本</span>
              {{ scopeLine || '-' }}
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">类型</span>
              {{ assetTypeSummary }}
            </span>
          </div>
        </div>
        <div class="hero-actions">
          <el-button @click="goBack">返回需求资产</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>需求描述</template>
      <div v-if="textAsset">
        <h4>{{ textAsset.title }}</h4>
        <pre class="text-content">{{ textAsset.content }}</pre>
      </div>
      <el-empty v-else description="暂无需求描述" />
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>需求文档</template>
      <el-table :data="fileAssets" border>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="fileName" label="文件名" min-width="260" />
        <el-table-column prop="fileSize" label="大小" width="120" />
      </el-table>
      <el-empty v-if="fileAssets.length === 0" description="暂无需求文档" />
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>原型图</template>
      <el-table :data="prototypeAssets" border>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="fileName" label="文件名" min-width="260" />
        <el-table-column prop="fileSize" label="大小" width="120" />
      </el-table>
      <el-empty v-if="prototypeAssets.length === 0" description="暂无原型图" />
    </el-card>
  </div>
</template>

<style scoped>
.asset-detail-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.asset-hero-card :deep(.el-card__body) {
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
}

.hero-meta-item {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  flex-wrap: nowrap;
  gap: 6px;
}

.hero-meta-key {
  color: var(--el-text-color-secondary);
}

.hero-meta-sep {
  color: var(--el-border-color);
  margin: 0 10px;
  user-select: none;
  font-weight: 300;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
  flex-shrink: 0;
}

.meta {
  color: #909399;
  font-size: 13px;
}

.scope-line {
  margin-top: 4px;
}

.section {
  margin-top: 0;
}

.text-content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
</style>
