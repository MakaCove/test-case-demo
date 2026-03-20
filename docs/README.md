# 文档索引

| 文档 | 说明 |
|------|------|
| [PRD_AI测试用例管理平台.md](./PRD_AI测试用例管理平台.md) | 产品需求与当前实现范围（对齐仓库现状） |
| [数据库表结构说明.md](./数据库表结构说明.md) | 表清单与字段说明（与 `database/schema_mysql8_full.sql` 一致） |
| [接口清单_API_v1.md](./接口清单_API_v1.md) | 后端 REST API 路径与方法（`/api/v1`） |

数据库 DDL 请以 **`../database/schema_mysql8_full.sql`** 为准。

## LLM 提示词（功能 / 接口用例）

见 **[`prompts/README.md`](./prompts/README.md)**：可复制到「Prompt 模板」，生成 JSON 可由后端解析并写入 `test_cases` / `api_test_cases`。
