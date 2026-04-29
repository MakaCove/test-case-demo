# Execution Workflow

## Step 0: 初始化与技术扫描（必须，首先执行）

> **本步骤 0.1~0.4 为硬前置条件，禁止跳过任何子步骤。**

### 0.1 输入校验

1. 校验 `repo_root` 路径存在且为 git 仓库（`.git` 目录存在）。
2. 若用户指定了 `java_module`，校验其 `pom.xml`（或 `build.gradle`）存在。
3. 校验 Maven/Gradle 可执行（`mvn --version` / `gradle --version`），若不可用则在回传中注明"未检测到构建工具，将跳过编译验证与测试执行"。
4. 若用户指定了 `dry_run=true`，后续所有步骤**仅读不写**，不修改任何文件、不执行命令。回传中输出"将生成的用例清单"与"将写入的文件列表"，不实际落盘。

### 0.2 生成时间戳

生成格式 `yyyyMMdd-HHmmss` 的时间戳（如 `20260429-143000`）。同一次执行只生成一次，后续所有步骤复用该值。

### 0.3 清理全部旧产物（必须，禁止跳过）

清空上次执行生成的全部产物，确保本次为干净的全量生成。以下产物一律删除（先检查是否存在，存在则删除，不存在则跳过）：

**目录（整体删除）：**
- `artifacts/tech-scan/` — 所有历史技术扫描报告
- `artifacts/api-change-docs/` — 所有历史变更报告
- `<java_module>/src/test/java/.../apitest/` 下所有 `<timestamp>/` 子目录 — 历史用例文档

**文件：**
- `apitest/` 根目录下所有 `*ApiTest.java`、`*ApiIntegrationTest.java`
- `apitest/support/` 下本技能生成的工具类（通过标准类注释模板判定归属，含"覆盖范围""鉴权策略"等字段）

**判定规则：** 无法判定归属的文件（无标准注释），列出并询问用户是否删除。

**回传：** 列出已删除的文件/目录清单；若无旧产物，注明"未发现旧产物，跳过清理"。

> 注意：**不清理** `allure.properties`（配置持久化）与 `pom.xml`/`build.gradle`（依赖修改为项目级变更，非一次性产物）。

### 0.4 创建目录

基于 0.2 的时间戳，一次性创建以下目录：
- `artifacts/tech-scan/<timestamp>/`
- `artifacts/api-change-docs/<timestamp>/`
- `<java_module>/src/test/java/.../apitest/<timestamp>/`
- `<java_module>/src/test/resources/`（若不存在）

> 若处于 `dry_run` 模式，跳过实际创建，仅在回传中列出将创建的目录。

### 0.5 执行技术扫描

扫描并输出：
- 构建工具（Maven/Gradle）及版本
- Spring Boot 版本（影响依赖兼容性判断）
- 测试框架（JUnit4/5/TestNG）
- HTTP 测试客户端（RestAssured/MockMvc/WebTestClient）
- 鉴权方式与白名单/受保护路径（Security 配置、Filter、Interceptor）
- DTO 校验注解（`@Valid`、`@Validated`、`@NotNull` 等）
- 响应封装类与全局异常处理（`@RestControllerAdvice`、统一 Result 类）
- 已存在的测试基类（`src/test/java` 下的抽象类）

输出到：`artifacts/tech-scan/<timestamp>/tech-scan-report.{md,json}`

**语言（必须）**：遵循 `05_OUTPUT_AND_REPORTING.md`「文档语言」总则。

**Exit Criteria**（Step 0 整体）：
- 输入校验已通过（或已明确标注风险）
- 时间戳已生成
- 旧产物已清理（或已确认无需清理）
- 输出目录已创建
- `tech-scan-report.md` 与 `tech-scan-report.json` 同时存在
- 关键字段缺失时标记为 `unknown`（而非空缺或臆测）

---

## Step 1: 执行前检查（必须）

**Entry Criteria**
- Step 0 已完成

### 1.1 确定 java_module

1. **定位 `java_module`**：
   - 单模块项目：直接使用仓库根目录。
   - 多模块项目：
     - 若本次变更文件**全部归属同一模块**：自动选择该模块。
     - 若变更文件**跨多个模块**：列出各模块及其涉及的变更文件清单，询问用户"选择主模块还是逐模块分别执行"，等待用户答复后继续。
     - **禁止**在跨模块场景下静默选择一个模块并忽略其余。
2. 若用户已在输入参数中指定 `java_module`，优先使用用户指定值。

### 1.2 管理 allure.properties

