# Examples

文档语言遵循 `05_OUTPUT_AND_REPORTING.md` 总则。

---

## Example A: 最近 10 次提交生成接口测试

### 输入

- `last_n=10`
- `head_ref=HEAD`
- `repo_root=<workspace>`
- `java_module` 未显式指定（自动发现）

### 期望执行顺序

1. 技术扫描 → 输出 `artifacts/tech-scan/20260429-143000/...`
2. 执行前检查：确定 `java_module=backend`，生成时间戳 `20260429-143000`，检查并补齐 pom.xml 依赖
3. 解析范围 `HEAD~10 -> HEAD`
4. 识别变更接口并生成变更报告
5. 生成用例文档（ready/draft/blocked 分类）
6. 为 `ready` 用例生成多个 `*ApiTest.java`（按 Controller 拆分），`draft` 用例生成 `@Disabled` 骨架，`blocked` 用例写注释块
7. 执行 `mvn -pl backend clean -q` 清理旧结果
8. 执行 `mvn -pl backend test -Dtest=*ApiTest`
9. 执行 `mvn -pl backend allure:report`

### 期望输出文件

```
artifacts/tech-scan/20260429-143000/tech-scan-report.md
artifacts/tech-scan/20260429-143000/tech-scan-report.json
artifacts/api-change-docs/20260429-143000/api-change-report-HEAD~10-HEAD.md
artifacts/api-change-docs/20260429-143000/api-change-report-HEAD~10-HEAD.json
backend/src/test/java/com/example/apitest/20260429-143000/api-test-cases-HEAD~10-HEAD.md
backend/src/test/java/com/example/apitest/20260429-143000/api-test-cases-HEAD~10-HEAD.json
backend/src/test/java/com/example/apitest/AuthApiTest.java
backend/src/test/java/com/example/apitest/UserApiTest.java
backend/src/test/java/com/example/apitest/support/AbstractApiIntegrationTest.java
backend/src/test/resources/allure.properties
```

### 依赖检查回传示例

```
依赖检查结果：
- io.rest-assured:rest-assured：已存在 4.5.3，但项目使用 Spring Boot 3.2 (jakarta.*)，已更新为 5.4.0
- io.qameta.allure:allure-junit5：不存在，已新增 2.27.0
- io.qameta.allure:allure-rest-assured：不存在，已新增 2.27.0
- io.qameta.allure:allure-maven（插件）：不存在，已新增 2.12.0

编译验证：mvn -q -pl backend test-compile → 已通过
```

---

## Example B: 显式范围优先于 last_n

### 输入

- `base_ref=release/1.3.0`
- `head_ref=HEAD`
- `last_n=10`

### 规则

- 实际使用 `release/1.3.0 -> HEAD`
- 回传中明确注明：`last_n=10 已忽略，以 base_ref/head_ref 为准`

---

## Example C: 受保护接口的断言设计

场景：`POST /api/v1/model-configs` 受 JWT 鉴权保护

最小用例覆盖：

| caseId | 场景 | 预期 |
|--------|------|------|
| TC-MODELCFG-001 | 无 token | HTTP 401 |
| TC-MODELCFG-002 | 非法 token（格式错误） | HTTP 401 |
| TC-MODELCFG-003 | 有效 token + 非法参数 | HTTP 400，错误信息含"不能为空" |
| TC-MODELCFG-004 | 有效 token + 合法参数 | HTTP 200/201，data.id 不为 null |

---

## Example D: 版本冲突处理示例

### 场景

项目 `pom.xml` 中已有：
```xml
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>4.5.3</version>
</dependency>
```
技术扫描发现 Spring Boot 版本为 `3.2.0`（Jakarta EE），要求 RestAssured `5.x`。

### 处理流程

1. 检测到 `rest-assured` 已存在，版本 `4.5.3`
2. 对比：Spring Boot 3.x 需要 RestAssured 5.x（Jakarta 命名空间）
3. 判定：版本不兼容，需更新
4. 执行：将 `4.5.3` 更新为 `5.4.0`
5. 回传：`io.rest-assured:rest-assured 已更新：4.5.3 → 5.4.0，原因：Spring Boot 3.x 需要 Jakarta 兼容版本`

---

## Example E: 跨模块变更处理示例

### 场景

`git diff` 结果显示以下文件变更：
```
backend/src/main/java/com/example/UserController.java
common/src/main/java/com/example/dto/UserDTO.java
```

### 处理流程

1. 检测到变更文件跨 `backend` 和 `common` 两个模块
2. 向用户询问：
   ```
   检测到变更涉及多个模块：
   - backend：UserController.java（接口变更）
   - common：UserDTO.java（DTO 变更，影响 backend 接口）

   请选择：
   A. 以 backend 为主模块执行（推荐，DTO 变更影响已在推断影响中体现）
   B. 逐模块分别执行
   ```
3. 等待用户确认后继续执行

---

## Example F: draft 与 blocked 用例的回传示例

