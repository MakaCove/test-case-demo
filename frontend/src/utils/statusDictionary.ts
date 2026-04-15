export type UiTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export type StatusMeta = {
  label: string
  tag: UiTagType
}

export type StatusDict = Record<string, StatusMeta>

export function normalizeStatus(code: string | undefined | null) {
  return (code || '').trim().toUpperCase()
}

export function statusLabel(dict: StatusDict, code: string | undefined | null, fallback = '—') {
  const normalized = normalizeStatus(code)
  if (!normalized) return fallback
  return dict[normalized]?.label || normalized
}

export function statusTagType(dict: StatusDict, code: string | undefined | null, fallback: UiTagType = 'info') {
  const normalized = normalizeStatus(code)
  if (!normalized) return fallback
  return dict[normalized]?.tag || fallback
}

export const CASE_REVIEW_STATUS: StatusDict = {
  PENDING: { label: '待评审', tag: 'info' },
  APPROVED: { label: '已通过', tag: 'success' },
  REJECTED: { label: '已驳回', tag: 'danger' },
}

export const CASE_EXECUTION_STATUS: StatusDict = {
  NOT_EXECUTED: { label: '未执行', tag: 'info' },
  EXECUTED: { label: '已执行', tag: 'success' },
  FAILED: { label: '执行失败', tag: 'danger' },
}

export const VERSION_STATUS: StatusDict = {
  DRAFT: { label: '草稿', tag: 'info' },
  PUBLISHED: { label: '已发布', tag: 'success' },
}

export const SWITCH_STATUS: StatusDict = {
  ENABLED: { label: '启用', tag: 'success' },
  DISABLED: { label: '停用', tag: 'info' },
}

export const GENERATION_TASK_STATUS: StatusDict = {
  PENDING: { label: '待启动', tag: 'info' },
  QUEUED: { label: '排队中', tag: 'warning' },
  RUNNING: { label: '运行中', tag: 'primary' },
  COMPLETED: { label: '已完成', tag: 'success' },
  FAILED: { label: '失败', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'info' },
}

export const EXPORT_STATUS: StatusDict = {
  RUNNING: { label: '导出中', tag: 'warning' },
  SUCCESS: { label: '成功', tag: 'success' },
  FAILED: { label: '失败', tag: 'danger' },
}

export const UI_NL_CASE_STATUS: StatusDict = {
  ENABLED: { label: '启用', tag: 'success' },
  DISABLED: { label: '停用', tag: 'info' },
}

export const UI_NL_TASK_PLAN_STATUS: StatusDict = {
  PENDING: { label: '待启动', tag: 'info' },
  QUEUED: { label: '排队中', tag: 'warning' },
  PLANNING: { label: '规划中', tag: 'primary' },
  READY: { label: '步骤就绪', tag: 'success' },
  FAILED: { label: '规划失败', tag: 'danger' },
  INTERRUPTED: { label: '规划中断', tag: 'warning' },
  CANCELLED: { label: '已取消', tag: 'info' },
}

export const UI_NL_TASK_EXEC_STATUS: StatusDict = {
  RUNNING: { label: '执行中', tag: 'warning' },
  COMPLETED: { label: '执行成功', tag: 'success' },
  FAILED: { label: '执行失败', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'info' },
}

export const UI_NL_STEP_PLAN_STATUS: StatusDict = {
  GENERATED: { label: '已生成', tag: 'success' },
  EDITED: { label: '已编辑', tag: 'warning' },
}

export const UI_NL_STEP_EXEC_STATUS: StatusDict = {
  PENDING: { label: '待执行', tag: 'info' },
  SUCCESS: { label: '成功', tag: 'success' },
  FAILED: { label: '失败', tag: 'danger' },
  SKIPPED: { label: '跳过', tag: 'warning' },
}

export const UI_NL_REPORT_STATUS: StatusDict = {
  SUCCESS: { label: '成功', tag: 'success' },
  FAILED: { label: '失败', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'warning' },
}