- 路径：`<java_module>/src/test/resources/allure.properties`
- 先读取文件（若存在），检查是否已含 `allure.results.directory`：
  - 已存在且值正确：不修改。
  - 已存在但重复出现（超过 1 次）：去重，保留 1 次。
  - 不存在：新建并写入。
- 保证文件中 `allure.results.directory=target/allure-results` 恰好出现 **1 次**。

### 1.3 测试依赖检查与补齐（必须）

**必须打开并核对**目标模块的 `pom.xml`（或 `build.gradle` / `build.gradle.kts`），对照 Step 0 选定的技术栈执行以下逻辑：

#### 版本冲突处理规则（新增）

对每个待检查依赖，执行：
1. **检查是否已存在**：在 `pom.xml` 的 `<dependencies>` 中搜索相同 `groupId:artifactId`。
2. **若不存在**：直接写入。
3. **若已存在**：读取当前版本，与兼容版本范围对比：
   - **版本兼容**（在建议范围内）：不修改，在回传中标注"已存在，版本兼容"。
   - **版本过旧或不兼容**（如 Spring Boot 3 项目用了 RestAssured 4.x）：更新版本，并在回传中标注"已更新版本，原版本=X，新版本=Y，原因=<不兼容原因>"。
   - **版本过新（高于已知兼容范围）**：保留现有版本不动，在回传中标注"版本高于建议范围，保留现有版本，请人工确认兼容性"。
4. **禁止**在版本冲突未明确处理的情况下静默写入，导致 pom 中出现重复坐标。

#### RestAssured 方案（本技能默认）依赖清单

| 用途 | Maven `groupId:artifactId` | `scope` | 版本策略 |
|------|---------------------------|---------|----------|
| Spring 测试与 JUnit 5 | `org.springframework.boot:spring-boot-starter-test` | `test` | 随 Spring Boot parent，一般无需写 version |
| HTTP 客户端 | `io.rest-assured:rest-assured` | `test` | Boot 2.x 项目建议 4.5.x；Boot 3.x / Jakarta 项目建议 5.x，写入 `<properties>` 中 `rest-assured.version` |
| Allure + JUnit 5 | `io.qameta.allure:allure-junit5` | `test` | 建议 `<properties>` 中 `allure.version`，当前推荐 2.27.x |
| Allure + RestAssured 过滤器 | `io.qameta.allure:allure-rest-assured` | `test` | 与 `allure-junit5` 同版本 |
| Allure 报告生成 | `io.qameta.allure:allure-maven` | **插件** | 写在 `<build><plugins>`，推荐 `2.12.0` |

**Spring Boot 版本与 RestAssured 版本对照（强制参考）**：
- Spring Boot `< 3.0`（使用 `javax.*`）→ RestAssured `4.x`
- Spring Boot `>= 3.0`（使用 `jakarta.*`）→ RestAssured `5.x`

#### MockMvc / WebTestClient 方案

若 Step 0 选定非 RestAssured，则**不得**强塞 `rest-assured`；改为确保 `spring-boot-starter-test` 已含对应用法，或按项目惯例补 `spring-security-test` 等（以实际 import 为准）。

#### Gradle 等价

在 `dependencies` 中补 `testImplementation` 等价坐标；Allure 使用 `io.qameta.allure` 官方 Gradle 插件配置。

#### 编译验证

补齐后执行编译快速校验（按平台选择）：
- **Maven**：`mvn -q -pl <module> test-compile`
- **Gradle**：`./gradlew :<module>:testClasses`

若环境不允许执行命令，须在回传中注明"未执行编译校验，建议手动执行上述命令"。

### 1.4 禁止擅自加重量依赖

未在用户任务中明确要求时：
- **不得**增加 Testcontainers、嵌入式 H2、额外 JDBC 驱动等仅服务于测试的重量依赖。
- **不得**主动新增 `*init*.sql`、测试专用全量建表脚本等 SQL 产物。
- 若用户明确要求"容器化/无本地库"方案，须在回传中单独说明用途、维护方与启用条件。

**Exit Criteria**
- `java_module` 已确定（跨模块场景已与用户确认）
- `allure.properties` 中 `allure.results.directory` 恰好 1 次
- 依赖检查已完成：新增/更新/保留决策均已记录，版本冲突已按规则处理
- 若已改构建文件：编译验证已通过或已在回传中说明未执行原因

---

## Step 2: 提交范围解析

**Entry Criteria**
- Step 1 已完成

- 按优先级解析参数：`base_ref/head_ref` > `last_n` > 默认（`last_n=7`）
- 若传入了 `last_n` 同时也有 `base_ref/head_ref`：使用 `base_ref/head_ref`，在回传中注明 `last_n` 已忽略
- 执行 `git diff --name-only <base_ref> <head_ref>`
- 回显最终参数：`base_ref`、`head_ref`、`last_n`（若适用）、`java_module`

