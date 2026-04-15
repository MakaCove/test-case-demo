# 后端说明（ai-testcase-platform / Backend）

基于 **Spring Boot 3**、**MyBatis-Plus**、**MySQL 8** 的 REST API 服务，为前端提供用例管理、需求资产、AI 生成任务、导出与 UI 自然语言自动化等能力。

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 运行时 | Java 17 |
| Web | Spring MVC，统一前缀 `/api/v1` |
| 持久化 | MyBatis-Plus，实体与表字段驼峰映射 |
| 数据库 | MySQL（库名见 `application.yml`） |
| 安全 | 自定义 Bearer Token + 拦截器（非 Spring Security 过滤器链） |
| 日志 | Log4j2（`log4j2-spring.xml`），同时输出控制台与滚动文件 |
| 定时任务 | `@Scheduled`：生成任务调度、UI-NL 规划/轮询执行等 |

---

## 目录结构（约定）

```
src/main/java/com/testcase/backend/
├── BackendApplication.java      # 启动类（含 @EnableScheduling）
├── common/                      # ApiResponse、全局异常、RequestContext、状态常量 StatusConstants 等
├── config/                      # CORS、鉴权拦截器、MyBatis、请求 ID 过滤器、密码加密 Bean 等
├── controller/                  # REST 入口，薄层：参数校验 + 调 Service + 返回 ApiResponse
├── dto/                         # 请求/响应体（Java record 为主）
├── entity/                      # 与表对应的实体
├── mapper/                      # MyBatis-Plus Mapper 接口
└── service/                     # 业务逻辑、外部调用（大模型、ui-runner、文件存储）
```

---

## 统一响应与错误

- **成功**：`ApiResponse.success(data)` → `code = 0`，`message = "success"`，`data` 为业务体。
- **失败**：`GlobalExceptionHandler` 将业务异常、校验异常、未授权等转为 `ApiResponse.fail(code, message)`。
- **请求 ID**：`RequestIdFilter` 生成/透传 `X-Request-Id`，写入 `RequestContext`，便于日志链路关联。

---

## 鉴权与安全

- 除登录、登出及 Actuator 外，`/api/**` 需带 `Authorization: Bearer <token>`。
- `AuthInterceptor` 解析 Token，校验后把 `loginUserId`、`loginUsername` 放入 `HttpServletRequest`。
- `BCryptPasswordEncoder` 用于管理员密码等加密；首次启动时可选引导管理员密码（见 `AdminPasswordBootstrapRunner`）。

---

## 状态与枚举（`StatusConstants`）

业务状态在数据库与接口中统一为 **英文大写枚举**；前端再做中文展示映射。

- **开关**：`ENABLED` / `DISABLED`
- **版本**：`DRAFT` / `PUBLISHED`
- **生成任务**：`PENDING` → `QUEUED` → `RUNNING` → `COMPLETED` / `FAILED` / `CANCELLED`
- **导出**：`RUNNING` / `SUCCESS` / `FAILED`
- **用例评审/执行**：评审 `PENDING`/`APPROVED`/`REJECTED`；执行 `NOT_EXECUTED`/`EXECUTED`/`FAILED`
- **UI-NL 任务**：规划侧 `PENDING`/`QUEUED`/`PLANNING`/`READY`/…；最近执行侧 `last_exec_status`：`RUNNING`/`COMPLETED`/`FAILED`/`CANCELLED` 等

具体取值以 `StatusConstants.java` 与数据库表注释为准。

---

## 核心模块与职责

### 项目与版本

- **ProjectController / VersionController**：项目 CRUD、版本 CRUD、版本发布/状态切换；与用例、资产、任务通过 `projectId` / `versionId` 关联。

### 需求资产（需求/原型等）

- **AssetController**：文本资产、文件上传；存储路径受 `app.storage.*` 约束。
- 与 **生成任务**、**用例** 通过资产 ID 或版本关联。

### 功能用例 / API 用例

- **TestCaseController / ApiTestCaseController**：列表筛选、详情、增删改、状态流转、批量操作。
- **TestCaseService / ApiTestCaseService**：持久化、状态日志、与生成任务落库用例的衔接。
- **ApiTestCaseParser**（若存在）：解析 API 相关结构。

### 生成任务（LLM 生成用例）

- **GenerationTaskController / GenerationTaskService**：提交任务、中断、查询、引用需求资产与 Prompt/模型配置。
- **GenerationTaskRunner**（`@Scheduled`）：消费 `RUNNING` 任务，调用 **ModelClient**（OpenAI 兼容 HTTP），把结果写入功能用例或 API 用例；支持从任务 payload 中解析参考版本等上下文。
- **ModelConfigController / PromptTemplateController**：模型连接与 Prompt 模板维护（启用/停用等）。

### 导出

- **ExportController / ExportService**：异步导出记录，状态机与文件落盘；`ExportStatus` 见 `StatusConstants.Export`。

### UI 自然语言（UI-NL）

- **UiNlController / UiNlService**：自然语言用例、任务（规划步骤、执行浏览器）、规划步骤与执行步骤、报告、HTML 报告等。
- **UiNlTaskRunner**（`@Scheduled`）：  
  - 约每 2s：对 `QUEUED` 任务做 **规划**（大模型生成步骤）；  
  - 约每 2s：对 **执行中** 任务 **轮询 ui-runner**，更新 `last_exec_status`、执行轨迹等。
- **UiRunnerClient**：调用独立服务 `app.ui-runner.base-url`（如 FastAPI），需配置 `token` 与超时。
- **UiNlHtmlReportService**：生成/读取 HTML 报告文件。

### 操作日志

- **OperationLogController / OperationLogService**：记录关键业务对象的创建/变更等，供前端「操作日志」列表查询。

### 认证

- **AuthController / AuthService**：登录颁发 Token、登出、当前用户信息。

---

## 外部依赖与配置要点（`application.yml`）

| 配置项 | 含义 |
|--------|------|
| `spring.datasource.*` | MySQL 连接 |
| `logging.file.path` | 文件日志目录（**相对 JVM 工作目录**；在仓库根目录启动时常为 `backend/logs`） |
| `app.auth.bootstrap-admin-password` | 引导管理员密码（首次等场景） |
| `app.storage.base-path` / `prototype-base-path` | 上传与原型图根目录 |
| `app.model.request-timeout-seconds` | 大模型单次请求超时 |
| `app.ui-runner.*` | UI 自动化执行服务地址与鉴权 |

---

## 日志

- 配置：`src/main/resources/log4j2-spring.xml`，应用名 `ai-testcase-backend`。
- 日志文件路径由 `logging.file.path` 与 Log4j2 的 `SpringProperty` 对齐；**工作目录不同会导致日志落在仓库根 `logs/` 或 `backend/logs/`**，属正常现象。

---

## 构建与运行

```bash
cd backend
mvn -DskipTests package
mvn spring-boot:run
```

默认端口 **8080**（`server.port`）。数据库请执行仓库根目录 **`database/schema_mysql8_full.sql`** 初始化。

---

## 与前端/数据库的边界

- **接口**：REST + JSON，路径以 `/api/v1` 为前缀（与前端 `api.ts` 对齐）。
- **数据库**：表结构见 `database/schema_mysql8_full.sql`；改字段后需同步实体与 DTO。

---

## 扩展阅读

- Spring Boot 官方文档与 HELP.md 中的 Maven / Web 链接。
- 若新增业务域，建议：在 `StatusConstants` 增加常量，在 `dto` + `service` 集中状态流转，避免魔法字符串。
