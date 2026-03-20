# AI 测试用例管理平台（Test Case Studio）

## 文档与数据库

- **产品 / 表说明 / API**：见 [`docs/README.md`](docs/README.md)
- **MySQL 全量建库（含字段中文注释）**：[`database/schema_mysql8_full.sql`](database/schema_mysql8_full.sql)  
  - 执行说明：[`database/README.md`](database/README.md)
- **可选 Prompt 种子**（功能 + 接口）：[`database/seed_optional_prompt_templates.sql`](database/seed_optional_prompt_templates.sql)；完整可复制正文见 [`docs/prompts/`](docs/prompts/)

## 工程结构（简）

- `backend/` — Spring Boot + MyBatis-Plus，默认库 `ai_testcase_platform`（见 `application.yml`）
- `frontend/` — Vue 3 + Element Plus

旧版根目录 `PRD_v1.3_*`、`接口清单_API_v1.md`、`数据库表结构草案_v1.md` 及 `docs/`、`database/` 下零散迁移脚本已移除，请以当前 `docs/` 与 `database/schema_mysql8_full.sql` 为准。
