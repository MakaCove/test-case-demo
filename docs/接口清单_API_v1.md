# HTTP API 清单（v1）

- **Base URL**：`/api/v1`（与 `server.servlet.context-path` 组合，默认服务 `http://localhost:8080`）
- **统一响应**：`ApiResponse<T>`（含 `code`、`message`、`data`、`requestId` 等，以前端 `api.ts` 类型为准）
- **鉴权**：除登录等公开接口外，请求头携带登录后下发的 Token（实现以 `AuthController` / 拦截器为准）

---

## 认证 `/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录 |
| POST | `/auth/logout` | 登出 |
| GET | `/auth/me` | 当前用户 |

---

## 项目 `/projects`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/projects` | 创建项目 |
| GET | `/projects` | 分页列表（支持 `name`、`code`、`sortBy`、`sortOrder`） |
| GET | `/projects/{projectId}` | 详情 |
| PUT | `/projects/{projectId}` | 更新 |
| DELETE | `/projects/{projectId}` | 删除（逻辑删除） |
| POST | `/projects/batch-update` | 批量操作（如归档） |

---

## 版本 `/projects/.../versions` 与 `/versions`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/projects/{projectId}/versions` | 创建版本 |
| GET | `/projects/{projectId}/versions` | 某项目下版本分页 |
| GET | `/versions` | 全局版本分页（筛选项目等） |
| GET | `/versions/{versionId}` | 版本详情 |
| PUT | `/versions/{versionId}` | 更新版本 |
| DELETE | `/versions/{versionId}` | 删除版本 |
| POST | `/versions/{versionId}/publish` | 发布版本 |
| GET | `/versions/compare` | 版本对比（MVP 占位，`leftVersionId`、`rightVersionId`） |

---

## 需求资产（前缀 `/api/v1`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/versions/{versionId}/requirements/text` | 创建文本类资产 |
| POST | `/versions/{versionId}/requirements/files` | 上传需求文档（`multipart/form-data`） |
| POST | `/versions/{versionId}/prototypes/files` | 上传原型（`multipart/form-data`） |
| GET | `/versions/{versionId}/assets` | 某版本下资产分页 |
| GET | `/assets` | 全量/条件资产分页（`projectId`、`versionId`、`relationCode`、`assetType`、`keyword`） |
| GET | `/assets/{assetId}` | 资产详情 |
| PUT | `/assets/{assetId}` | 更新资产 |
| DELETE | `/assets/{assetId}` | 删除单条资产 |
| POST | `/assets/batch-delete` | 按 `relationCodes` 批量删除 |

列表与单条资产返回体除 `projectId`、`versionId` 外，附带 **`projectName`、`projectCode`、`versionName`、`versionNo`**，由服务端关联项目表与版本表填充，便于列表/详情直接展示。

---

## 模型配置 `/model-configs`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/model-configs` | 列表（`name`、`status`） |
| POST | `/model-configs` | 创建 |
| PUT | `/model-configs/{id}` | 更新 |
| DELETE | `/model-configs/{id}` | 删除 |
| POST | `/model-configs/{id}/enable` | 启用 |
| POST | `/model-configs/{id}/disable` | 禁用 |
| POST | `/model-configs/{id}/test-connection` | 连通性测试 |

---

## Prompt 模板 `/prompt-templates`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/prompt-templates` | 列表 |
| POST | `/prompt-templates` | 创建 |
| PUT | `/prompt-templates/{id}` | 更新 |
| DELETE | `/prompt-templates/{id}` | 删除 |
| POST | `/prompt-templates/{id}/enable` | 启用 |
| POST | `/prompt-templates/{id}/disable` | 禁用 |

---

## 生成任务 `/generation-tasks`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/generation-tasks` | 提交任务 |
| GET | `/generation-tasks` | 分页列表（`projectId`、`versionId`、`status`） |
| GET | `/generation-tasks/{taskId}` | 详情 |
| PUT | `/generation-tasks/{taskId}` | 更新任务配置 |
| POST | `/generation-tasks/{taskId}/cancel` | 取消 |
| POST | `/generation-tasks/{taskId}/interrupt` | 中断 |
| POST | `/generation-tasks/{taskId}/retry` | 重试 |
| POST | `/generation-tasks/{taskId}/start` | 启动 |
| POST | `/generation-tasks/batch-delete` | 批量删除（body：`taskIds`） |

