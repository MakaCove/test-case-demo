# 提示词模板（LLM）

| 文件 | 用途 |
|------|------|
| [功能测试用例_LLM提示词.md](./功能测试用例_LLM提示词.md) | 生成 **功能用例** JSON → 解析后写入 `test_cases` |
| [接口测试用例_LLM提示词.md](./接口测试用例_LLM提示词.md) | 生成 **接口用例** JSON → 解析后写入 `api_test_cases` |

复制各文档中的 **「系统提示词」** 到前端 **Prompt 模板** 维护页，或执行 `database/seed_optional_prompt_templates.sql` 向库中插入同名全局模板（可选）。

若改动了 Markdown 中的系统提示词正文，请同步更新 **`database/seed_optional_prompt_templates.sql`** 里对应 `INSERT` 的 `content`，避免文档与库内模板不一致。
