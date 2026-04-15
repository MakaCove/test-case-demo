# ui-runner

独立 **UI 自然语言 / 浏览器自动化执行服务**：接收 Java 后端下发的「任务说明 + 可选规划步骤」，通过 **browser-use**（内置 **Playwright** 驱动 Chromium）调用 **OpenAI 兼容多模态 LLM** 在浏览器中执行任务，并把执行状态与步骤结果以 HTTP 轮询方式供后端消费。

与 **backend** 解耦部署：backend 只负责业务数据与调用 `app.ui-runner.base-url`；本服务负责真实浏览器与 Agent 推理。

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 运行时 | Python 3（建议 3.11+） |
| HTTP | FastAPI + Uvicorn |
| 校验 | Pydantic v2 |
| 浏览器自动化 | browser-use、Playwright（需 `playwright install chromium`） |
| LLM | `browser_use.ChatOpenAI`（OpenAI 兼容：官方 OpenAI、DashScope 等） |

---

## 目录结构

```
ui-runner/
├── app/
│   ├── main.py              # FastAPI 应用、环境引导、鉴权、路由
│   ├── models/schemas.py    # RunRequest / RunResponse / StepResult / StatusResponse
│   └── runner/service.py    # RunnerService：异步执行、落盘、取消
├── runs/                    # 每次执行的产物目录（按 runId 分目录，勿提交敏感内容）
│   └── <RUN-xxx>/
│       ├── result.json      # 当前状态快照（供进程重启后 GET 恢复）
│       ├── agent_history.json  # browser-use 原始历史（可选）
│       └── shots/           # 步骤截图副本（从 agent 临时路径拷贝）
├── .env / .env.example      # 本地配置（密钥勿入库）
├── requirements.txt
└── README.md
```

---

## 启动与配置

1. 创建虚拟环境并安装依赖：

```bash
cd ui-runner
python -m venv .venv
.venv\Scripts\activate   # Windows
pip install -r requirements.txt
playwright install chromium
```

2. 复制环境文件并填写 **API Key**、可选 **RUNNER_TOKEN**（与 backend `app.ui-runner.token` 一致）：

```bash
copy .env.example .env
```

3. 启动服务（默认端口与 backend 示例一致为 **18081**）：

```bash
uvicorn app.main:app --host 127.0.0.1 --port 18081
```

---

## 环境变量说明

启动时 `main.py` 会优先加载项目根目录 **`.env`**；若不存在则回退读取 **`.env.example`**（仅便于本地起步，生产务必使用独立 `.env`）。

| 变量 | 含义 |
|------|------|
| `RUNNER_TOKEN` | 若非空，所有业务接口需 `Authorization: Bearer <token>`；为空则不校验（仅建议开发环境） |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` | 传给 `ChatOpenAI`，驱动 browser-use Agent |
| `DASHSCOPE_API_KEY` / `DASHSCOPE_BASE_URL` / `QWEN_MODEL` | 便捷别名：未设置 `OPENAI_*` 时会自动映射到 `OPENAI_*`（见 `main._bootstrap_env`） |
| `CHROME_PATH` | 可选，指定本机 Chrome/Chromium 可执行文件路径 |
| `HEADLESS` | 示例 `.env` 中用于文档说明；实际 headless 由请求体 `RunRequest.headless` 决定 |
| `BROWSER_WINDOW_SIZE` / `BROWSER_WINDOW_WIDTH`+`HEIGHT` | 有界面模式下固定窗口大小，如 `1920x1080` |
| `BROWSER_DISABLE_START_MAXIMIZED` | 非空则关闭「默认最大化」相关修正逻辑 |
| `BROWSER_KEEP_DISABLE_WINDOW_ACTIVATION` | 与 browser-use 默认 Chrome 参数有关，见 `service.py` 注释 |

控制台会打印脱敏后的模型与 `api_key` 前缀，便于确认配置是否加载。

---

## HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/health` | 健康检查，无需 Token |
| `POST` | `/run` | 提交一次执行（异步），立即返回 `accepted` |
| `GET` | `/runs/{run_id}` | 查询状态：`status`、`summary`、`errorMessage`、`artifactsJson`、`steps[]` |
| `POST` | `/runs/{run_id}/cancel` | 请求取消：协作调用 `agent.stop()` 并取消协程 |

