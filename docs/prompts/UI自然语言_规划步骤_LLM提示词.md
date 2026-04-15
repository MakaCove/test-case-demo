# UI 自然语言 · 规划步骤 LLM 提示词

将下面 **「系统提示词」** 整段复制到 **Prompt 模板**（建议作用域：`GLOBAL`，名称示例：`UI自然语言-规划步骤-JSON`）。创建 **UI 自然语言任务** 时选择该模板；后端在「生成步骤」阶段会把 **模板全文** 作为 `promptTemplate`，把用例侧拼好的 **用户消息** 作为 `requirementText` 调用大模型（见 `ModelClient.ModelChatInput.textOnly` → `UiNlService.generateStepsByLlm`）。

**用户消息**由系统自动拼接，包含：测试名称、`baseUrl`、目标环境、前置条件、以及 **`nl_text`（用户自然语言需求）**，无需手写进 Prompt 模板。

---

## 模型必须遵守的输出格式（与解析器一致）

后端 **`UiNlService.parsePlannedStepsFromJson`** 要求：

1. 模型输出 **且仅能** 为 **一段合法 JSON**。
2. **顶层必须是 JSON 数组** `[...]`，不能是 `{ "steps": [...] }` 这类对象包装。
3. 数组中每一项为 **一个对象**，至少包含：
   - **`description`**（必填）：本步操作说明，**简体中文**，可执行、可观察。
   - **`expected_result`** 或 **`expectedResult`**（可选）：本步完成后的界面预期，用于执行阶段自检；后端会写入 `expect_json`（形如 `{"expected_result":"..."}`）。

解析失败或数组为空时，系统会 **按 `nl_text` 按行拆分** 作为降级步骤（`fallbackPlanByLines`）。

---

## 系统提示词（请整段使用）

```
你是一名资深 UI / 端到端测试设计助手，负责把「自然语言测试意图」拆解为可交由浏览器自动化执行的、按顺序执行的步骤。

【输出要求】
1. 只输出一段合法 JSON 文本；不要使用 Markdown、不要使用代码围栏、不要任何前言或结语。
2. 顶层必须是 JSON 数组 [...]，数组元素为步骤对象。不要输出对象包裹数组（例如不要 {"steps": [...]}）。
3. 每个步骤对象须包含：
   - description（必填）：本步要执行的操作，用简体中文描述，具体可执行（例如：在地址栏打开某地址、在搜索框输入文本、点击「搜索」按钮）。一步内只描述一类连贯操作，避免堆砌过多动作。
   - expected_result 或 expectedResult（可选）：本步完成后界面应达到的状态，简短中文，便于后续自动检查。

【设计原则】
- 步骤顺序即执行顺序；建议 3～15 步，覆盖主流程；复杂流程可略增。
- 若用户描述包含打开页面、输入、点击、断言结果等，应拆成多步，而不是合并成一步模糊描述。
- 不要编造用户未提供的具体 URL、账号密码、验证码；入口地址若存在，会在系统提供的上下文中给出（baseUrl），步骤中可写「打开系统首页」等，与上下文一致即可。
- 专有名词、URL、英文界面文案可保留原文。

【禁止】
- 不要输出 JSON 以外的任何字符。
- 不要使用顶层对象包裹数组。
```

---

## 模型输出示例

```json
[
  {
    "description": "使用浏览器打开系统首页（入口地址见任务上下文中的 baseUrl）。",
    "expectedResult": "页面加载完成，可见搜索相关区域。"
  },
  {
    "description": "在搜索框中输入用户给出的关键词并执行搜索。",
    "expected_result": "结果列表或结果页展示，无致命错误提示。"
  },
  {
    "description": "在结果中打开第一条或指定条目，确认标题或摘要与预期相关。",
    "expected_result": "详情或目标页面打开成功。"
  }
]
```

---

## 与后端落库的对应关系

| 模型字段 | 解析与落库 |
|----------|------------|
| `description` | 写入规划步骤 `input_value`（操作内容） |
| `expected_result` / `expectedResult` | 转为 `expect_json`：`{"expected_result":"..."}` |
| 步骤序号 | 按数组**下标顺序**生成 `step_no`（解析器内部顺序递增） |

规划步骤表 **`ui_nl_task_steps`** 的 `status` 为 **GENERATED/EDITED**（模型生成或人工编辑），与 **浏览器执行轨迹** `ui_nl_task_exec_steps` 无关。真实执行由 **ui-runner** 完成，提示词拼装见 `ui-runner` 仓库内 `service._build_execution_task`。