列表项与详情中的 `task` 均含 **`requirementAssets`**：该任务 **目标版本**（`versionId`）下未删除的需求资产摘要，每项为 `assetCode`、`title`（无标题时用 `fileName`）、`assetType`，与执行生成时拼入上下文的资产集合一致。

---

## UI 自然语言用例库 `/ui-nl-cases`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ui-nl-cases` | 创建自然语言用例 |
| GET | `/ui-nl-cases` | 分页列表（`projectId`、`versionId`、`keyword`） |
| GET | `/ui-nl-cases/{id}` | 详情 |
| PUT | `/ui-nl-cases/{id}` | 更新 |
| DELETE | `/ui-nl-cases/{id}` | 删除（逻辑删除） |

---

## UI 自然语言任务中心 `/ui-nl-tasks`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ui-nl-tasks` | 创建任务（绑定自然语言用例） |
| GET | `/ui-nl-tasks` | 分页列表（`projectId`、`versionId`、`status`） |
| GET | `/ui-nl-tasks/{id}` | 任务详情 |
| POST | `/ui-nl-tasks/{id}/start` | 启动（`PENDING/FAILED/CANCELLED -> QUEUED`） |
| POST | `/ui-nl-tasks/{id}/execute` | 执行（调用 runner） |
| POST | `/ui-nl-tasks/{id}/cancel` | 取消 |
| POST | `/ui-nl-tasks/{id}/retry` | 重试（重置为 `PENDING`） |
| GET | `/ui-nl-tasks/{id}/steps` | 获取任务步骤列表 |

任务状态建议：`PENDING/QUEUED/PLANNING/READY/RUNNING/COMPLETED/FAILED/CANCELLED`。

---

## UI 步骤与报告

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/ui-nl-steps/{stepId}` | 步骤详情 |
| GET | `/ui-nl-reports` | 报告分页列表（`projectId`、`versionId`、`status`） |
| GET | `/ui-nl-reports/{id}` | 报告详情 |

---

## 功能测试用例 `/test-cases`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/test-cases` | 分页检索（项目、版本、模块、功能、优先级、执行/评审状态、`keyword` 标题等） |
| POST | `/test-cases` | 创建 |
| GET | `/test-cases/{caseId}` | 详情（含状态日志、历史） |
| PUT | `/test-cases/{caseId}` | 更新 |
| DELETE | `/test-cases/{caseId}` | 删除 |
| POST | `/test-cases/batch-delete` | 批量删除 |
| POST | `/test-cases/batch-update` | 批量更新字段 |
| PATCH | `/test-cases/{caseId}/status` | 单条执行/评审状态更新 |
| POST | `/test-cases/materialize-from-task/{taskId}` | 从生成任务物化用例 |

---

## 接口测试用例 `/api-test-cases`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api-test-cases` | 分页检索 |
| POST | `/api-test-cases` | 创建 |
| GET | `/api-test-cases/{caseId}` | 详情 |
| PUT | `/api-test-cases/{caseId}` | 更新 |
| DELETE | `/api-test-cases/{caseId}` | 删除 |
| POST | `/api-test-cases/batch-delete` | 批量删除 |
| POST | `/api-test-cases/batch-update` | 批量更新 |
| PATCH | `/api-test-cases/{caseId}/status` | 单条状态更新 |

---

## 导出 `/exports`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/exports` | 导出记录分页 |
| POST | `/exports` | 创建导出任务 |
| GET | `/exports/{id}` | 详情 |
| POST | `/exports/{id}/retry` | 失败重试 |
| GET | `/exports/{id}/download` | 下载文件（二进制流） |

---

## 操作日志 `/operation-logs`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/operation-logs` | 分页查询 |

---

## 备注

- 查询参数、请求体字段以各 `Controller` 的 DTO / `@RequestParam` 为准；本文仅列路径与语义。
- 单条删除生成任务前端调用 **`POST /generation-tasks/batch-delete`** 传单个 `taskId`，与后端现有一致。