若配置了 `RUNNER_TOKEN`，除 `/health` 外需在 Header 带：`Authorization: Bearer <token>`。

### `POST /run` 请求体（`RunRequest`）

| 字段 | 说明 |
|------|------|
| `runId` | 与 backend 侧 `runner_run_id` 对齐的唯一 ID（3～64 字符） |
| `taskText` | 自然语言任务说明；可与 `plannedSteps` 二选一或组合（至少其一非空） |
| `plannedSteps` | 可选：来自后端的规划步骤（`stepNo`、标题、操作、预期 JSON 等），会拼进 Agent 任务正文并用于步骤对齐 |
| `baseUrl` | 可选：会先要求打开该 URL，再执行后续任务 |
| `headless` | 是否无头浏览器 |
| `model` | 可选：覆盖默认模型名；默认取环境变量中的 Qwen/OpenAI 模型 |
| `timeoutSeconds` | 整次 `agent.run()` 超时（30～7200 秒） |

### 运行状态 `status`（字符串）

常见取值：`PENDING` → `RUNNING` → `COMPLETED` | `FAILED` | `CANCELLED`。

### `steps[]`（`StepResult`）

由 Agent 历史转换而来：含 `stepNo`、`title`、`actionType`、`status`（如 `SUCCESS`/`FAILED`）、`errorMessage`、截图相对路径、耗时、`targetJson`/`inputValue`/`expectJson`、`rawLog` 等，供 backend 写入 `ui_nl_task_exec_steps` 等表。

---

## 执行流程（`RunnerService`）

1. **`submit_run`**：若同 `runId` 已在 `PENDING`/`RUNNING`，直接返回内存状态；否则新建 `RunState`，`asyncio.create_task` 进入 **`_execute`**。
2. **`_execute`**：状态置 `RUNNING` 并 **`_persist_state`**（写入 `runs/<runId>/result.json`）；构建 `ChatOpenAI`、`Browser`、`Agent`，在超时内 `await agent.run()`。
3. **成功**：`COMPLETED`，从 history 生成 `steps`、可选保存 `agent_history.json`，`artifactsJson` 中记录 `runDir`、`resultFile` 等路径。
4. **失败**：`FAILED`，尽量从已产生的 `history` 恢复步骤；否则生成单步错误占位。
5. **取消**：`cancel_run` 调用 `agent.stop()` 再 `task.cancel()`，状态 `CANCELLED` 并落盘。
6. **重启恢复**：内存无状态时，`GET /runs/{id}` 可 **`load_state_from_disk`** 读取 `result.json` 并 `remember_state`。

---

## 与 backend 的对接

- backend **`application.yml`**：`app.ui-runner.base-url`（如 `http://127.0.0.1:18081`）、`app.ui-runner.token` 与本服务 `RUNNER_TOKEN` 一致。
- Java 侧 **`UiRunnerClient`** 调用上述接口，轮询 `GET /runs/{runId}`，把步骤与截图路径同步回业务库与报告。

---

## 安全与运维建议

- **切勿**将含真实 Key 的 `.env` 提交到 Git；`.env.example` 仅保留占位符。
- 生产环境务必设置 **`RUNNER_TOKEN`**，并限制网络仅 backend 可访问本服务。
- `runs/` 下含截图与日志，注意磁盘占用与定期清理策略。

---

## 常见问题

- **浏览器未安装**：执行 `playwright install chromium`；或设置 `CHROME_PATH` 指向本机 Chrome。
- **LLM 报错**：检查 `OPENAI_API_KEY` / `OPENAI_BASE_URL` 与厂商文档；DashScope 用户可使用 `DASHSCOPE_*` 别名。
- **窗口未最大化**：见 `service.py` 中 `_fix_browser_window_maximized` 与 `.env.example` 注释。
