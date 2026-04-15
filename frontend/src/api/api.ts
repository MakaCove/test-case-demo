import axios from 'axios'

export type ApiResponse<T> = {
  code: number
  message: string
  data: T
  requestId: string
}

export type Project = {
  id: number
  name: string
  code: string
  description?: string
  owner: string
  archived: boolean
  createdAt?: string
  updatedAt?: string
}

// 统一状态枚举：数据库/接口英文枚举，展示层在 statusDictionary 中转中文。
export type SwitchStatus = 'ENABLED' | 'DISABLED'
export type VersionStatus = 'DRAFT' | 'PUBLISHED'
export type CaseExecutionStatus = 'NOT_EXECUTED' | 'EXECUTED' | 'FAILED'
export type CaseReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type GenerationTaskStatus = 'PENDING' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type ExportStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'
export type UiNlTaskPlanStatus = 'PENDING' | 'QUEUED' | 'PLANNING' | 'READY' | 'FAILED' | 'INTERRUPTED' | 'CANCELLED'
export type UiNlTaskExecStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type UiNlStepStatus = 'GENERATED' | 'EDITED' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'
export type UiNlReportStatus = 'SUCCESS' | 'FAILED' | 'CANCELLED'

export type Version = {
  id: number
  projectId: number
  versionNo: string
  name?: string
  description?: string
  status: VersionStatus | string
  deleted: boolean
  createdAt?: string
  updatedAt?: string
}

export type Asset = {
  id: number
  projectId: number
  versionId: number
  assetCode: string
  relationCode: string
  assetType: 'TEXT' | 'FILE' | 'PROTOTYPE'
  title: string
  content?: string
  filePath?: string
  fileName?: string
  fileSize?: number
  mimeType?: string
  source?: string
  createdAt?: string
  updatedAt?: string
  /** 列表/详情展示 */
  projectName?: string | null
  projectCode?: string | null
  versionName?: string | null
  versionNo?: string | null
}

export type PagedResult<T> = {
  records: T[]
  pageNo: number
  pageSize: number
  total: number
}

export type GenerationTask = {
  id: number
  projectId: number
  versionId: number
  taskNo: string
  status: GenerationTaskStatus
  /** 当为 true 时，系统正在自动推进队列（手动启动/重试之后） */
  queueAutoEnabled?: boolean
  queueNo?: number
  submittedBy: number
  createdAt?: string
  updatedAt?: string
  submittedAt?: string
  startedAt?: string
  finishedAt?: string
  interruptBy?: number
  interruptReason?: string
  errorMessage?: string
  modelConfigId: number
  promptTemplateId: number
  /** FUNCTIONAL | API */
  caseCategory?: string
  /** 目标版本下的需求资产（与生成上下文一致）：编码 + 标题对应关系 */
  requirementAssets?: Array<{ assetCode: string; title?: string | null; assetType?: string | null }>
}

export type UiNlCase = {
  id: number
  caseNo: string
  projectId: number
  versionId: number
  title: string
  nlText: string
  precondition?: string
  targetEnv?: string
  baseUrl?: string
  credentialRef?: string
  status: SwitchStatus | string
  tagsJson?: string
  createdAt?: string
  updatedAt?: string
}

export type UiNlTask = {
  id: number
  projectId: number
  versionId: number
  uiNlCaseId: number
  taskNo: string
  status: UiNlTaskPlanStatus | string
  /** 最近浏览器执行：RUNNING/COMPLETED/FAILED/CANCELLED；与 status（步骤生成）分离 */
  lastExecStatus?: UiNlTaskExecStatus | string | null
  submittedBy: number
  submittedAt?: string
  /** 步骤生成（LLM规划） */
  planStartedAt?: string
  planFinishedAt?: string
  /** 最近一轮浏览器执行 */
  execStartedAt?: string
  execFinishedAt?: string
  runnerRunId?: string
  modelConfigId?: number
  promptTemplateId?: number
  headless?: boolean
  browserName?: string
  modelKey?: string
  timeoutSeconds?: number
  resultSummary?: string
  interruptReason?: string
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
}