```
用例统计：总数 12（P0 4 / P1 5 / P2 2 / P3 1）
用例状态：ready 9 / draft 2 / blocked 1

draft 用例（已生成 @Disabled 骨架，待补充后去掉注解）：
- TC-BATCH-001：批量创建-批量上限待确认
- TC-EXPORT-002：导出格式-CSV 格式字段待产品确认

blocked 用例（未生成代码，需人工处理）：
- TC-OSS-001：导出文件-依赖外部 OSS 服务
  原因：测试环境未配置 OSS；建议解除条件：配置 OSS Mock 或测试桶后改为 ready
```

---

## Example G: 可观测性标准实现（完整代码模板）

以下为一条完整测试方法的可观测性标准实现，展示 nested `Allure.step`、中文附件命名、断言日志记录：

```java
// 推荐：可观测性工具方法封装在 apitest/support/ApiTestObservability.java
// 以下为内联示例，实际使用时从 support 工具类调用

@Test
void createExample_success() {
    /**
     * caseId: TC-EXAMPLE-001
     * endpointKey: POST /api/v1/example
     * title: 创建资源-合法入参-返回201
     * preconditions: 用户已登录
     * assertions: HTTP 201, code=0, data.id 不为 null
     * sourceEvidence: backend/src/main/java/.../ExampleController.java#create
     */

    // 主步骤：包含 caseId + endpointKey + 中文目的
    Allure.step("caseId=TC-EXAMPLE-001 | endpointKey=POST /api/v1/example | 目的=校验合法入参可成功创建资源", () -> {

        // 子步骤：准备数据
        Allure.step("准备：构造合法请求体（含唯一 name 字段）", () -> {
            // 唯一值避免数据冲突
        });

        // 构造请求体
        String requestBody = objectMapper.writeValueAsString(Map.of("name", "测试资源-" + System.currentTimeMillis()));
        System.out.println("[REQUEST] POST /api/v1/example");
        System.out.println("[REQUEST] body=" + requestBody);
        // 附件名含中文步骤说明 + caseId
        Allure.addAttachment(
            "请求 · 提交创建资源 · TC-EXAMPLE-001",
            "application/json",
            "步骤说明（中文）=提交创建资源\ncaseId=TC-EXAMPLE-001\n" + requestBody,
            ".json"
        );

        // 子步骤：发起请求
        Allure.step("执行：调用创建接口，预期返回 201", () -> {
            Response resp = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(requestBody)
                .when()
                .post("/api/v1/example");

            String responseBody = resp.getBody().asPrettyString();
            System.out.println("[RESPONSE] status=" + resp.statusCode());
            System.out.println("[RESPONSE] body=" + responseBody);
            Allure.addAttachment(
                "响应 · 创建接口返回 · TC-EXAMPLE-001",
                "application/json",
                "步骤说明（中文）=创建接口返回\ncaseId=TC-EXAMPLE-001\n" + responseBody,
                ".json"
            );

            // 子步骤：断言
            Allure.step("断言：验证响应状态、业务码、data.id", () -> {
                List<String> assertionLogs = new ArrayList<>();
                assertAll("TC-EXAMPLE-001 断言集",
                    () -> {
                        int actual = resp.statusCode();
                        assertionLogs.add("HTTP状态码 expected=201 actual=" + actual + " pass=" + (actual == 201));
                        assertEquals(201, actual, "HTTP 状态码应为 201");
                    },
                    () -> {
                        int code = resp.jsonPath().getInt("code");
                        assertionLogs.add("业务码code expected=0 actual=" + code + " pass=" + (code == 0));
                        assertEquals(0, code, "业务码 code 应为 0");
                    },
                    () -> {
                        Object id = resp.jsonPath().get("data.id");
                        assertionLogs.add("data.id expected=非null actual=" + id + " pass=" + (id != null));
                        assertNotNull(id, "data.id 不应为 null");
                    }
                );
                String assertionReport = String.join("\n", assertionLogs);
                System.out.println("[ASSERTIONS]\n" + assertionReport);
                Allure.addAttachment("断言结果 · TC-EXAMPLE-001", assertionReport);
            });
        });
    });
}
```

---

## 示例回传（完整精简版）

```
## 执行摘要
变更范围：HEAD~10 -> HEAD
参数解析：last_n=10 | java_module=backend
时间戳：20260429-143000

## 依赖检查
rest-assured：4.5.3 → 5.4.0（版本升级，Spring Boot 3.x 兼容）
allure-junit5：已新增 2.27.0
编译验证：已通过

## 接口变更
新增 2 | 修改 16 | 删除 0 | 推断影响 3

## 用例统计
总数 12 | P0 4 / P1 5 / P2 2 / P3 1
状态：ready 9 / draft 2 / blocked 1

## 执行结果
Tests run: 9, Failures: 0, Errors: 0, Skipped: 2（draft 用例被 @Disabled 跳过）

## 报告
Allure HTML：backend/target/site/allure-maven-plugin
查看命令：mvn -pl backend allure:serve
```
