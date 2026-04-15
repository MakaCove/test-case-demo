# HTTP API 参考（v1）

- **Base URL**：`http://<host>:8080/api/v1`（无前缀 path 时即此）
- **统一响应**：`{ code, message, data, requestId }`；**`code === 0`** 表示成功，`data` 为业务体（见前端 `api.ts` 的 `ApiResponse<T>`）
- **鉴权**：除登录、登出外，请求头需 **`Authorization: Bearer <token>`**

查询参数、请求体以各 Controller / DTO 为准；下表为路径与语义速查。

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
| POST | `/projects` | 创建 |
| GET | `/projects` | 分页（`name`、`code`、`sortBy`、`sortOrder` 等） |
| GET | `/projects/{projectId}` | 详情 |
| PUT | `/projects/{projectId}` | 更新 |
| DELETE | `/projects/{projectId}` | 删除（逻辑删除） |
| POST | `/projects/batch-update` | 批量更新 |

---

## 版本

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/projects/{projectId}/versions` | 创建版本 |
| GET | `/projects/{projectId}/versions` | 某项目下版本分页 |
| GET | `/versions` | 全局版本分页（筛选项目等） |
| GET | `/versions/{versionId}` | 详情 |
| PUT | `/versions/{versionId}` | 更新 |
| DELETE | `/versions/{versionId}` | 删除 |
| POST | `/versions/{versionId}/publish` | 发布 |
| GET | `/versions/compare` | 版本对比（占位，参数 `leftVersionId`、`rightVersionId`） |

---

## 需求资产 `AssetController`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/versions/{versionId}/requirements/text` | 创建文本资产 |
| POST | `/versions/{versionId}/requirements/files` | 上传需求文档（`multipart`） |
| POST | `/versions/{versionId}/prototypes/files` | 上传原型（`multipart`） |
| GET | `/versions/{versionId}/assets` | 某版本下资产分页 |
| GET | `/assets` | 资产分页（`projectId`、`versionId`、`relationCode`、`assetType`、`keyword` 等） |
| GET | `/assets/{assetId}` | 详情 |
| PUT | `/assets/{assetId}` | 更新 |
| DELETE | `/assets/{assetId}` | 删除 |
| POST | `/assets/batch-delete` | 按 `relationCodes` 批量删除 |

---

## 模型配置 `/model-configs`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/model-configs` | 列表 |
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
| GET | `/generation-tasks` | 分页（`projectId`、`versionId`、`status`） |
| GET | `/generation-tasks/{taskId}` | 详情 |
| PUT | `/generation-tasks/{taskId}` | 更新配置 |
| POST | `/generation-tasks/{taskId}/cancel` | 取消 |
| POST | `/generation-tasks/{taskId}/interrupt` | 中断（body 可选 `reason`） |
| POST | `/generation-tasks/{taskId}/retry` | 重试 |
| POST | `/generation-tasks/{taskId}/start` | 启动队列推进 |
| POST | `/generation-tasks/batch-delete` | 批量删除（`taskIds`） |

详情/列表中的任务含 **`requirementAssets`**（目标版本下需求资产摘要），与生成上下文一致。

---

## UI 自然语言

### 用例 `/ui-nl-cases`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ui-nl-cases` | 创建 |
| GET | `/ui-nl-cases` | 分页（`projectId`、`versionId`、`keyword`、`status`） |
| GET | `/ui-nl-cases/{id}` | 详情 |
| PUT | `/ui-nl-cases/{id}` | 更新 |
| DELETE | `/ui-nl-cases/{id}` | 删除 |

### 任务 `/ui-nl-tasks`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ui-nl-tasks` | 创建任务 |
| GET | `/ui-nl-tasks` | 分页（`projectId`、`versionId`、`status` 步骤生成、`caseTitle`、`lastExecStatus` 执行状态、`pageNo`、`pageSize`） |
| GET | `/ui-nl-tasks/{id}` | 详情 |
| PUT | `/ui-nl-tasks/{id}` | 更新 |
| DELETE | `/ui-nl-tasks/{id}` | 删除 |
| POST | `/ui-nl-tasks/{id}/execute` | **生成步骤**（入队，由后台 planner 写入规划步骤，非 runner 真执行） |
| POST | `/ui-nl-tasks/{id}/interrupt` | 中断 |
| POST | `/ui-nl-tasks/{id}/run` | **提交浏览器执行**（依赖已就绪的规划步骤） |
| POST | `/ui-nl-tasks/{id}/cancel` | 取消 |
| POST | `/ui-nl-tasks/{id}/retry` | 重试 |
| GET | `/ui-nl-tasks/{id}/steps` | 步骤列表 |

### 步骤与报告

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/ui-nl-steps/{stepId}` | 步骤详情（规划/执行由后端区分） |
| PUT | `/ui-nl-plan-steps/{stepId}` | 更新规划步骤 |
| GET | `/ui-nl-exec-steps/{stepId}/screenshot` | 执行步骤截图 |
| GET | `/ui-nl-reports` | 报告分页 |
| GET | `/ui-nl-reports/{id}` | 报告详情 |
| GET | `/ui-nl-reports/{id}/html` | HTML 报告内容 |

---

## 功能测试用例 `/test-cases`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/test-cases` | 分页检索 |
| POST | `/test-cases` | 创建 |
| GET | `/test-cases/{caseId}` | 详情 |
| PUT | `/test-cases/{caseId}` | 更新 |
| DELETE | `/test-cases/{caseId}` | 删除 |
| POST | `/test-cases/batch-delete` | 批量删除 |
| POST | `/test-cases/batch-update` | 批量更新 |
| PATCH | `/test-cases/{caseId}/status` | 执行/评审状态 |
| POST | `/test-cases/materialize-from-task/{taskId}` | 从生成任务物化 |

---

## 接口测试用例 `/api-test-cases`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api-test-cases` | 分页 |
| POST | `/api-test-cases` | 创建 |
| GET | `/api-test-cases/{caseId}` | 详情 |
| PUT | `/api-test-cases/{caseId}` | 更新 |
| DELETE | `/api-test-cases/{caseId}` | 删除 |
| POST | `/api-test-cases/batch-delete` | 批量删除 |
| POST | `/api-test-cases/batch-update` | 批量更新 |
| PATCH | `/api-test-cases/{caseId}/status` | 状态更新 |

---

## 导出 `/exports`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/exports` | 分页 |
| POST | `/exports` | 创建导出任务 |
| GET | `/exports/{id}` | 详情 |
| POST | `/exports/{id}/retry` | 失败重试 |
| GET | `/exports/{id}/download` | 下载文件 |

---

## 操作日志 `/operation-logs`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/operation-logs` | 分页查询 |

---

## 备注

- 单条删除生成任务前端可调用 **`POST /generation-tasks/batch-delete`** 传单个 `taskId`。
- 若接口行为与本文不一致，以 **`backend/**/controller`** 源码为准。