**Exit Criteria**
- 变更文件列表可用（允许为空，空列表时在回传中说明"未检测到变更文件"后终止）
- 最终参数已回显

---

## Step 3: 接口变更识别

**Entry Criteria**
- Step 2 已完成并有变更文件列表

**识别逻辑**：
- 解析 Controller 路由（类级 `@RequestMapping` + 方法级 `@GetMapping`/`@PostMapping` 等）
- 生成 `endpointKey`（格式：`METHOD /完整路径`）
- 判定变更类型：`added`（新增方法/类）/ `modified`（方法签名、DTO、Service 变更）/ `removed`（方法或类被删除）
- 结合 DTO/Service/Security 配置推断间接影响接口（即 Controller 未变但依赖的类有变更）

**非 Controller 变更的推断规则**：
- DTO 变更 → 找到所有使用该 DTO 的 Controller 方法，标记为 `modified`（`inferredImpact`）
- Service 变更 → 找到调用该 Service 的 Controller 方法，标记为 `inferredImpact`
- Security 配置变更 → 全量标记受保护路径集合可能变化，在报告中注明"鉴权配置已变更，建议重新覆盖 401/403 用例"

**Exit Criteria**
- 已生成稳定的 `endpointKey` 集合
- 变更类型统计与推断影响集合已生成
- 若无法从变更文件中识别任何接口，在回传中说明原因（如"变更仅涉及配置/工具类"）并提示用户是否手动指定接口范围

---

## Step 4: 变更报告生成（必须）

**Entry Criteria**
- Step 3 已完成

输出（写入 `artifacts/api-change-docs/<timestamp>/`）：
- `api-change-report-<base>-<head>.md`
- `api-change-report-<base>-<head>.json`

要求：
- md 与 json 内容保持一致（统计数字、接口清单不能矛盾）
- 两文件缺一即本步骤失败，禁止继续后续步骤
- **语言**：md 使用简体中文；JSON 中 `notes`、`reason` 等叙述字段为中文

**Exit Criteria**
- 两文件同时存在
- 统计数据与接口清单一致

---

## Step 5: 用例文档生成（必须）

**Entry Criteria**
- Step 4 已完成
- Step 0 技术扫描产物可读

输入：Step 4 报告 JSON + Step 0 技术扫描 JSON

输出（写入 `<java_module>/src/test/java/.../apitest/<timestamp>/`）：
- `api-test-cases-<base>-<head>.md`
- `api-test-cases-<base>-<head>.json`

约束：
- 每个 `endpointKey` 至少 1 条用例
- 用例状态定义见 `01_SCOPE_AND_PRINCIPLES.md`「关键术语」；处理流程见 Step 6.2
- `sourceEvidence` 格式：`<相对文件路径>#<类名或方法名>`（见 `01_SCOPE_AND_PRINCIPLES.md`）
- **语言**：md 全文简体中文；JSON 叙述字段简体中文（`caseId`/`endpointKey` 等保持规范格式）

**Exit Criteria**
- 两文件同时存在且位于 `apitest/<timestamp>/` 目录（禁止写入 `apitest/` 根目录）
- 每个 `endpointKey` 至少有一条关联用例
- 用例状态已标注，`blocked` 用例已记录原因

---

## Step 6: 测试代码生成

**Entry Criteria**
- Step 5 已完成
- 存在 `ready` 或 `draft` 用例
- **再次确认 Step 1 依赖清单**：若生成代码将引入新 import（如 `JsonPath`、`Awaitility`），须先回到 Step 1 按版本冲突规则补齐依赖，再写测试类

### 6.1 文件拆分（必须）

- 按 **Controller** 或**稳定 URL 前缀**（如 `/api/v1/auth`、`/api/v1/users`）拆分为多个 `*ApiTest.java`
- 每个类只覆盖同一资源族/同一控制器
- 禁止把多个无关接口堆进单个 `*ApiTest` 类（用户显式要求单文件时除外，须在类注释写明原因）
- 公共逻辑放在 `apitest/support/`（基类、可观测性工具、Token 管理等）
- 禁止在多个 `*ApiTest` 中复制粘贴相同工具代码

### 6.2 用例状态处理

- `ready` 用例：生成完整测试方法
- `draft` 用例：生成方法骨架，方法体内核心逻辑注释为 `// TODO: draft — 请补充测试数据与断言`，`@Disabled("draft: 待完善")` 标注，避免 CI 误跑
- `blocked` 用例：不生成代码；在对应 `*ApiTest` 类末尾添加注释块，列出所有 blocked 用例的 caseId 与阻断原因

### 6.3 防重复规则

