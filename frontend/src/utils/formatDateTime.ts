function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function formatLocal(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

/**
 * 将接口返回的日期时间格式化为本地 `YYYY-MM-DD HH:mm:ss`；空值返回 "-"。
 */
export function formatDateTime(v: string | number | Date | null | undefined): string {
  if (v == null || v === '') return '-'
  if (v instanceof Date) {
    return Number.isNaN(v.getTime()) ? '-' : formatLocal(v)
  }
  if (typeof v === 'number' && Number.isFinite(v)) {
    const d = new Date(v)
    return Number.isNaN(d.getTime()) ? '-' : formatLocal(d)
  }
  const s = String(v).trim()
  if (!s) return '-'
  const d = new Date(s)
  if (!Number.isNaN(d.getTime())) {
    return formatLocal(d)
  }
  // 无法解析时：常见 ISO / LocalDateTime 字符串直接压成可读形态
  if (s.length >= 19 && (s.includes('T') || s.includes(' '))) {
    return s.slice(0, 19).replace('T', ' ')
  }
  return s
}
