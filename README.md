# AI 测试用例管理平台（Test Case Studio）

面向测试与研发的 **需求资产 → AI 生成用例 → 评审与执行 → 导出** 的一体化工作台，并集成 **UI 自然语言用例**（大模型规划步骤 + 独立浏览器执行服务）与 **操作审计**。

---

## 能力概览

| 能力域 | 说明 |
|--------|------|
| **项目与版本** | 项目 / 版本生命周期管理，需求资产（文本、文档、原型）归档 |
| **AI 生成任务** | 基于模型配置与 Prompt 模板，批量生成 **功能测试用例** 或 **接口测试用例**，支持参考版本与需求上下文 |
| **用例库** | 功能用例、接口用例的 CRUD、筛选、评审与执行状态、历史与状态日志 |
| **导出** | 将用例导出为文件（如 Markdown），异步任务与下载 |
| **UI 自然语言** | 自然语言描述用例 → 任务排队 **规划步骤**（LLM）→ **浏览器执行**（Playwright + browser-use，由 ui-runner 承载）→ 测试报告与 HTML |
| **基础能力** | 模型连接配置、Prompt 模板管理、看板、操作日志 |

---

## 技术栈

| 模块 | 技术 |
|------|------|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Vue Router、Axios |
| 后端 | Java 17、Spring Boot 3、MyBatis-Plus、MySQL 8 |
| UI 执行服务 | Python、FastAPI、browser-use、Playwright（Chromium） |
| 数据与协议 | MySQL；REST `JSON`，统一前缀 `/api/v1`；状态枚举 **英文**，前端 **中文** 展示 |

各子目录另有 **README**：[`backend/README.md`](backend/README.md)、[`frontend/README.md`](frontend/README.md)、[`ui-runner/README.md`](ui-runner/README.md)。

---

## 仓库结构

```
test-case-demo/
├── backend/           # Spring Boot API、定时任务（生成任务调度、UI-NL 规划/轮询）
├── frontend/          # Vue 单页应用，默认对接 localhost:8080
├── ui-runner/         # 独立进程：接收执行 ID，驱动浏览器完成自然语言任务
├── database/          # 全量 DDL 仅一份：schema_mysql8_full.sql
└── docs/                # 产品说明、API 参考、数据表说明、LLM 提示词（含 UI 规划步骤）
```

---

## 快速开始（开发）

### 1. 数据库

- 创建并初始化（**会 DROP 旧表**，仅用于空库或可控环境）：

```bash
mysql -u root -p < database/schema_mysql8_full.sql
```

- 默认库名：`ai_testcase_platform`；默认用户：`admin`（密码见 `backend/src/main/resources/application.yml` 中 `app.auth.bootstrap-admin-password`，首次登录后建议按后端逻辑升级为安全哈希）。

### 2. 后端

```bash
cd backend
mvn spring-boot:run
```

默认 **http://localhost:8080**，数据源与端口见 `application.yml`。

### 3. 前端

```bash
cd frontend
npm install
npm run dev
```

默认 **http://localhost:5173**；API 基地址见 `frontend/src/api/api.ts`（可按环境改为可配置变量）。

### 4. UI 自然语言执行（可选）

若使用「执行任务」跑真实浏览器，需启动 **ui-runner**，并在后端配置 `app.ui-runner.base-url` 与 `app.ui-runner.token`：

```bash
cd ui-runner
# 见 ui-runner/README.md：创建 venv、安装依赖、playwright install chromium、配置 .env
uvicorn app.main:app --host 127.0.0.1 --port 18081
```

---

## 文档索引

| 文档 | 内容 |
|------|------|
| [`docs/README.md`](docs/README.md) | 文档总目录 |
| [`docs/product-overview.md`](docs/product-overview.md) | 产品范围与已知边界 |
| [`docs/api-reference.md`](docs/api-reference.md) | HTTP API 速查 |
| [`docs/database.md`](docs/database.md) | 表清单与约定 |
| [`docs/architecture.md`](docs/architecture.md) | 组件关系（frontend / backend / ui-runner / DB） |
| [`docs/prompts/`](docs/prompts/) | 功能 / 接口 / UI 规划步骤等 LLM 提示词 |

---

## 约定说明

- **鉴权**：登录后使用 `Authorization: Bearer <token>`；未登录接口见后端 `WebCorsConfig` 排除列表。
- **状态与枚举**：数据库与接口为英文大写；前端用 `statusDictionary` 等统一映射中文。
- **数据库变更**：唯一权威脚本为 **`database/schema_mysql8_full.sql`**；改表需同步 Java 实体与相关 DTO。