export type UiNlStep = {
  id: number
  taskId: number
  stepNo: number
  stepTitle?: string
  actionType: string
  targetJson?: string
  inputValue?: string
  expectJson?: string
  status: UiNlStepStatus | string
  durationMs?: number
  errorMessage?: string
  screenshotPath?: string
  startedAt?: string
  finishedAt?: string
  rawLog?: string
  phase?: 'PLAN' | 'EXEC' | string
}

export type UiNlReport = {
  id: number
  reportNo: string
  taskId: number
  projectId: number
  versionId: number
  status: UiNlReportStatus | string
  totalSteps: number
  passedSteps: number
  failedSteps: number
  summary?: string
  artifactsJson?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
  updatedAt?: string
  reportFilePath?: string
  reportGeneratedAt?: string
}

export type TestCase = {
  id: number
  caseNo: string
  projectId: number
  versionId: number
  sourceTaskId?: number
  moduleName: string
  featureName: string
  title: string
  precondition?: string
  steps: string
  testData?: string
  expectedResult: string
  priority: 'P0' | 'P1' | 'P2' | 'P3' | string
  executionStatus: CaseExecutionStatus | string
  reviewStatus: CaseReviewStatus | string
  reviewComment?: string
  lastExecutedAt?: string
  reviewedAt?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
  /** 列表/详情展示：项目名称 */
  projectName?: string | null
  /** 列表/详情展示：项目编码 */
  projectCode?: string | null
  /** 列表/详情展示：版本名称（可为空） */
  versionName?: string | null
  /** 列表/详情展示：业务版本号，如 v1.0 */
  versionNo?: string | null
  /** 列表/详情展示：该用例关联版本下的需求资产摘要 */
  requirementAssets?: Array<{ assetCode: string; title?: string | null; assetType?: string | null }>
}

export type ApiTestCase = {
  id: number
  caseNo: string
  projectId: number
  versionId: number
  sourceTaskId?: number
  moduleName: string
  featureName: string
  title: string
  requestJson: string
  expectedJson: string
  assertionsJson: string
  priority: string
  executionStatus: CaseExecutionStatus | string
  reviewStatus: CaseReviewStatus | string
  reviewComment?: string
  lastExecutedAt?: string
  reviewedAt?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
  /** 列表/详情展示：项目名称 */
  projectName?: string | null
  /** 列表/详情展示：项目编码 */
  projectCode?: string | null
  /** 列表/详情展示：版本名称（可为空） */
  versionName?: string | null
  /** 列表/详情展示：业务版本号，如 v1.0 */
  versionNo?: string | null
  /** 列表/详情展示：该用例关联版本下的需求资产摘要 */
  requirementAssets?: Array<{ assetCode: string; title?: string | null; assetType?: string | null }>
}

export type ApiTestCaseDetail = {
  testCase: ApiTestCase
}

export type TestCaseDetail = {
  testCase: TestCase
  statusLogs: Array<{
    id: number
    caseId: number
    fieldName: string
    oldValue?: string
    newValue: string
    reason?: string
    changedBy: number
    changedAt: string
  }>
  histories: Array<{
    id: number
    caseId: number
    snapshotJson: string
    changedBy: number
    changedAt: string
    changeType: string
  }>
}

export type ExportRecord = {
  id: number
  exportNo: string
  projectId: number
  versionId: number
  format: string
  scope: string
  status: ExportStatus | string
  requestJson?: string
  /** 后端解析：如「功能用例、接口用例」 */
  exportContent?: string
  filePath?: string
  fileSize?: number
  errorMessage?: string
  createdBy: number
  createdAt?: string
  updatedAt?: string
}

const http = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const PROJECT_LIST_CACHE_TTL_MS = 60_000
let projectsListCache: { expiresAt: number; data: PagedResult<Project> | null; pending: Promise<PagedResult<Project>> | null } = {
  expiresAt: 0,
  data: null,
  pending: null,
}