- 覆盖写文件前**必须先读取**已有内容，不得盲目追加
- 单文件仅一个 `package` 声明
- 顶层类与文件名一致且仅一份
- 禁止追加第二段完整源码

### 6.4 可观测性（必须）

每条用例必须打印并记录：
- 入参（method/url/headers/query/body）
- 响应（status/headers/body）
- 断言结果（每条断言的 pass/fail 与失败原因）

日志同时输出到：
- 控制台（便于本地排障）
- Allure 报告附件（便于回溯，命名格式见下）

**Allure Step 与附件规范**：
- 每条用例至少 1 条主 `Allure.step`，标题格式：`caseId=<…> | endpointKey=<METHOD path> | 目的=<中文描述>`
- 单条用例内含多次 HTTP 请求时，必须为每个业务子阶段加**嵌套中文 step**，禁止内层全是匿名 HTTP
- 附件命名格式：`请求 · <中文步骤说明> · <caseId>`、`响应 · <中文步骤说明> · <caseId>`、`断言结果 · <caseId>`
- 附件正文首行须包含 `步骤说明（中文）=<…>` 与 `caseId=<…>`
- **不得**将 `AllureRestAssured` 全局过滤器作为唯一附件来源（其默认附件名无语义，多步骤下堆叠难以阅读）；建议统一走 `apitest/support` 封装的工具方法

### 6.5 注释完整性（必须）

- 每个 `*ApiTest` 类必须有类注释：覆盖接口范围、鉴权策略、数据准备策略
- 每个测试方法必须有多行注释：`caseId`、`endpointKey`、`title`、`preconditions`、`assertions`、`sourceEvidence`
- 关键步骤（请求构造、鉴权注入、断言分组、数据清理）须有"意图型注释"（解释为什么这样做）
- 注释与实现必须一致，接口变更时同步更新

**Exit Criteria**
- 每条 `ready` 用例均有完整测试方法（含 caseId / endpointKey）
- 每条 `draft` 用例均有骨架方法（含 `@Disabled` 与 `// TODO`）
- `blocked` 用例已在对应类注释块中列出
- `*ApiTest` 已按 Controller / URL 前缀拆分
- 无重复 `package`、重复同名类
- 每条用例均包含"入参/响应/断言"日志与 Allure 附件
- 若修改了 `pom.xml`/`gradle`：回传中列出新增/更新的坐标与版本

---

## Step 7: 测试执行与 Allure 报告

**Entry Criteria**
- Step 6 已完成且测试代码可编译

### 7.1 执行前清理（必须，防止历史结果污染）

使用构建工具的 `clean` 统一清理（跨平台，无路径分隔符问题）：

- **Maven**：`mvn -pl <module> clean -q`
- **Gradle**：`./gradlew :<module>:clean`

### 7.2 执行测试

**Maven**：
```
mvn -pl <module> test -Dtest=*ApiTest
mvn -pl <module> allure:report
```
可选（本地查看）：`mvn -pl <module> allure:serve`

**Gradle**：
```
./gradlew :<module>:test --tests "*ApiTest"
./gradlew :<module>:allureReport
```

### 7.3 失败处理

- **编译失败**：优先修复重复 `package`/重复类/import 冲突，修复后重新执行
- **测试失败**：返回失败类、失败方法、首个关键报错，不得静默忽略
- **Allure 报告生成失败**：检查 `allure.properties` 与结果目录是否存在

**Exit Criteria**
- 有测试执行统计（Tests run / Failures / Errors / Skipped）
- Allure HTML 报告目录可用
- 随机抽检至少 1 条用例：Allure 中能看到请求、响应、断言结果附件/步骤
- 报告基于已清理后的结果目录生成，不含历史残留

---

## 必须规则（硬约束）

1. 不允许跳过 Step 5 直接写代码
2. 覆盖写任何 `.java` 或 `allure.properties` 前必须先读取已有内容
3. 不允许把报告写到时间戳目录外
4. 不允许在未清理旧 `allure-results`/旧 HTML 目录的情况下生成新报告
5. 不允许在用户**未**声明「禁止修改构建文件」的前提下，在缺少已选定方案所需依赖时仍只生成测试代码而不补构建文件
6. 版本冲突必须按 Step 1.3 规则显式处理，禁止静默写入重复坐标
7. 跨模块变更场景必须询问用户确认，禁止静默选择
8. `blocked` 用例禁止生成代码，`draft` 用例必须用 `@Disabled` 隔离
9. 执行前必须按 Step 0.3 清理全部旧产物，确保每次生成为干净的全量产物
10. 禁止跳过 Step 0.1~0.4 直接执行 Step 0.5 或 Step 1
