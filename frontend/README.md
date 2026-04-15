# 前端说明（ai-testcase-platform / Frontend）

基于 **Vue 3**（`<script setup>`）、**TypeScript**、**Vite**、**Element Plus**、**Vue Router**、**Pinia** 的单页应用（SPA），通过 REST 调用后端 `http://localhost:8080/api/v1`（见 `src/api/api.ts`）。

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 框架 | Vue 3 + TypeScript |
| 构建 | Vite 8 |
| UI | Element Plus + `@element-plus/icons-vue` |
| 路由 | Vue Router 4（history 模式） |
| 状态 | 已注册 Pinia（当前业务以页面内 `ref` + `localStorage` 为主） |
| HTTP | Axios：`axios.create` + 请求/响应拦截器 |

---

## 目录结构（约定）

```
src/
├── main.ts                 # 挂载 Pinia、Router、Element Plus
├── App.vue                 # 根布局：侧栏菜单、顶栏、面包屑、表格密度 provide
├── style.css               # 全局样式
├── router/index.ts         # 路由表 + 登录守卫
├── api/api.ts              # 统一类型、请求封装、各业务 API 方法（体量较大）
├── views/                  # 页面级组件（按菜单/功能划分）
├── utils/                  # 展示与工具：状态词典、用例状态、操作日志文案、日期/字节等
└── components/             # 可复用组件（当前以页面内为主，组件较少）
```

---

## 路由与鉴权

- **`meta.requiresAuth`**：需登录；未登录跳转 `/login?redirect=原路径`。
- **`meta.public`**：登录页；已登录访问 `/login` 会重定向到 `redirect` 或看板。
- **Token**：存 `localStorage` 的 `token`；登录后一般还有 `userInfo`（JSON 字符串）供顶栏展示。
- **Axios**：请求头自动带 `Authorization: Bearer <token>`；**401** 时清空本地凭证并整页跳登录页（`window.location.href`）。

---

## 接口层（`api/api.ts`）

- **baseURL**：`http://localhost:8080/api/v1`（部署环境需改为可配置或环境变量）。
- **响应约定**：后端统一 `ApiResponse<T>`（`code === 0` 为成功），`request()` 解包后只返回 `data`。
- **类型**：`Project`、`Version`、`GenerationTask`、`UiNlTask` 等与后端 DTO 对齐；状态字段使用 **英文枚举** 的 TypeScript union 类型，与后端一致。
- **缓存**：项目列表存在短时内存缓存（默认查询场景），用于减少重复请求。

---

## 业务与 UI 约定

### 状态展示（英文枚举 → 中文 + Tag）

- **`utils/statusDictionary.ts`**：各域状态字典（如生成任务、导出、用例评审/执行、UI-NL 规划/执行等），提供 `statusLabel`、`statusTagType`。
- **`utils/caseStatusDisplay.ts`**：功能/API 用例列表与详情上的状态标签，内部复用上述词典。
- **原则**：接口与库表存英文枚举；**界面只展示中文**，避免各处硬编码不一致。

### 操作日志

- **`utils/operationLogDisplay.ts`**：对象类型、动作、快照解析后的名称等，供「操作日志」列表与 tooltip 使用。

### 布局与体验

- **`App.vue`**：`provide('tableDensity')` 供子页面表格 `size` 统一；侧栏折叠、面包屑来自 `route.meta.title`。
- **列表页**：常见模式为筛选区 + `el-table` + `el-pagination`（含 `jumper` 等）。
- **详情页**：多通过路由 query（如 `id`、`taskId`）拉取详情。

---

## 页面与路由对应关系

| 路径 | 功能 |
|------|------|
| `/dashboard` | 看板 KPI、近期入口 |
| `/projects` | 项目管理 |
| `/versions` | 版本管理 |
| `/assets`、`/assets/detail` | 用例需求库、资产详情 |
| `/generation-tasks` | 用例生成任务中心 |
| `/test-cases`、`/test-cases/detail` | 功能测试用例 |
| `/api-test-cases`、`/api-test-cases/detail` | 接口测试用例 |
| `/model-configs` | 模型配置 |
| `/prompt-templates` | Prompt 模板 |
| `/exports` | 导出中心 |
| `/ui-nl-cases` | UI 自然语言用例库 |
| `/ui-nl-tasks` | UI 自然语言任务中心 |
| `/ui-nl-steps`、`/ui-nl-steps/detail` | UI 步骤管理、任务步骤详情 |
| `/ui-nl-reports` | UI 测试报告 |
| `/operation-logs` | 操作日志 |
| `/login` | 登录 |

---

## 与后端的协作

- **CORS**：后端对 `http://localhost:5173` 放行（开发时 Vite 默认端口 5173）。
- **错误信息**：业务错误通常由后端 `message` 抛出，前端 `ElMessage` 等展示。
- **数据库/枚举变更**：需同步 `api.ts` 类型与 `statusDictionary.ts` 文案。

---

## 本地开发

```bash
cd frontend
npm install
npm run dev
```

默认开发地址一般为 **http://localhost:5173**。

## 构建

```bash
npm run build
```

产物在 `dist/`，可由任意静态服务器或网关托管；生产环境请将 **API 基地址** 改为实际后端地址（建议通过环境变量或 `import.meta.env` 注入，避免写死 localhost）。

---

## 扩展建议

- 将 `api.ts` 的 `baseURL` 抽到 `VITE_API_BASE`（`.env`、`.env.production`）。
- 若全局状态增多，可引入 Pinia store（登录态、用户信息、字典缓存等）。
