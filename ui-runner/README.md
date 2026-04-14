# ui-runner

独立 UI 自然语言执行服务（Python + FastAPI + browser-use + Playwright）。

## 目录

- `app/main.py`：HTTP 入口（`/run`、`/runs/{id}`、`/runs/{id}/cancel`）
- `app/runner/service.py`：任务执行与状态持久化
- `app/models/schemas.py`：请求/响应模型
- `runs/`：每次执行产物（`result.json`）

## 本地启动

```bash
cd ui-runner
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
playwright install chromium
copy .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 18081
```

## 与 backend 对接

- `POST /run`
  - 请求：`runId`、`taskText`、`baseUrl`、`headless`、`model`、`timeoutSeconds`
  - 响应：`accepted`、`runnerRunId`
- `GET /runs/{runId}`
  - 响应：`status`、`summary`、`errorMessage`、`steps[]`
- `POST /runs/{runId}/cancel`

若配置 `RUNNER_TOKEN`，请求需带 `Authorization: Bearer <token>`。
