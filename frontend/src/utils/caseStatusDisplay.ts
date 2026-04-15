import {
  CASE_EXECUTION_STATUS,
  CASE_REVIEW_STATUS,
  statusLabel,
  statusTagType,
  type UiTagType,
} from './statusDictionary'

/** 向后兼容：页面仍沿用 CaseStatusTagType 命名。 */
export type CaseStatusTagType = UiTagType

export function executionStatusLabel(s: string | undefined) {
  return statusLabel(CASE_EXECUTION_STATUS, s, '-')
}

export function executionStatusTagType(s: string | undefined): CaseStatusTagType {
  return statusTagType(CASE_EXECUTION_STATUS, s, 'info')
}

export function reviewStatusLabel(s: string | undefined) {
  return statusLabel(CASE_REVIEW_STATUS, s, '-')
}

export function reviewStatusTagType(s: string | undefined): CaseStatusTagType {
  return statusTagType(CASE_REVIEW_STATUS, s, 'info')
}
