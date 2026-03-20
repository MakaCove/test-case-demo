/** Element Plus el-tag type */
export type CaseStatusTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export function executionStatusLabel(s: string | undefined) {
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

export function executionStatusTagType(s: string | undefined): CaseStatusTagType {
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

export function reviewStatusLabel(s: string | undefined) {
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

export function reviewStatusTagType(s: string | undefined): CaseStatusTagType {
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
