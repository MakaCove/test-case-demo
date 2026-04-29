# Assertion Rules

## 断言分层模型

### L1 协议层（必选）
- HTTP 状态码
- Content-Type
- 关键响应头（如 `X-Request-Id`、`Location`）

### L2 结构层（必选）
- 响应整体结构（如 `code/message/data` 是否存在）
- 关键字段存在性与类型（`data.id` 不为 null、`data.name` 为 string）

### L3 业务层（必选）
- 业务成功码/失败码（如 `code=0` 表示成功）
- 关键业务字段值（如 `data.status="ACTIVE"`）
- 错误语义匹配（如错误信息包含"用户名已存在"）

### L4 副作用层（建议）
- 写操作后通过读接口验证数据实际落库（写后读）
- 跨接口一致性（如创建后在列表中可查到）

### L5 非功能层（按需）
- 性能阈值（响应时间 < N ms）
- 幂等性（重复请求结果一致）
- 安全性（越权访问被拒绝、敏感字段已脱敏）

---

## 成功场景最低断言

1. HTTP 状态码（通常 200 或 201）
2. 业务码（如 `code=0`）
3. 至少一个关键业务字段值或存在性
4. 写操作必须有至少一个副作用断言（写后读验证）
5. 每条断言结果可输出（断言名、期望值、实际值）

## 失败场景最低断言

1. HTTP 状态码（4xx/5xx）
2. 业务错误码（若有）
3. 错误信息中的关键字（如"不能为空"、"用户名已存在"）
4. **无**副作用断言（失败场景不验证数据库状态）
5. 失败断言必须输出可定位信息：JSONPath/字段名 + 期望值 + 实际值 + `caseId`

---

## 动态字段断言规则

对 `id`、`requestId`、`token`、时间戳等动态生成字段：
- **使用"存在性 + 格式/范围"断言**，例如：
  - `data.id` 不为 null 且为正整数
  - `data.createdAt` 匹配 ISO-8601 格式
  - `data.token` 长度 > 0 且不含空格
- **禁止**固定值全等断言（如 `assertEquals("abc123", token)`）

---

## 鉴权断言规范

受保护接口最少必须覆盖：

| 场景 | 预期 HTTP 状态码 | 说明 |
|------|-----------------|------|
| 无 token | 401 | 未携带 Authorization 头 |
| 非法/过期 token | 401 | 格式错误或已过期的 token |
| 有效 token 但权限不足 | 403 | 有权限模型时覆盖 |

---

## 断言可观测性规范（强制）

1. 每条测试至少输出三类信息（控制台 + Allure，**断言说明短语使用简体中文**）：
   - 入参快照（请求方法、URL、headers、query、body）
   - 响应快照（status、headers、body）
   - 断言结果（每条断言 pass/fail）

2. 断言结果既要在控制台可见，也要在 Allure 报告中以附件形式可见。

3. 若使用聚合断言（`assertAll`），断言条目名称必须可读，例如：
   - `"HTTP 状态码应为 200"`
   - `"业务码 code 应为 0"`
   - `"data.id 不应为 null"`

4. 对动态字段（id/requestId/token/timestamp），打印时应脱敏或截断敏感值：
   - token：仅打印前 8 位 + `***`
   - password：替换为 `[REDACTED]`

5. Allure 附件命名须含中文步骤语义，禁止大量无区别的 `Request`/`HTTP/1.1 200`（与 `02_EXECUTION_WORKFLOW.md` Step 6.4 一致）。

---

## 断言实现规范

### 聚合断言（推荐）

使用 JUnit 5 的 `assertAll` 汇总多个断言，确保所有失败一次性暴露：

```java
assertAll("TC-AUTH-001 断言集",
    () -> assertEquals(200, resp.statusCode(), "HTTP 状态码应为 200"),
    () -> assertEquals(0, resp.jsonPath().getInt("code"), "业务码 code 应为 0"),
    () -> assertNotNull(resp.jsonPath().get("data.token"), "data.token 不应为 null")
);
```

### 断言日志记录

```java
List<String> assertionLogs = new ArrayList<>();
// 在每个 lambda 内追加：
assertionLogs.add("HTTP状态码 expected=200 actual=" + actual + " pass=" + (actual == 200));
// 最后：
Allure.addAttachment("断言结果 · TC-AUTH-001", String.join("\n", assertionLogs));
```

---

## 禁止项（反模式）

| 反模式 | 说明 |
|--------|------|
| 只断言 HTTP 200 | 未验证业务逻辑 |
| 只断言 `code=0` | 未验证实际业务数据 |
| 全量 JSON 严格全等 | 动态字段（id/时间戳）导致不稳定 |
| 忽略 401/403 鉴权语义 | 安全漏洞无法被测试捕获 |
| 失败信息不含字段路径/caseId | 无法快速定位问题 |
| 只在控制台打印，不写 Allure 附件 | 报告中无法追溯证据 |
| 对动态字段做固定值全等断言 | 测试不稳定，随机失败 |
