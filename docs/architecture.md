# 系统组成与部署关系

```
┌─────────────┐     HTTP/JSON      ┌─────────────┐
│  frontend   │  ───────────────► │   backend   │
│  (Vue 3)    │  ◄───────────────  │ (Spring Boot)│
└─────────────┘   :8080 /api/v1    └──────┬──────┘
                                         │
                          MySQL          │  HTTP (ui-runner 客户端)
                                         ▼
                                  ┌─────────────┐
                                  │  ui-runner  │
                                  │ (FastAPI)   │
                                  └──────┬──────┘
                                         │
                                  Playwright/Chromium
                                         ▼
                                         被测 Web
```

| 组件 | 说明 |
|------|------|
| **frontend** | Vite 开发默认 `http://localhost:5173`；通过 Axios 访问 `api/v1` |
| **backend** | Spring Boot + MyBatis-Plus；库名 `ai_testcase_platform`；见 `backend/README.md` |
| **ui-runner** | 独立进程，默认 `127.0.0.1:18081`；`app.ui-runner` 在 `application.yml` 配置 |
| **database** | 仅 `database/schema_mysql8_full.sql` 全量建库 |

**日志**：后端 Log4j2 文件路径受工作目录影响；详见 `backend` 内说明。
