# Scope And Principles

## 适用场景

- 根据最近 N 次提交自动生成接口测试
- 识别接口变更并补齐回归测试
- 产出"变更报告 + 用例文档 + 自动化代码 + Allure 报告"

## 输入参数

- `base_ref`：起始提交
- `head_ref`：结束提交（默认 `HEAD`）
- `last_n`：最近 N 次提交（默认 7，推导 `HEAD~N -> HEAD`）
- `repo_root`：仓库根目录
- `output_dir`：报告输出根目录（默认 `artifacts/api-change-docs`）
- `java_module`：目标 Java 模块（可选）
- `dry_run`：仅预览不写文件不执行命令（默认 `false`）

优先级：`base_ref/head_ref` > `last_n` > 默认值。

## 核心原则

1. 先分析后生成
2. 文档先行（先用例文档后代码）
3. 先读后写（覆盖任何已有文件前必须先读取）
4. 生成物可追溯（caseId 与 endpointKey）
5. 输出路径可迁移（仅仓库相对路径，禁止绝对路径）
6. 测试代码按接口边界拆分文件（Controller 或 URL 前缀），公共逻辑进 `apitest/support/`，避免单文件堆砌与重复粘贴
7. **文档以中文为主**：遵循 `05_OUTPUT_AND_REPORTING.md`「文档语言」总则
8. **构建依赖可运行**：生成接口测试前核对并补齐测试栈依赖（见 `02_EXECUTION_WORKFLOW.md` Step 1），避免编译期或运行期才暴露缺包

## 路径约束（强制）

- 禁止在输出中写本机绝对路径。
- 变更报告目录：`artifacts/api-change-docs/<timestamp>/`
- 技术扫描目录：`artifacts/tech-scan/<timestamp>/`
- 测试代码目录：`<java_module>/src/test/java/<base_package_path>/apitest/`，其中 `<base_package_path>` 取 `src/main/java` 下与 Controller 相同的包前缀（如 `com/example/demo`）；若 `src/test/java` 下已有测试包结构则沿用
- 用例文档目录：`<java_module>/src/test/java/<base_package_path>/apitest/<timestamp>/`
- Allure 结果目录：`<java_module>/target/allure-results`（Maven）/ `<java_module>/build/allure-results`（Gradle）

## 时间戳格式（强制统一）

所有路径与文件名中的 `<timestamp>` 统一使用格式：`yyyyMMdd-HHmmss`，例如 `20260429-143000`。

- 同一次执行中所有目录使用**同一个**时间戳（在 Step 1 准备目录时生成一次，后续步骤复用）。
- 禁止在不同步骤中分别生成时间戳（避免目录不一致）。

## 关键术语

- `endpointKey`：`METHOD path`，如 `POST /api/v1/users`
- `ready`：可进入代码生成的用例状态
- `draft`：结构完整但细节待确认，生成代码骨架（方法体注释掉，标注 `// TODO: draft`）
- `blocked`：受环境/依赖/权限等外部条件限制，无法自动化；必须记录阻断原因与解除条件
- `DoD`：执行完成定义（见 `05_OUTPUT_AND_REPORTING.md`）
- `sourceEvidence`：格式为 `<相对文件路径>#<类名或方法名>`，如 `backend/src/main/java/com/example/UserController.java#createUser`；若证据来自 git commit 则格式为 `commit:<hash>:<相对路径>`
