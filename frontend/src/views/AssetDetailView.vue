<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, type Asset } from '../api/api'
import { formatBytes } from '../utils/formatBytes'

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

const hasText = computed(() => !!textAsset.value)
const hasFile = computed(() => fileAssets.value.length > 0)

/** 与列表「录入方式」一致 */
const requirementEntrySummary = computed(() => {
  if (hasText.value && hasFile.value) return '混合（手动 + 文档）'
  if (hasText.value) return '手动描述'
  if (hasFile.value) return '文档提取'
  return '—'
})

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

/** 列表标题口径：优先手动描述资产标题，其次文档资产标题（避免误用原型图标题） */
const titleSummary = computed(() => {
  const t0 = textAsset.value?.title?.trim()
  if (t0) return t0
  const f0 = fileAssets.value.find((a) => a.title?.trim())
  if (f0?.title) return String(f0.title).trim()
  const anyReq = assets.value.find(
    (a) => (a.assetType === 'TEXT' || a.assetType === 'FILE') && String(a.title || '').trim(),
  )
  return anyReq?.title ? String(anyReq.title).trim() : '—'
})

const assetTypeSummary = computed(() => {
  const parts: string[] = []
  if (textAsset.value) parts.push('文本')
  if (fileAssets.value.length) parts.push('需求文档')
  if (prototypeAssets.value.length) parts.push('原型图')
  return parts.length ? parts.join('、') : '—'
})

/**
 * 正文展示：与新建弹窗「二选一」一致——有手动描述且非空则优先；否则展示文档提取的正文。
 */
const primaryRequirementBody = computed(() => {
  const t = textAsset.value
  if (t?.content != null && String(t.content).trim()) {
    return {
      kind: 'TEXT' as const,
      tag: '手动录入',
      sublines: t.title?.trim() ? [`标题：${t.title.trim()}`] : [],
      content: String(t.content),
    }
  }
  const f = fileAssets.value.find((a) => a.content != null && String(a.content).trim())
  if (f) {
    const sublines: string[] = []
    if (f.title?.trim()) sublines.push(`资产标题：${f.title.trim()}`)
    if (f.fileName?.trim()) sublines.push(`上传时原文件名：${f.fileName.trim()}`)
    return {
      kind: 'FILE' as const,
      tag: '文档提取',
      sublines,
      content: String(f.content),
    }
  }
  return null
})

const showMixedRequirementHint = computed(
  () => hasText.value && hasFile.value && primaryRequirementBody.value?.kind === 'TEXT',
)

/** 批次内既有文本资产又有文档，但文本正文为空时，正文区实际展示的是文档提取 */
const showDocPreferredHint = computed(
  () => hasText.value && hasFile.value && primaryRequirementBody.value?.kind === 'FILE',
)

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

watch(
  relationCode,
  (code) => {
    if (code) void loadDetail()
  },
  { immediate: true },
)
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
              <span class="hero-meta-key">需求录入</span>
              {{ requirementEntrySummary }}
            </span>
            <span class="hero-meta-sep" aria-hidden="true">|</span>
            <span class="hero-meta-item">
              <span class="hero-meta-key">资产类型</span>
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
      <template #header>
        <div class="section-card-header">
          <span>需求正文</span>
          <el-text type="info" size="small">与新建时「手动描述 / 文档提取」一致；有手动正文时优先展示</el-text>
        </div>
      </template>

      <el-alert
        v-if="showMixedRequirementHint"
        type="info"
        :closable="false"
        show-icon
        class="mix-hint"
      >
        本批次同时存在手动描述与文档记录。下方正文为<strong>手动录入</strong>；文档元数据与提取结果见「文档记录」表格。
      </el-alert>
      <el-alert
        v-else-if="showDocPreferredHint"
        type="info"
        :closable="false"
        show-icon
        class="mix-hint"
      >
        本批次存在文本资产但正文为空，下方展示的是<strong>文档提取</strong>正文；完整文档元数据见「文档记录」表格。
      </el-alert>

      <div v-if="primaryRequirementBody" class="body-wrap">
        <el-tag
          size="small"
          :type="primaryRequirementBody.kind === 'TEXT' ? 'success' : 'info'"
          class="body-tag"
        >
          {{ primaryRequirementBody.tag }}
        </el-tag>
        <ul v-if="primaryRequirementBody.sublines.length" class="body-sublines">
          <li v-for="(line, i) in primaryRequirementBody.sublines" :key="i">{{ line }}</li>
        </ul>
        <pre class="text-content">{{ primaryRequirementBody.content }}</pre>
      </div>
      <el-empty
        v-else
        description="暂无正文（未填写手动描述，且文档未提取到内容或尚未上传文档）"
      />
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="section-card-header">
          <span>文档记录</span>
          <el-text type="info" size="small">文档类资产元数据；当前流程保存后一般仅保留提取正文，不长期保留原件</el-text>
        </div>
      </template>
      <div v-if="fileAssets.length">
        <el-table :data="fileAssets" border>
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="fileName" label="上传时原文件名" min-width="220" show-overflow-tooltip />
          <el-table-column label="大小" width="110" align="right">
            <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="已提取正文" width="110" align="center">
            <template #default="{ row }">{{ row.content && String(row.content).trim() ? '有' : '无' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else description="暂无文档类资产" />
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="section-card-header">
          <span>原型图</span>
          <el-text type="info" size="small">落盘路径由服务端配置</el-text>
        </div>
      </template>
      <div v-if="prototypeAssets.length">
        <el-table :data="prototypeAssets" border>
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column label="大小" width="110" align="right">
            <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="filePath" label="存储路径" min-width="280" show-overflow-tooltip />
        </el-table>
      </div>
      <el-empty v-else description="暂无原型图" />
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
  flex-shrink: 0;
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

.section {
  margin-top: 0;
}

.section-card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.section-card-header > span:first-child {
  font-weight: 600;
}

.mix-hint {
  margin-bottom: 14px;
}

.body-wrap {
  padding-top: 4px;
}

.body-tag {
  vertical-align: middle;
}

.body-sublines {
  margin: 10px 0 8px;
  padding-left: 1.2em;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.text-content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  padding: 12px 14px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.55;
}
</style>
