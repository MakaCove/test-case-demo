# 数据库说明

- **权威 DDL**：仓库根目录 **`database/schema_mysql8_full.sql`**（含 `DROP TABLE`、建库、建表、默认 `admin`、文末全局 Prompt 模板 `INSERT`，含功能/接口/UI 规划步骤）。
- **字段级注释**：以 SQL 内 `COMMENT` 为准；本文仅作表级索引。

---

## 表清单

| 表名 | 说明 |
|------|------|
| `users` | 登录用户 |
| `projects` | 项目 |
| `project_versions` | 项目版本 |
| `requirement_assets` | 需求资产（文本/文档/原型） |
| `prompt_templates` | Prompt 模板 |
| `model_configs` | 大模型连接配置 |
| `generation_tasks` | 用例生成任务 |
| `generation_task_refs` | 生成任务引用参考版本 |
| `ui_nl_cases` | UI 自然语言用例库 |
| `ui_nl_tasks` | UI 自然语言任务（规划状态 + `last_exec_status`） |
| `ui_nl_task_steps` | 规划步骤（模型生成/人工编辑） |
| `ui_nl_task_exec_steps` | 执行轨迹（runner 步骤结果） |
| `ui_nl_reports` | UI 测试报告 |
| `test_cases` | 功能测试用例 |
| `api_test_cases` | 接口测试用例 |
| `test_case_status_logs` | 功能用例执行/评审状态变更日志 |
| `test_case_histories` | 功能用例内容历史快照 |
| `export_records` | 导出任务记录 |
| `operation_logs` | 操作审计日志 |

---

## 通用约定

- **`is_deleted`**：`0` 未删除，`1` 已删除（逻辑删除）。`operation_logs` 无此字段。
- **`created_by` / `updated_by`**：用户 ID。
- **JSON 列**：MySQL `JSON`；Java 实体中部分以 `String` 映射。
- **业务单号**：`case_no`、`task_no`、`export_no`、`report_no`、`asset_code` 等由服务生成唯一值；见各表注释。

---

## 与前端展示

- **`export_records`** 无单独「导出内容」列；列表展示由后端从 `request_json` 等解析后填入 DTO。
- **项目 `owner` / `archived`**：以 API 聚合字段为准，与库表字段可能为映射关系。
