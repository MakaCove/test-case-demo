/** 展示文件体积 */
export function formatBytes(n: number | string | null | undefined): string {
  const num = typeof n === 'string' ? Number(n) : n
  if (!Number.isFinite(num as number) || (num as number) < 0) return '—'
  const v = num as number
  if (v < 1024) return `${v} B`
  if (v < 1024 * 1024) return `${(v / 1024).toFixed(1)} KB`
  return `${(v / (1024 * 1024)).toFixed(1)} MB`
}
