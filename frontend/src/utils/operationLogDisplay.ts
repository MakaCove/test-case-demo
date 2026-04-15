/** 操作日志列表行（与后端 operation_logs 及 OperationLogsView 一致） */
export type OperationLogRow = {
  id: number
  objectType: string
  objectId?: number | null
  action: string
  beforeJson?: string
  afterJson?: string
  operatorName?: string
  remark?: string
  createdAt: string
}

export const OBJECT_TYPE_LABELS: Record<string, string> = {
  PROJECT: '项目',
  VERSION: '版本',
  ASSET: '需求资产',
  TEST_CASE: '功能用例',
  API_TEST_CASE: '接口用例',
  TASK: '生成任务',
  EXPORT: '导出',
  MODEL_CONFIG: '模型配置',
  PROMPT_TEMPLATE: 'Prompt 模板',
  UI_NL_CASE: 'UI自然语言用例',
  UI_NL_TASK: 'UI自然语言任务',
  UI_NL_PLAN_STEP: 'UI规划步骤',
}

export const ACTION_LABELS: Record<string, string> = {
  CREATE: '新增',
  CREATE_TEXT: '新增文本',
  UPDATE: '修改',
  DELETE: '删除',
  DELETE_BATCH: '批量删除',
  BATCH_DELETE: '批量删除',
  BATCH_UPDATE: '批量更新',
  PUBLISH: '发布',
  SUBMIT: '提交',
  START: '启动',
  RETRY: '重试',
  CANCEL: '取消',
  INTERRUPT: '中断',
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  QUEUED: '排队中',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
  ENABLED: '启用',
  DISABLED: '停用',
  STATUS_UPDATE: '状态更新',
  MATERIALIZE_TEST_CASES: '生成功能用例',
  MATERIALIZE_API_TEST_CASES: '生成接口用例',
  ENQUEUE_PLAN: '入队规划',
  READY: '规划就绪',
  RUN: '开始执行',
  EXEC_COMPLETED: '执行完成',
  EXEC_FAILED: '执行失败',
}

export function objectTypeLabel(code: string) {
  return OBJECT_TYPE_LABELS[code] || code
}

export function actionLabel(code: string) {
  const normalized = (code || '').trim().toUpperCase()
  if (!normalized) return '-'
  if (ACTION_LABELS[normalized]) return ACTION_LABELS[normalized]
  if (normalized.startsWith('UPLOAD_')) {
    const suffix = normalized.slice('UPLOAD_'.length)
    const suffixLabel: Record<string, string> = {
      TEXT: '文本',
      IMAGE: '图片',
      FILE: '文件',
      PROTOTYPE: '原型',
      OTHER: '其他',
    }
    return `上传资产（${suffixLabel[suffix] || suffix}）`
  }
  return code
}

function strVal(v: unknown): string | null {
  if (v == null) return null
  const s = String(v).trim()
  return s || null
}

function parseSnapshot(raw?: string | null): Record<string, unknown> | null {
  if (!raw?.trim()) return null
  try {
    const o = JSON.parse(raw) as unknown
    if (o && typeof o === 'object' && !Array.isArray(o)) return o as Record<string, unknown>
  } catch {
    /* ignore */
  }
  return null
}

function nameFromSnapshot(objectType: string, snap: Record<string, unknown>): string | null {
  const t = (objectType || '').trim().toUpperCase()
  const mod = strVal(snap.moduleName)
  const feat = strVal(snap.featureName)
  const modFeat = [mod, feat].filter(Boolean).join(' / ') || null

  switch (t) {
    case 'PROJECT':
      return strVal(snap.name) || strVal(snap.code)
    case 'VERSION': {
      const n = strVal(snap.name)
      const vn = strVal(snap.versionNo)
      if (n && vn) return `${n}（${vn}）`
      return n || vn
    }
    case 'ASSET':
      return strVal(snap.title) || strVal(snap.fileName) || strVal(snap.assetCode) || strVal(snap.relationCode)
    case 'TEST_CASE':
    case 'API_TEST_CASE':
      return strVal(snap.title) || modFeat || strVal(snap.caseNo)
    case 'TASK':
      return strVal(snap.taskNo)
    case 'EXPORT': {
      const no = strVal(snap.exportNo)
      if (no) return no
      const fmt = strVal(snap.format)
      const sc = strVal(snap.scope)
      if (fmt || sc) return [fmt, sc].filter(Boolean).join(' · ')
      return null
    }
    case 'MODEL_CONFIG':
      return strVal(snap.name) || strVal(snap.provider)
    case 'PROMPT_TEMPLATE': {
      const n = strVal(snap.name)
      const ver = snap.versionNo
      const vs = ver != null && ver !== '' ? String(ver) : null
      if (n && vs) return `${n}（v${vs}）`
      return n
    }
    case 'UI_NL_CASE':
      return strVal(snap.title) || strVal(snap.caseNo)
    case 'UI_NL_TASK':
      return strVal(snap.taskNo)
    case 'UI_NL_PLAN_STEP': {
      const st = strVal(snap.stepTitle)
      const sn = snap.stepNo != null ? String(snap.stepNo) : null
      if (st && sn) return `步骤 ${sn} · ${st}`
      return st || (sn ? `步骤 ${sn}` : null)
    }
    default:
      return null
  }
}

function nameFromSnapshotGeneric(snap: Record<string, unknown>): string | null {
  const keys = [
    'title',
    'name',
    'fileName',
    'taskNo',
    'exportNo',
    'caseNo',
    'code',
    'relationCode',
    'assetCode',
    'stepTitle',
  ] as const
  for (const k of keys) {
    const v = strVal(snap[k])
    if (v) return v
  }
  return null
}

/** 从快照解析出的业务名称（不含类型、不含数据库 ID） */
export function operationObjectResolvedName(row: OperationLogRow): string | null {
  const after = parseSnapshot(row.afterJson)
  const before = parseSnapshot(row.beforeJson)
  const snap = after ?? before
  if (!snap) return null
  return nameFromSnapshot(row.objectType, snap) ?? nameFromSnapshotGeneric(snap)
}

/** 列表/看板用的一行摘要（类型：名称 · id） */
export function operationObjectDisplay(row: OperationLogRow) {
  const typeZh = objectTypeLabel(row.objectType)
  const name = operationObjectResolvedName(row)
  if (name) {
    const max = 100
    const short = name.length > max ? `${name.slice(0, max)}…` : name
    const idSuffix = row.objectId != null ? ` · ${row.objectId}` : ''
    return `${typeZh}：${short}${idSuffix}`
  }
  if (row.objectId != null) return `${typeZh} · ${row.objectId}`
  return `${typeZh} · —`
}
