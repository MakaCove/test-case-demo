# Schemas And Templates

**文档语言：** 遵循 `05_OUTPUT_AND_REPORTING.md`「文档语言」总则。JSON 键名保持英文不变。

**测试依赖：** 生成代码前按 `02_EXECUTION_WORKFLOW.md` **Step 1** 核对 `pom.xml`/`gradle` 并处理版本冲突；本文件不重复坐标表，以免与流程不同步。

---

## 1) `tech-scan-report.json`（最小模板）

```json
{
  "scope": {
    "repoRoot": "string（仓库相对路径）",
    "javaModule": "string",
    "generatedAt": "ISO-8601",
    "timestamp": "yyyyMMdd-HHmmss（与目录一致）"
  },
  "build": {
    "tool": "maven|gradle|unknown",
    "springBootVersion": "string|unknown"
  },
  "testing": {
    "framework": "junit4|junit5|testng|unknown",
    "httpClient": "restassured|mockmvc|webtestclient|unknown",
    "baseTestClass": "string|unknown（如 com.example.AbstractApiTest）",
    "restAssuredCompatibleVersion": "string|unknown（根据 Spring Boot 版本推荐，如 5.x）"
  },
  "auth": {
    "mode": "jwt|session|oauth2|basic|none|unknown",
    "publicPaths": ["string（如 /api/v1/auth/login）"],
    "protectedPathPatterns": ["string（如 /api/v1/**）"]
  },
  "conventions": {
    "responseWrapper": "string|unknown（如 com.example.Result）",
    "globalExceptionHandler": "string|unknown（如 GlobalExceptionHandler）",
    "dtoValidation": ["NotNull", "NotBlank", "Size"]
  },
  "notes": "string（中文，扫描过程中的特殊发现或说明）"
}
```

---

## 2) `api-change-report.json`（最小模板）

```json
{
  "scope": {
    "baseRef": "string",
    "headRef": "string",
    "generatedAt": "ISO-8601",
    "timestamp": "yyyyMMdd-HHmmss"
  },
  "summary": {
    "changedFiles": 0,
    "endpointsAdded": 0,
    "endpointsModified": 0,
    "endpointsRemoved": 0,
    "inferredImpacts": 0
  },
  "endpoints": [
    {
      "method": "POST",
      "path": "/api/v1/example",
      "endpointKey": "POST /api/v1/example",
      "changeType": "added|modified|removed",
      "controller": "ExampleController",
      "handlerMethod": "create",
      "notes": ["中文说明，如「新增了 userId 字段校验」"],
      "evidenceFiles": ["backend/src/main/java/.../ExampleController.java"]
    }
  ],
  "inferredImpacts": [
    {
      "endpointKey": "POST /api/v1/example",
      "reason": "中文说明，如「ExampleDTO 新增了必填字段 userId」",
      "evidenceFile": "backend/src/main/java/.../ExampleDTO.java"
    }
  ]
}
```

---

## 3) `api-test-cases.json`（最小模板）

