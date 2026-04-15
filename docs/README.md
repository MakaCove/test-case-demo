# 文档索引

| 文档 | 说明 |
|------|------|
| [product-overview.md](./product-overview.md) | 产品定位、模块能力、与代码对齐的边界说明 |
| [api-reference.md](./api-reference.md) | 后端 REST API（`/api/v1`），与 Controller 一致 |
| [database.md](./database.md) | 数据表清单与约定；DDL 以 `database/schema_mysql8_full.sql` 为准 |
| [architecture.md](./architecture.md) | 仓库组成与部署关系（backend / frontend / ui-runner） |
| [prompts/README.md](./prompts/README.md) | 功能 / 接口 / UI 规划步骤 LLM 提示词（与库内种子模板同步） |

**数据库初始化**：仅执行仓库根目录的 **`database/schema_mysql8_full.sql`**（含 `DROP TABLE`，勿直接用于生产覆盖）。
