# 数据库脚本说明

## 全量建库

- **`schema_mysql8_full.sql`**：删除旧表后重建 `ai_testcase_platform` 下全部业务表，**每张表、每个字段均带中文 `COMMENT`**，并插入默认用户 `admin`（密码逻辑见后端 `application.yml` / 认证模块）。
- 执行示例：`mysql -u root -p < schema_mysql8_full.sql`

⚠️ 脚本内含 **`DROP TABLE IF EXISTS`**，请勿在生产库直接执行。

## 可选种子数据

- **`seed_optional_prompt_templates.sql`**：可选，向 `prompt_templates` 插入两条全局模板（**功能测试用例-JSON**、**接口测试用例-JSON**），正文与 **`docs/prompts/`** 下 Markdown 一致；若 `name` 已存在请先删旧行或改名再执行。

## 与代码的对应关系

表结构与 `backend/src/main/java/com/testcase/.../entity/*.java` 及 MyBatis-Plus 字段映射保持一致。未在代码中出现的表不会出现在本脚本中。