function isDefaultProjectsQuery(params?: {
  name?: string
  code?: string
  pageNo?: number
  pageSize?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}) {
  return (
    (params?.pageNo ?? 1) === 1 &&
    (params?.pageSize ?? 10) === 100 &&
    (params?.name ?? '') === '' &&
    (params?.code ?? '') === '' &&
    (params?.sortBy ?? 'id') === 'id' &&
    (params?.sortOrder ?? 'desc') === 'desc'
  )
}

function invalidateProjectsListCache() {
  projectsListCache = { expiresAt: 0, data: null, pending: null }
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      const currentPath = window.location.pathname + window.location.search
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`
      }
    }
    return Promise.reject(error)
  },
)

async function request<T>(method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE', url: string, data?: unknown): Promise<T> {
  try {
    const response = await http.request<ApiResponse<T>>({
      method,
      url,
      data,
    })
    const json = response.data
    if (json.code !== 0) {
      throw new Error(json.message)
    }
    return json.data
  } catch (error: any) {
    const message = error?.response?.data?.message || error?.message || 'request failed'
    throw new Error(message)
  }
}

export const api = {
  login(username: string, password: string) {
    return request<{ token: string; userInfo: { id: number; username: string; displayName: string } }>('POST', '/auth/login', {
      username,
      password,
    })
  },
  logout() {
    return request<null>('POST', '/auth/logout')
  },
  me() {
    return request<{ id: number; username: string; displayName: string }>('GET', '/auth/me')
  },
  getProjects(params?: { name?: string; code?: string; pageNo?: number; pageSize?: number; sortBy?: string; sortOrder?: 'asc' | 'desc' }) {
    const useCache = isDefaultProjectsQuery(params)
    if (useCache && projectsListCache.data && Date.now() < projectsListCache.expiresAt) {
      return Promise.resolve(projectsListCache.data)
    }
    if (useCache && projectsListCache.pending) {
      return projectsListCache.pending
    }
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 10),
      sortBy: params?.sortBy ?? 'id',
      sortOrder: params?.sortOrder ?? 'desc',
    })
    const nameTrim = params?.name?.trim()
    const codeTrim = params?.code?.trim()
    if (nameTrim) q.set('name', nameTrim)
    if (codeTrim) q.set('code', codeTrim)
    const req = request<PagedResult<Project>>('GET', `/projects?${q.toString()}`)
    if (!useCache) {
      return req
    }
    projectsListCache.pending = req
    return req
      .then((data) => {
        projectsListCache = { expiresAt: Date.now() + PROJECT_LIST_CACHE_TTL_MS, data, pending: null }
        return data
      })
      .catch((error) => {
        projectsListCache.pending = null
        throw error
      })
  },
  createProject(payload: { name: string; code: string; description?: string; owner?: string }) {
    return request<Project>('POST', '/projects', payload).then((data) => {
      invalidateProjectsListCache()
      return data
    })
  },
  updateProject(projectId: number, payload: { name: string; description?: string; owner?: string }) {
    return request<Project>('PUT', `/projects/${projectId}`, payload).then((data) => {
      invalidateProjectsListCache()
      return data
    })
  },
  deleteProject(projectId: number) {
    return request<null>('DELETE', `/projects/${projectId}`).then((data) => {
      invalidateProjectsListCache()
      return data
    })
  },
  batchDeleteProjects(ids: number[]) {
    return request<null>('POST', '/projects/batch-update', { ids, action: 'ARCHIVE' }).then((data) => {
      invalidateProjectsListCache()
      return data
    })
  },
  getVersions(
    projectId: number,
    params?: { keyword?: string; status?: string; pageNo?: number; pageSize?: number; sortBy?: string; sortOrder?: 'asc' | 'desc' },
  ) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 10),
      keyword: params?.keyword ?? '',
      status: params?.status ?? '',
      sortBy: params?.sortBy ?? 'id',
      sortOrder: params?.sortOrder ?? 'desc',
    })
    return request<PagedResult<Version>>('GET', `/projects/${projectId}/versions?${q.toString()}`)
  },
  getAllVersions(params?: {
    projectId?: number
    keyword?: string
    status?: string
    pageNo?: number
    pageSize?: number
    sortBy?: string
    sortOrder?: 'asc' | 'desc'
  }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 10),
      keyword: params?.keyword ?? '',
      status: params?.status ?? '',
      sortBy: params?.sortBy ?? 'id',
      sortOrder: params?.sortOrder ?? 'desc',
    })
    if (params?.projectId) {
      q.set('projectId', String(params.projectId))
    }
    return request<PagedResult<Version>>('GET', `/versions?${q.toString()}`)
  },
  createVersion(projectId: number, payload: { versionNo: string; name?: string; description?: string }) {
    return request<Version>('POST', `/projects/${projectId}/versions`, payload)
  },
  updateVersion(versionId: number, payload: { versionNo: string; name?: string; description?: string; status: string }) {
    return request<Version>('PUT', `/versions/${versionId}`, payload)
  },
  deleteVersion(versionId: number) {
    return request<null>('DELETE', `/versions/${versionId}`)
  },
  createTextAsset(versionId: number, payload: { title: string; content: string; relationCode?: string }) {
    return request<Asset>('POST', `/versions/${versionId}/requirements/text`, payload)
  },
  async uploadRequirementFile(versionId: number, file: File, relationCode?: string, title?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (relationCode) {
      formData.append('relationCode', relationCode)
    }
    if (title != null && String(title).trim()) {
      formData.append('title', String(title).trim())
    }
    const response = await http.post<ApiResponse<Asset>>(`/versions/${versionId}/requirements/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (response.data.code !== 0) {
      throw new Error(response.data.message)
    }
    return response.data.data
  },
  async uploadPrototypeFile(versionId: number, file: File, relationCode?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (relationCode) {
      formData.append('relationCode', relationCode)
    }
    const response = await http.post<ApiResponse<Asset>>(`/versions/${versionId}/prototypes/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (response.data.code !== 0) {
      throw new Error(response.data.message)
    }
    return response.data.data
  },
  getAssets(
    versionId: number,
    params?: { pageNo?: number; pageSize?: number; assetType?: string; keyword?: string },
  ) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 10),
      assetType: params?.assetType ?? '',
      keyword: params?.keyword ?? '',
    })
    return request<PagedResult<Asset>>('GET', `/versions/${versionId}/assets?${q.toString()}`)
  },
  getAllAssets(params?: {
    pageNo?: number
    pageSize?: number
    projectId?: number
    versionId?: number
    relationCode?: string
    assetType?: string
    keyword?: string
  }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 10),
      assetType: params?.assetType ?? '',
      keyword: params?.keyword ?? '',
    })
    if (params?.projectId) {
      q.set('projectId', String(params.projectId))
    }
    if (params?.versionId) {
      q.set('versionId', String(params.versionId))
    }
    if (params?.relationCode) {
      q.set('relationCode', params.relationCode)
    }
    return request<PagedResult<Asset>>('GET', `/assets?${q.toString()}`)
  },
  updateAsset(assetId: number, payload: { title: string; content?: string }) {
    return request<Asset>('PUT', `/assets/${assetId}`, payload)
  },
  getAsset(assetId: number) {
    return request<Asset>('GET', `/assets/${assetId}`)
  },
  deleteAsset(assetId: number) {
    return request<null>('DELETE', `/assets/${assetId}`)
  },
  batchDeleteAssets(relationCodes: string[]) {
    return request<null>('POST', '/assets/batch-delete', { relationCodes })
  },
  submitGenerationTask(payload: {
    projectId: number
    versionId: number
    modelConfigId: number
    promptTemplateId: number
    referenceVersionIds?: number[]
    /** 选择要参与生成的需求资产批次 relation_code（可多选）；空/缺失表示使用该版本全部资产 */
    referenceAssetRelationCodes?: string[]
    strategy?: string
    caseLimit?: number
    caseCategory?: 'FUNCTIONAL' | 'API' | string
  }) {
    return request<GenerationTask>('POST', `/generation-tasks`, payload)
  },
  getGenerationTasks(params?: { projectId?: number; versionId?: number; status?: string; pageNo?: number; pageSize?: number }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
    })
    if (params?.projectId) {
      q.set('projectId', String(params.projectId))
    }
    if (params?.versionId) {
      q.set('versionId', String(params.versionId))
    }
    if (params?.status) {
      q.set('status', params.status)
    }
    return request<PagedResult<GenerationTask>>('GET', `/generation-tasks?${q.toString()}`)
  },
  getGenerationTaskDetail(taskId: number) {
    return request<{
      task: GenerationTask
      referenceVersionIds: number[]
      payloadJson?: string
      resultSummary?: string
    }>('GET', `/generation-tasks/${taskId}`)
  },
  updateGenerationTask(
    taskId: number,
    payload: {
      modelConfigId: number
      promptTemplateId: number
      referenceVersionIds?: number[]
      referenceAssetRelationCodes?: string[]
      strategy?: string
      caseLimit?: number
      caseCategory?: 'FUNCTIONAL' | 'API' | string
    },
  ) {
    return request<GenerationTask>('PUT', `/generation-tasks/${taskId}`, payload)
  },
  cancelGenerationTask(taskId: number) {
    return request<null>('POST', `/generation-tasks/${taskId}/cancel`)
  },
  interruptGenerationTask(taskId: number, reason?: string) {
    return request<null>('POST', `/generation-tasks/${taskId}/interrupt`, { reason })
  },
  startGenerationTask(taskId: number) {
    return request<null>('POST', `/generation-tasks/${taskId}/start`)
  },
  retryGenerationTask(taskId: number) {
    return request<GenerationTask>('POST', `/generation-tasks/${taskId}/retry`)
  },
  batchDeleteGenerationTasks(taskIds: number[]) {
    return request<null>('POST', '/generation-tasks/batch-delete', { taskIds })
  },
  materializeTestCasesFromTask(taskId: number, count?: number) {
    return request<number>('POST', `/test-cases/materialize-from-task/${taskId}`, { count })
  },
  getModelConfigs(params?: { name?: string; status?: string }) {
    const q = new URLSearchParams({
      name: params?.name ?? '',
      status: params?.status ?? '',
    })
    return request<Array<{
      id: number
      name: string
      provider: string
      baseUrl: string
      modelKey: string
      status: string
    }>>('GET', `/model-configs?${q.toString()}`)
  },
  createModelConfig(payload: {
    name: string
    provider: string
    baseUrl: string
    modelKey: string
    apiKeyEncrypted: string
    temperature?: number
    maxTokens?: number
  }) {
    return request<any>('POST', '/model-configs', payload)
  },
  updateModelConfig(
    id: number,
    payload: {
      name: string
      provider: string
      baseUrl: string
      modelKey: string
      apiKeyEncrypted: string
      temperature?: number
      maxTokens?: number
    },
  ) {
    return request<any>('PUT', `/model-configs/${id}`, payload)
  },
  deleteModelConfig(id: number) {
    return request<null>('DELETE', `/model-configs/${id}`)
  },
  enableModelConfig(id: number) {
    return request<null>('POST', `/model-configs/${id}/enable`)
  },
  disableModelConfig(id: number) {
    return request<null>('POST', `/model-configs/${id}/disable`)
  },
  testModelConnection(id: number, prompt?: string) {
    return request<string>('POST', `/model-configs/${id}/test-connection`, { prompt })
  },
  getPromptTemplates(params?: { name?: string; status?: string; scopeType?: string; scopeId?: number }) {
    const q = new URLSearchParams({
      name: params?.name ?? '',
      status: params?.status ?? '',
      scopeType: params?.scopeType ?? '',
      scopeId: params?.scopeId ? String(params.scopeId) : '',
    })
    return request<Array<{
      id: number
      name: string
      scopeType: string
      scopeId?: number
      versionNo: number
      status: string
    }>>('GET', `/prompt-templates?${q.toString()}`)
  },
  createPromptTemplate(payload: { name: string; scopeType?: string; scopeId?: number; content: string; remark?: string }) {
    return request<any>('POST', '/prompt-templates', payload)
  },
  updatePromptTemplate(id: number, payload: { name: string; content: string; remark?: string }) {
    return request<any>('PUT', `/prompt-templates/${id}`, payload)
  },
  deletePromptTemplate(id: number) {
    return request<null>('DELETE', `/prompt-templates/${id}`)
  },
  enablePromptTemplate(id: number) {
    return request<null>('POST', `/prompt-templates/${id}/enable`)
  },
  disablePromptTemplate(id: number) {
    return request<null>('POST', `/prompt-templates/${id}/disable`)
  },
  getOperationLogs(params?: { objectType?: string; action?: string; pageNo?: number; pageSize?: number }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
      objectType: params?.objectType ?? '',
      action: params?.action ?? '',
    })
    return request<PagedResult<{
      id: number
      objectType: string
      objectId?: number | null
      action: string
      beforeJson?: string
      afterJson?: string
      operatorId: number
      operatorName: string
      remark?: string
      createdAt: string
    }>>('GET', `/operation-logs?${q.toString()}`)
  },

  getTestCases(params?: {
    pageNo?: number
    pageSize?: number
    projectId?: number
    versionId?: number
    sourceTaskId?: number
    moduleName?: string
    featureName?: string
    priority?: string
    executionStatus?: string
    reviewStatus?: string
    keyword?: string
  }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
      moduleName: params?.moduleName ?? '',
      featureName: params?.featureName ?? '',
      priority: params?.priority ?? '',
      executionStatus: params?.executionStatus ?? '',
      reviewStatus: params?.reviewStatus ?? '',
      keyword: params?.keyword ?? '',
    })
    if (params?.projectId) q.set('projectId', String(params.projectId))
    if (params?.versionId) q.set('versionId', String(params.versionId))
    if (params?.sourceTaskId) q.set('sourceTaskId', String(params.sourceTaskId))
    return request<PagedResult<TestCase>>('GET', `/test-cases?${q.toString()}`)
  },
  createTestCase(payload: {
    projectId: number
    versionId: number
    sourceTaskId?: number
    moduleName: string
    featureName: string
    title: string
    precondition?: string
    steps: string
    testData?: string
    expectedResult: string
    priority?: string
    remark?: string
  }) {
    return request<TestCase>('POST', '/test-cases', payload)
  },
  updateTestCase(
    caseId: number,
    payload: {
      moduleName: string
      featureName: string
      title: string
      precondition?: string
      steps: string
      testData?: string
      expectedResult: string
      priority?: string
      remark?: string
    },
  ) {
    return request<TestCase>('PUT', `/test-cases/${caseId}`, payload)
  },
  deleteTestCase(caseId: number) {
    return request<null>('DELETE', `/test-cases/${caseId}`)
  },
  batchDeleteTestCases(ids: number[]) {
    return request<null>('POST', '/test-cases/batch-delete', { ids })
  },
  batchUpdateTestCases(payload: { ids: number[]; fields?: Record<string, any>; reviewComment?: string; reason?: string }) {
    return request<null>('POST', '/test-cases/batch-update', payload)
  },
  updateTestCaseStatus(caseId: number, payload: { executionStatus?: string; reviewStatus?: string; reviewComment?: string; reason?: string }) {
    return request<TestCase>('PATCH', `/test-cases/${caseId}/status`, payload)
  },
  getTestCaseDetail(caseId: number) {
    return request<TestCaseDetail>('GET', `/test-cases/${caseId}`)
  },

  getApiTestCases(params?: {
    pageNo?: number
    pageSize?: number
    projectId?: number
    versionId?: number
    sourceTaskId?: number
    moduleName?: string
    featureName?: string
    priority?: string
    executionStatus?: string
    reviewStatus?: string
    keyword?: string
  }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
      moduleName: params?.moduleName ?? '',
      featureName: params?.featureName ?? '',
      priority: params?.priority ?? '',
      executionStatus: params?.executionStatus ?? '',
      reviewStatus: params?.reviewStatus ?? '',
      keyword: params?.keyword ?? '',
    })
    if (params?.projectId) q.set('projectId', String(params.projectId))
    if (params?.versionId) q.set('versionId', String(params.versionId))
    if (params?.sourceTaskId) q.set('sourceTaskId', String(params.sourceTaskId))
    return request<PagedResult<ApiTestCase>>('GET', `/api-test-cases?${q.toString()}`)
  },
  createApiTestCase(payload: {
    projectId: number
    versionId: number
    sourceTaskId?: number
    moduleName: string
    featureName: string
    title: string
    requestJson: string
    expectedJson: string
    assertionsJson: string
    priority?: string
    remark?: string
  }) {
    return request<ApiTestCase>('POST', '/api-test-cases', payload)
  },
  updateApiTestCase(
    caseId: number,
    payload: {
      moduleName: string
      featureName: string
      title: string
      requestJson: string
      expectedJson: string
      assertionsJson: string
      priority?: string
      remark?: string
    },
  ) {
    return request<ApiTestCase>('PUT', `/api-test-cases/${caseId}`, payload)
  },
  deleteApiTestCase(caseId: number) {
    return request<null>('DELETE', `/api-test-cases/${caseId}`)
  },
  batchDeleteApiTestCases(ids: number[]) {
    return request<null>('POST', '/api-test-cases/batch-delete', { ids })
  },
  batchUpdateApiTestCases(payload: { ids: number[]; fields?: Record<string, any>; reviewComment?: string; reason?: string }) {
    return request<null>('POST', '/api-test-cases/batch-update', payload)
  },
  updateApiTestCaseStatus(caseId: number, payload: { executionStatus?: string; reviewStatus?: string; reviewComment?: string; reason?: string }) {
    return request<ApiTestCase>('PATCH', `/api-test-cases/${caseId}/status`, payload)
  },
  getApiTestCaseDetail(caseId: number) {
    return request<ApiTestCaseDetail>('GET', `/api-test-cases/${caseId}`)
  },

  getExportRecords(params?: { pageNo?: number; pageSize?: number; projectId?: number; versionId?: number; status?: string }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
      status: params?.status ?? '',
    })
    if (params?.projectId) q.set('projectId', String(params.projectId))
    if (params?.versionId) q.set('versionId', String(params.versionId))
    return request<PagedResult<ExportRecord>>('GET', `/exports?${q.toString()}`)
  },
  createExport(payload: { projectId: number; versionId: number; format: 'md'; scope: 'all' | string; requestJson?: string }) {
    return request<ExportRecord>('POST', '/exports', payload)
  },
  retryExport(exportId: number) {
    return request<ExportRecord>('POST', `/exports/${exportId}/retry`)
  },
  async downloadExport(exportId: number) {
    const response = await http.get(`/exports/${exportId}/download`, { responseType: 'blob' })
    return response.data as Blob
  },

  createUiNlCase(payload: {
    projectId: number
    versionId: number
    title: string
    nlText: string
    precondition?: string
    targetEnv?: string
    baseUrl?: string
    credentialRef?: string
    status?: string
    tagsJson?: string
  }) {
    return request<UiNlCase>('POST', '/ui-nl-cases', payload)
  },
  getUiNlCases(params?: {
    pageNo?: number
    pageSize?: number
    projectId?: number
    versionId?: number
    keyword?: string
    status?: string
  }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
      keyword: params?.keyword ?? '',
    })
    if (params?.projectId) q.set('projectId', String(params.projectId))
    if (params?.versionId) q.set('versionId', String(params.versionId))
    if (params?.status) q.set('status', String(params.status))
    return request<PagedResult<UiNlCase>>('GET', `/ui-nl-cases?${q.toString()}`)
  },
  getUiNlCase(id: number) {
    return request<UiNlCase>('GET', `/ui-nl-cases/${id}`)
  },
  updateUiNlCase(
    id: number,
    payload: {
      title: string
      nlText: string
      precondition?: string
      targetEnv?: string
      baseUrl?: string
      credentialRef?: string
      status?: string
      tagsJson?: string
    },
  ) {
    return request<UiNlCase>('PUT', `/ui-nl-cases/${id}`, payload)
  },
  deleteUiNlCase(id: number) {
    return request<null>('DELETE', `/ui-nl-cases/${id}`)
  },
  createUiNlTask(payload: {
    projectId: number
    versionId: number
    uiNlCaseId: number
    modelConfigId: number
    promptTemplateId: number
    headless?: boolean
    browserName?: string
    modelKey?: string
    timeoutSeconds?: number
  }) {
    return request<UiNlTask>('POST', '/ui-nl-tasks', payload)
  },
  getUiNlTasks(params?: {
    pageNo?: number
    pageSize?: number
    projectId?: number
    versionId?: number
    status?: string
    caseTitle?: string
    lastExecStatus?: string
  }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
      status: params?.status ?? '',
    })
    if (params?.projectId) q.set('projectId', String(params.projectId))
    if (params?.versionId) q.set('versionId', String(params.versionId))
    if (params?.caseTitle) q.set('caseTitle', String(params.caseTitle))
    if (params?.lastExecStatus) q.set('lastExecStatus', String(params.lastExecStatus))
    return request<PagedResult<UiNlTask>>('GET', `/ui-nl-tasks?${q.toString()}`)
  },
  getUiNlTask(id: number) {
    return request<UiNlTask>('GET', `/ui-nl-tasks/${id}`)
  },
  updateUiNlTask(
    id: number,
    payload: {
      uiNlCaseId: number
      modelConfigId: number
      promptTemplateId: number
      headless?: boolean
      browserName?: string
      modelKey?: string
      timeoutSeconds?: number
    },
  ) {
    return request<UiNlTask>('PUT', `/ui-nl-tasks/${id}`, payload)
  },
  deleteUiNlTask(id: number) {
    return request<null>('DELETE', `/ui-nl-tasks/${id}`)
  },
  executeUiNlTask(id: number) {
    return request<UiNlTask>('POST', `/ui-nl-tasks/${id}/execute`)
  },
  interruptUiNlTask(id: number, reason?: string) {
    return request<UiNlTask>('POST', `/ui-nl-tasks/${id}/interrupt`, { reason })
  },
  cancelUiNlTask(id: number, reason?: string) {
    return request<null>('POST', `/ui-nl-tasks/${id}/cancel`, { reason })
  },
  runUiNlTask(id: number) {
    return request<UiNlTask>('POST', `/ui-nl-tasks/${id}/run`)
  },
  getUiNlTaskSteps(taskId: number, phase?: 'PLAN' | 'EXEC') {
    const q = new URLSearchParams()
    if (phase) q.set('phase', phase)
    const suffix = q.toString() ? `?${q.toString()}` : ''
    return request<UiNlStep[]>('GET', `/ui-nl-tasks/${taskId}/steps${suffix}`)
  },
  getUiNlStep(stepId: number, phase?: 'PLAN' | 'EXEC') {
    const q = new URLSearchParams()
    if (phase) q.set('phase', phase)
    const suffix = q.toString() ? `?${q.toString()}` : ''
    return request<UiNlStep>('GET', `/ui-nl-steps/${stepId}${suffix}`)
  },
  updateUiNlPlanStep(
    stepId: number,
    payload: {
      stepTitle: string
      actionType?: string
      inputValue: string
      expectJson?: string
    },
  ) {
    return request<UiNlStep>('PUT', `/ui-nl-plan-steps/${stepId}`, payload)
  },
  async getUiNlExecStepScreenshotBlob(stepId: number) {
    const response = await http.get(`/ui-nl-exec-steps/${stepId}/screenshot`, { responseType: 'blob' })
    return response.data as Blob
  },
  getUiNlReports(params?: {
    pageNo?: number
    pageSize?: number
    projectId?: number
    versionId?: number
    status?: string
  }) {
    const q = new URLSearchParams({
      pageNo: String(params?.pageNo ?? 1),
      pageSize: String(params?.pageSize ?? 20),
      status: params?.status ?? '',
    })
    if (params?.projectId) q.set('projectId', String(params.projectId))
    if (params?.versionId) q.set('versionId', String(params.versionId))
    return request<PagedResult<UiNlReport>>('GET', `/ui-nl-reports?${q.toString()}`)
  },
  getUiNlReport(id: number) {
    return request<UiNlReport>('GET', `/ui-nl-reports/${id}`)
  },
  async downloadUiNlReportHtml(id: number) {
    const response = await http.get(`/ui-nl-reports/${id}/html`, { responseType: 'blob' })
    return response.data as Blob
  },
}