```json
{
  "scope": {
    "baseRef": "string",
    "headRef": "string",
    "generatedAt": "ISO-8601",
    "timestamp": "yyyyMMdd-HHmmss"
  },
  "summary": {
    "total": 0,
    "priority": { "P0": 0, "P1": 0, "P2": 0, "P3": 0 },
    "status": { "ready": 0, "draft": 0, "blocked": 0 }
  },
  "cases": [
    {
      "caseId": "TC-EXAMPLE-001",
      "title": "创建资源-合法入参-返回201",
      "endpointKey": "POST /api/v1/example",
      "priority": "P0",
      "type": "功能",
      "status": "ready",
      "preconditions": "用户已登录，存在有效 Bearer Token",
      "request": {
        "method": "POST",
        "url": "/api/v1/example",
        "headers": { "Authorization": "Bearer <token>", "Content-Type": "application/json" },
        "body": { "name": "测试资源-${timestamp}" }
      },
      "assertions": [
        "HTTP 状态码应为 201",
        "业务码 code 应为 0",
        "data.id 不为 null 且为正整数",
        "data.name 应与请求 name 一致"
      ],
      "expectedResult": "返回新创建资源的 id 与 name，HTTP 状态码 201",
      "cleanup": "调用 DELETE /api/v1/example/{id} 删除测试数据",
      "tags": ["功能", "P0", "创建"],
      "sourceEvidence": "backend/src/main/java/com/example/ExampleController.java#create",
      "blockedReason": null
    },
    {
      "caseId": "TC-EXAMPLE-002",
      "title": "创建资源-缺少必填字段-返回400",
      "endpointKey": "POST /api/v1/example",
      "priority": "P1",
      "type": "异常",
      "status": "ready",
      "preconditions": "用户已登录",
      "request": {
        "method": "POST",
        "url": "/api/v1/example",
        "headers": { "Authorization": "Bearer <token>", "Content-Type": "application/json" },
        "body": {}
      },
      "assertions": [
        "HTTP 状态码应为 400",
        "错误信息包含「name 不能为空」"
      ],
      "expectedResult": "返回 400 校验错误，错误信息指向 name 字段",
      "cleanup": "无",
      "tags": ["异常", "P1", "校验"],
      "sourceEvidence": "backend/src/main/java/com/example/ExampleDTO.java#name",
      "blockedReason": null
    },
    {
      "caseId": "TC-EXAMPLE-003",
      "title": "创建资源-无 token-返回401",
      "endpointKey": "POST /api/v1/example",
      "priority": "P0",
      "type": "安全",
      "status": "ready",
      "preconditions": "未携带 Authorization 头",
      "request": {
        "method": "POST",
        "url": "/api/v1/example",
        "headers": { "Content-Type": "application/json" },
        "body": { "name": "测试" }
      },
      "assertions": [
        "HTTP 状态码应为 401"
      ],
      "expectedResult": "未登录访问受保护接口，返回 401",
      "cleanup": "无",
      "tags": ["安全", "P0", "鉴权"],
      "sourceEvidence": "backend/src/main/java/com/example/SecurityConfig.java#configure",
      "blockedReason": null
    },
    {
      "caseId": "TC-EXAMPLE-004",
      "title": "批量创建-部分字段待确认",
      "endpointKey": "POST /api/v1/example/batch",
      "priority": "P1",
      "type": "功能",
      "status": "draft",
      "preconditions": "用户已登录；批量上限字段值待产品确认",
      "request": {},
      "assertions": ["待补充"],
      "expectedResult": "待确认批量上限后补充",
      "cleanup": "待补充",
      "tags": ["功能", "P1", "批量"],
      "sourceEvidence": "backend/src/main/java/com/example/ExampleController.java#batchCreate",
      "blockedReason": null
    },
    {
      "caseId": "TC-EXAMPLE-005",
      "title": "导出资源-依赖外部存储服务",
      "endpointKey": "GET /api/v1/example/export",
      "priority": "P2",
      "type": "功能",
      "status": "blocked",
      "preconditions": "需要外部对象存储服务（OSS）可用",
      "request": {},
      "assertions": [],
      "expectedResult": "返回文件下载链接",
      "cleanup": "无",
      "tags": ["功能", "P2", "导出"],
      "sourceEvidence": "backend/src/main/java/com/example/ExportController.java#export",
      "blockedReason": "依赖外部 OSS 服务，测试环境未配置；建议解除条件：配置 OSS Mock 或测试桶后改为 ready"
    }
  ]
}
```

---

## 4) Java 测试方法注释模板

```java
/**
 * caseId: TC-EXAMPLE-001
 * endpointKey: POST /api/v1/example
 * title: 创建资源-合法入参-返回201
 * preconditions: 用户已登录，存在有效 Bearer Token
 * assertions:
 *   - HTTP 状态码应为 201
 *   - 业务码 code 应为 0
 *   - data.id 不为 null 且为正整数
 * sourceEvidence: backend/src/main/java/com/example/ExampleController.java#create
 */
```

## 4.1) Java 测试类注释模板

