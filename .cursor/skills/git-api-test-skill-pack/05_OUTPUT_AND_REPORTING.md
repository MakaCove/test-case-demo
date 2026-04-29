# Output And Reporting

## 文档语言（必须）

1. **Markdown（`.md`）**
   `tech-scan-report.md`、`api-change-report-*.md`、`api-test-cases-*.md` 等：**正文、章节标题、表格说明、列表项**均使用**简体中文**。
   例外：代码标识、文件路径、`endpointKey`、`METHOD path`、类全名、命令行、HTTP 头名等保持英文或与仓库一致。

2. **JSON（`.json`）**
   - **键名（key）**：保持英文蛇形/驼峰，便于程序消费。
   - **人读值**：`notes`、`reason`、`title`（用例标题）、`preconditions`、`expectedResult`、`sourceEvidence`、`blockedReason`、推断说明、摘要句等，**使用简体中文**。
   - **机器值**：`changeType`、`status`、`priority`、`method`、`path`、`caseId`、`endpointKey` 等按规范保留英文或固定枚举。

3. **对用户回传**（聊天/报告摘要）
   与本次执行相关的说明、统计、风险、DoD 核对项，**默认简体中文**；必要时在括号内保留英文术语。

4. **Java 测试代码**
   类注释、方法 JavaDoc、`Allure.step` 中文目的/预期：使用**简体中文**；代码标识符仍为英文。

---

## 标准输出产物

### 1. 技术扫描报告
- `artifacts/tech-scan/<timestamp>/tech-scan-report.md`
- `artifacts/tech-scan/<timestamp>/tech-scan-report.json`

### 2. 变更报告
- `artifacts/api-change-docs/<timestamp>/api-change-report-<base>-<head>.md`
- `artifacts/api-change-docs/<timestamp>/api-change-report-<base>-<head>.json`

### 3. 用例文档
- `<java_module>/src/test/java/.../apitest/<timestamp>/api-test-cases-<base>-<head>.md`
- `<java_module>/src/test/java/.../apitest/<timestamp>/api-test-cases-<base>-<head>.json`

### 4. 测试代码（禁止默认合并为单类）
- `<java_module>/src/test/java/.../apitest/<Name>ApiTest.java`（按 Controller 或 URL 前缀拆分）
- `<java_module>/src/test/java/.../apitest/support/`（可选：基类、可观测性/附件工具等）

### 5. Allure 报告
- `<java_module>/target/allure-results`（Maven）/ `<java_module>/build/allure-results`（Gradle）
- `<java_module>/target/site/allure-maven-plugin`（Maven HTML）

---

## 用户回传模板

执行完成后，向用户回传以下内容（全部使用简体中文）：

```
## 执行摘要

**变更范围**：<base_ref> -> <head_ref>
**参数解析**：base_ref=<…> | head_ref=<…> | last_n=<…>（若适用） | java_module=<…>
**时间戳**：<yyyyMMdd-HHmmss>

---

## 依赖检查结果

| 依赖坐标 | 处理结果 | 说明 |
|---------|---------|------|
| io.rest-assured:rest-assured | 新增 5.4.0 | Spring Boot 3.x，升级至兼容版本 |
| io.qameta.allure:allure-junit5 | 已存在，版本兼容 | 当前 2.27.0，在建议范围内 |

**编译验证**：已通过 / 未执行（原因：<…>）

---

## 技术扫描报告

- MD：artifacts/tech-scan/<timestamp>/tech-scan-report.md
- JSON：artifacts/tech-scan/<timestamp>/tech-scan-report.json

---

## 接口变更统计

| 类型 | 数量 |
|------|------|
| 新增接口 | <n> |
| 修改接口 | <n> |
| 删除接口 | <n> |
| 推断影响 | <n> |

**变更报告**：
- MD：artifacts/api-change-docs/<timestamp>/api-change-report-<base>-<head>.md
- JSON：artifacts/api-change-docs/<timestamp>/api-change-report-<base>-<head>.json

---

## 用例文档

- MD：<java_module>/src/test/java/.../apitest/<timestamp>/api-test-cases-<base>-<head>.md
- JSON：<java_module>/src/test/java/.../apitest/<timestamp>/api-test-cases-<base>-<head>.json

**用例统计**：总数 <n>（P0 <n> / P1 <n> / P2 <n> / P3 <n>）
**用例状态**：ready <n> / draft <n> / blocked <n>

**blocked 用例清单**（若有）：
- TC-XXX-001：<blockedReason>，建议解除条件：<…>

---

## 测试文件清单

- <java_module>/src/test/java/.../apitest/AuthApiTest.java（<n> 个测试方法）
- <java_module>/src/test/java/.../apitest/UserApiTest.java（<n> 个测试方法）
- <java_module>/src/test/java/.../apitest/support/AbstractApiIntegrationTest.java

---

## 执行命令

**清理（推荐）**：
mvn -pl <module> clean -q

**执行测试**：
mvn -pl <module> test -Dtest=*ApiTest

**生成报告**：
mvn -pl <module> allure:report

**查看报告（本地浏览器）**：
mvn -pl <module> allure:serve

---

## 执行结果

Tests run: <n>, Failures: <n>, Errors: <n>, Skipped: <n>

**allure.properties 状态**：已配置，路径 = target/allure-results
**Allure 结果目录**：<java_module>/target/allure-results
**Allure HTML 报告**：<java_module>/target/site/allure-maven-plugin

---

## 可观测性检查

| 项目 | 状态 |
|------|------|
| 入参日志（控制台） | 已输出 |
| 响应日志（控制台） | 已输出 |
| 断言结果日志（控制台） | 已输出 |
| Allure 请求附件 | <n> 个 |
| Allure 响应附件 | <n> 个 |
| Allure 断言结果附件 | <n> 个 |
| 报告历史隔离 | 已清理旧结果后生成 |
```

---

## 失败处理与兜底

| 失败类型 | 处理方式 |
|---------|---------|
| 依赖版本冲突 | 按 Step 1.3 规则处理，回传中列出决策理由 |
| 编译失败 | 优先修复重复 `package`/重复类/import 冲突，修复后重新执行 |
| 测试失败 | 返回失败类、失败方法、首个关键报错；不得静默忽略 |
| Allure 报告失败 | 检查 `allure.properties` 与 `target/allure-results` 是否存在 |
| 未识别到接口 | 输出推断影响接口（基于 DTO/Service 变更），提示用户是否手动指定 |
| 变更文件列表为空 | 回传说明原因，终止执行 |

---

## Definition of Done (DoD)

满足以下**全部条件**才算本次执行完成：

1. 技术扫描报告（md + json）已生成，Markdown 正文符合中文要求
2. 变更报告（md + json）已生成，接口清单与统计数据一致
3. 用例文档（md + json）已生成，每个 `endpointKey` 至少 1 条用例，状态已标注
4. `ready` 用例已落地为完整测试代码；`draft` 用例已生成 `@Disabled` 骨架；`blocked` 用例已在回传中单独列出
5. 测试所需依赖已在 `pom.xml`/`gradle` 中校验并处理（补齐/版本确认/冲突说明）
6. 测试已执行并返回统计结果（Tests run / Failures / Errors / Skipped）
7. Allure HTML 报告已生成（至少路径可用）
8. 随机抽检至少 1 条用例在 Allure 中可见"入参/响应/断言结果"附件
9. 本次报告基于已清理的历史结果目录生成
