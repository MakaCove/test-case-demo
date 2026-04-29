---
name: git-api-test-skill-pack
description: 接口测试 / API 测试 / 自动化测试 / 生成测试用例 / RestAssured / Allure。根据 Git 提交记录识别接口变更，自动完成技术扫描→用例设计→代码生成→测试执行→Allure 报告的完整流水线。
---

# Git API Test Skill Pack

## 核心目标

根据 Git 变更自动完成：**接口识别 → 用例文档 → 测试代码 → 执行 → Allure 报告** 的完整链路。

## 必读文件（按序）

1. `01_SCOPE_AND_PRINCIPLES.md` — 适用场景、输入参数、核心原则、路径约束、时间戳格式、术语定义
2. `02_EXECUTION_WORKFLOW.md` — Step 0~7 完整执行流程（**核心文件**。Step 0 包含初始化：校验→时间戳→清理旧产物→创建目录→技术扫描）
3. `03_TEST_CASE_RULES.md` — 用例设计规则、字段模板、状态模型、数据策略、优先级
4. `04_ASSERTION_RULES.md` — 断言分层模型、最低要求、动态字段策略、反模式
5. `05_OUTPUT_AND_REPORTING.md` — 产物清单、回传模板、文档语言总则、DoD

## 参考文件（按需）

6. `06_SCHEMAS_AND_TEMPLATES.md` — JSON 模板、Java 注释模板、pom.xml 依赖片段
7. `07_EXAMPLES.md` — 典型执行示例（last_n、版本冲突、跨模块、draft/blocked）

## 冲突处理规则

1. 流程冲突：以 `02_EXECUTION_WORKFLOW.md` 的「必须」条款优先
2. 输出格式冲突：以 `05_OUTPUT_AND_REPORTING.md` 为准
3. 模板与流程冲突：流程优先，模板按流程修正

## 维护约定

| 变更内容 | 修改文件 |
|---------|---------|
| 流程步骤 | `02` |
| 用例设计规则 | `03` |
| 断言规范 | `04` |
| 回传/报告/Doc/DoD | `05` |
| JSON/Java 模板 | `06` |
| 示例 | `07` |
| 核心原则/路径/术语/时间戳 | `01` |
| 文档语言总则 | `05`；各 Step 及 `03` 同步引用 |
| 依赖检查/版本冲突/跨模块策略 | `02` Step 1 |
| draft/blocked 定义与处理 | `01` 术语定义（权威）+ `02` Step 5/6 + `03` 状态规则，三处须同步 |