```java
/**
 * 模块：example
 * 覆盖范围：
 *   - POST /api/v1/example（创建资源）
 *   - GET  /api/v1/example/{id}（查询资源）
 *   - DELETE /api/v1/example/{id}（删除资源）
 * 鉴权策略：受保护接口使用 Bearer Token；未登录场景显式覆盖 401。
 * 数据策略：每条用例独立造数，测试数据前缀 "[TEST]"，@AfterEach 中清理。
 * 说明：本类仅覆盖本次变更范围（<base> -> <head>），非全量回归。
 */
```

## 4.2) draft 用例骨架模板

```java
/**
 * caseId: TC-EXAMPLE-004
 * endpointKey: POST /api/v1/example/batch
 * title: 批量创建-部分字段待确认
 * status: draft — 批量上限字段值待产品确认后补充
 */
@Test
@Disabled("draft: 批量上限字段值待确认，补充后去掉 @Disabled")
void batchCreate_draft() {
    // TODO: draft — 请补充：
    //   1. 确认批量上限值（当前不确定）
    //   2. 补充请求体与断言
    //   3. 补充数据清理逻辑
    //   完成后删除 @Disabled 注解并更新用例状态为 ready
}
```

## 4.3) blocked 用例注释块模板（写在类末尾）

```java
/*
 * ===== BLOCKED 用例（不生成代码，需人工处理）=====
 *
 * caseId: TC-EXAMPLE-005
 * endpointKey: GET /api/v1/example/export
 * title: 导出资源-依赖外部存储服务
 * blockedReason: 依赖外部 OSS 服务，测试环境未配置
 * 建议解除条件: 配置 OSS Mock 或测试桶后将状态改为 ready，重新生成代码
 *
 * ==================================================
 */
```

---

## 5) 代码生成约束（强制片段）

生成测试代码时必须满足以下约束：

```text
1) 每条测试必须打印入参、响应、断言结果。
2) 入参打印内容：method/url/headers/query/body。
3) 响应打印内容：status/headers/body。
4) 断言结果打印内容：每条断言的名称、期望值、实际值、pass/fail。
5) 除控制台日志外，必须将以上信息写入 Allure：
   - 请求：附件名格式「请求 · <中文步骤说明> · <caseId>」
   - 响应：附件名格式「响应 · <中文步骤说明> · <caseId>」
   - 断言结果：附件名格式「断言结果 · <caseId>」
   - 附件正文首行：「步骤说明（中文）=<…>\ncaseId=<…>」
6) 断言使用 assertAll 聚合，失败信息包含字段路径与 caseId。
7) 对 token/password 等敏感字段做脱敏后打印（token 仅前 8 位 + ***）。
8) 每个测试类与测试方法包含详细注释；关键代码块解释设计意图。
9) 每个测试方法有主 Allure.step，标题格式：
   「caseId=<…> | endpointKey=<METHOD path> | 目的=<中文描述>」
10) 单条用例内含多次 HTTP 请求时，必须加嵌套中文子 step。
11) draft 用例加 @Disabled 注解，blocked 用例不生成代码。
```

---

## 6) Java 代码片段模板（可观测性标准实现）

完整可观测性示例代码见 `07_EXAMPLES.md`「示例 G：可观测性标准实现」。约束清单见上方「5) 代码生成约束」。

---

---

## 7) `allure.properties` 模板

```properties
allure.results.directory=target/allure-results
```

---

## 8) `pom.xml` 依赖片段参考

以下仅为格式参考，版本号须按 Step 1.3 版本冲突规则确定：

```xml
<properties>
    <rest-assured.version>5.4.0</rest-assured.version>  <!-- Boot 3.x 用 5.x -->
    <allure.version>2.27.0</allure.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <version>${rest-assured.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.qameta.allure</groupId>
        <artifactId>allure-junit5</artifactId>
        <version>${allure.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.qameta.allure</groupId>
        <artifactId>allure-rest-assured</artifactId>
        <version>${allure.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.qameta.allure</groupId>
            <artifactId>allure-maven</artifactId>
            <version>2.12.0</version>
        </plugin>
    </plugins>
</build>
```
