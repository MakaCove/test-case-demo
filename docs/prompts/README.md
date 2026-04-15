# LLM 提示词

| 文件 | 用途 |
|------|------|
| [功能测试用例_LLM提示词.md](./功能测试用例_LLM提示词.md) | 生成 **功能用例** JSON → 解析写入 `test_cases` |
| [接口测试用例_LLM提示词.md](./接口测试用例_LLM提示词.md) | 生成 **接口用例** JSON → 解析写入 `api_test_cases` |
| [UI自然语言_规划步骤_LLM提示词.md](./UI自然语言_规划步骤_LLM提示词.md) | **UI 自然语言任务**「生成步骤」→ 顶层 JSON 数组 → `UiNlService.parsePlannedStepsFromJson` → `ui_nl_task_steps` |

复制各文档中的 **「系统提示词」** 到前端 **Prompt 模板** 维护页；全量建库时 **`database/schema_mysql8_full.sql`** 文末已插入全局模板（含 `UI自然语言-规划步骤-JSON`）。

若修改 Markdown 正文，请同步更新 **`database/schema_mysql8_full.sql`** 文末对应 `INSERT` 的 `content`，保持与库内模板一致。
