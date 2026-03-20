/** Element Plus el-tag 的 type，P0 最高优先级用最醒目颜色 */
export type PriorityElTagType = 'danger' | 'warning' | 'primary' | 'info'

export function priorityTagType(p: string | undefined): PriorityElTagType {
  const v = (p || '').trim().toUpperCase()
  switch (v) {
    case 'P0':
      return 'danger'
    case 'P1':
      return 'warning'
    case 'P2':
      return 'primary'
    case 'P3':
      return 'info'
    default:
      return 'info'
  }
}
