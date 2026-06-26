# Function Calling 工具体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 chat 和 interview 接入模型 Function Calling，统一封装 Tavily 证据搜索、RAG 知识库查询和数据库只读白名单查询，并记录每次工具调用的原始请求与结果。

**Architecture:** 新增 `common/tool` 作为通用 tool call 协议，`biz/tool` 作为模型工具编排层，`biz/evidence`、`biz/rag`、`biz/database` 分别实现三类业务工具。`AgentScopeChatTextModel` 继续作为 OpenAI-compatible HTTP 客户端，但增加 tool call 请求/响应能力；chat 和 interview 通过同一个 `ToolCallingModelService` 调用模型和工具。

**Tech Stack:** Java 17、Spring Boot 4、Spring JDBC、PostgreSQL、Jackson、JDK HttpClient、OpenAI-compatible Chat Completions tools、Tavily Search API、JUnit 5、Mockito、JDK HttpServer。

---

## 执行约束

- 当前工作区已有大量未提交变更，执行本计划时不要自动 commit；只有用户明确要求提交时再执行 `git add` 和 `git commit`。
- 所有文档保持中文。
- 数据库查询工具只允许只读白名单查询，模型不能提交 SQL。
- 记录原始请求/响应时不得保存 Tavily API key、OpenAI API key、Authorization header、数据库密码。
- OpenAI-compatible tool call 请求按 Chat Completions 的 `tools`、`tool_choice`、`tool_calls`、`tool` role 消息结构实现。
- Tavily 搜索按官方 Search API 的 `POST /search`、Bearer 认证、`query`、`include_domains`、`max_results` 等字段实现。

## 文件结构

### 新增通用工具协议

- Create: `src/main/java/com/example/demoscope/common/tool/FunctionTool.java`
- Create: `src/main/java/com/example/demoscope/common/tool/FunctionToolDefinition.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallArguments.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallContext.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallContextHolder.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallEventSink.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallRecord.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallRecorder.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallResult.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallStatus.java`
- Create: `src/main/java/com/example/demoscope/common/tool/NoopToolCallRecorder.java`

### 新增工具编排层

- Create: `src/main/java/com/example/demoscope/biz/tool/FunctionToolRegistry.java`
- Create: `src/main/java/com/example/demoscope/biz/tool/DefaultFunctionToolRegistry.java`
- Create: `src/main/java/com/example/demoscope/biz/tool/ToolCallingModelService.java`
- Create: `src/main/java/com/example/demoscope/biz/tool/ToolCallingPromptPolicy.java`

### 新增 OpenAI-compatible tool call 模型 DTO

- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiChatMessage.java`
- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiChatCompletion.java`
- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiToolCall.java`
- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiChatCompletionClient.java`
- Modify: `src/main/java/com/example/demoscope/common/llm/AgentScopeChatTextModel.java`

### 新增 Tavily 客户端

- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchClient.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/DefaultTavilySearchClient.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchRequest.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchResponse.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchResult.java`

### 新增配置与 JDBC 适配

- Create: `src/main/java/com/example/demoscope/config/tool/ToolCallingConfig.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcToolSchemaInitializer.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcToolCallRecorder.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcComponentKnowledgeConfigRepository.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcDatabaseQueryConfigRepository.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcDatabaseQueryExecutor.java`

### 新增三类工具

- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentKnowledgeConfig.java`
- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentKnowledgeConfigRepository.java`
- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentMatcher.java`
- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentEvidenceTool.java`
- Create: `src/main/java/com/example/demoscope/biz/evidence/EvidenceSearchRecorder.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcEvidenceSearchRecorder.java`
- Create: `src/main/java/com/example/demoscope/biz/rag/RagKnowledgeTool.java`
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryConfig.java`
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryConfigRepository.java`
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryTool.java`
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryExecutor.java`

### 修改业务接入

- Modify: `src/main/java/com/example/demoscope/service/chat/OpenAiAgentChatService.java`
- Modify: `src/main/java/com/example/demoscope/service/chat/StreamingAgentChatService.java`
- Modify: `src/main/java/com/example/demoscope/common/llm/InterviewAiJsonClient.java`
- Modify: `src/main/java/com/example/demoscope/service/interview/InterviewService.java`
- Modify: `src/main/java/com/example/demoscope/service/interview/InterviewStreamingFacade.java`
- Modify: `src/main/java/com/example/demoscope/controller/stream/StreamMessage.java`
- Modify: `src/main/java/com/example/demoscope/controller/chat/ChatWebSocketHandler.java`
- Modify: `src/main/java/com/example/demoscope/controller/interview/InterviewWebSocketHandler.java`

## 任务 1：通用 Tool Call 协议与注册表

**Files:**
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallStatus.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallContext.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallContextHolder.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallEventSink.java`
- Create: `src/main/java/com/example/demoscope/common/tool/FunctionToolDefinition.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallArguments.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallResult.java`
- Create: `src/main/java/com/example/demoscope/common/tool/FunctionTool.java`
- Create: `src/main/java/com/example/demoscope/biz/tool/FunctionToolRegistry.java`
- Create: `src/main/java/com/example/demoscope/biz/tool/DefaultFunctionToolRegistry.java`
- Test: `src/test/java/com/example/demoscope/biz/tool/FunctionToolRegistryTest.java`
- Test: `src/test/java/com/example/demoscope/common/tool/ToolCallContextHolderTest.java`

- [ ] **Step 1: 写注册表失败测试**

测试要覆盖：按名称找到工具、未知工具失败、重复工具名失败。

```java
class FunctionToolRegistryTest {

    @Test
    void findsToolByNameAndRejectsUnknownTool() {
        FunctionTool tool = new StubTool("search_component_evidence");
        FunctionToolRegistry registry = new DefaultFunctionToolRegistry(List.of(tool));

        assertThat(registry.findRequired("search_component_evidence")).isSameAs(tool);

        assertThatThrownBy(() -> registry.findRequired("missing_tool"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown function tool");
    }

    @Test
    void rejectsDuplicateToolNames() {
        FunctionTool first = new StubTool("query_database");
        FunctionTool second = new StubTool("query_database");

        assertThatThrownBy(() -> new DefaultFunctionToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate function tool");
    }

    private record StubTool(String name) implements FunctionTool {
        @Override
        public FunctionToolDefinition definition() {
            return new FunctionToolDefinition(name, "test", Map.of("type", "object"));
        }

        @Override
        public ToolCallResult execute(ToolCallArguments arguments, ToolCallContext context) {
            return ToolCallResult.success(name, Map.of("ok", true), 1);
        }
    }
}
```

- [ ] **Step 2: 写 ThreadLocal 上下文失败测试**

```java
class ToolCallContextHolderTest {

    @Test
    void restoresPreviousContextAfterCall() {
        ToolCallContext outer = new ToolCallContext("user-1", "conv-1", "CHAT", null, ToolCallEventSink.noop());
        ToolCallContext inner = new ToolCallContext("user-2", null, "INTERVIEW", "interview-1", ToolCallEventSink.noop());

        ToolCallContextHolder.runWithContext(outer, () -> {
            assertThat(ToolCallContextHolder.current()).isEqualTo(outer);
            ToolCallContextHolder.runWithContext(inner, () ->
                    assertThat(ToolCallContextHolder.current()).isEqualTo(inner));
            assertThat(ToolCallContextHolder.current()).isEqualTo(outer);
        });

        assertThat(ToolCallContextHolder.current().businessType()).isEqualTo("UNKNOWN");
    }
}
```

- [ ] **Step 3: 实现协议最小代码**

关键类型签名：

```java
public enum ToolCallStatus {
    SUCCESS,
    FAILED,
    NEED_CLARIFICATION
}

public record ToolCallContext(
        String userId,
        String conversationId,
        String businessType,
        String businessId,
        ToolCallEventSink eventSink) {

    public static ToolCallContext unknown() {
        return new ToolCallContext(null, null, "UNKNOWN", null, ToolCallEventSink.noop());
    }
}

public record FunctionToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema) {
}

public record ToolCallArguments(String rawJson, JsonNode json) {
}

public record ToolCallResult(
        String toolName,
        ToolCallStatus status,
        Map<String, Object> payload,
        String errorMessage,
        int evidenceCount) {

    public static ToolCallResult success(String toolName, Map<String, Object> payload, int evidenceCount) {
        return new ToolCallResult(toolName, ToolCallStatus.SUCCESS, payload, null, evidenceCount);
    }

    public static ToolCallResult failed(String toolName, String errorMessage) {
        return new ToolCallResult(toolName, ToolCallStatus.FAILED, Map.of(), errorMessage, 0);
    }

    public static ToolCallResult needClarification(String toolName, String message) {
        return new ToolCallResult(toolName, ToolCallStatus.NEED_CLARIFICATION, Map.of("message", message), null, 0);
    }
}

public interface FunctionTool {
    String name();
    FunctionToolDefinition definition();
    ToolCallResult execute(ToolCallArguments arguments, ToolCallContext context);
}

public interface FunctionToolRegistry {
    FunctionTool findRequired(String name);
    List<FunctionToolDefinition> definitions();
}

public interface ToolCallEventSink {
    void started(String toolName, String message);
    void finished(String toolName, ToolCallStatus status, int evidenceCount);

    static ToolCallEventSink noop() {
        return new ToolCallEventSink() {
            @Override
            public void started(String toolName, String message) {
            }

            @Override
            public void finished(String toolName, ToolCallStatus status, int evidenceCount) {
            }
        };
    }
}
```

- [ ] **Step 4: 运行任务 1 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=FunctionToolRegistryTest,ToolCallContextHolderTest' test
```

Expected: PASS。

## 任务 2：工具记录与配置表 Schema

**Files:**
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallRecord.java`
- Create: `src/main/java/com/example/demoscope/common/tool/ToolCallRecorder.java`
- Create: `src/main/java/com/example/demoscope/common/tool/NoopToolCallRecorder.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcToolSchemaInitializer.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcToolCallRecorder.java`
- Test: `src/test/java/com/example/demoscope/common/jdbc/JdbcToolSchemaInitializerTest.java`
- Test: `src/test/java/com/example/demoscope/common/jdbc/JdbcToolCallRecorderTest.java`

- [ ] **Step 1: 写 Schema 初始化失败测试**

使用 Mockito 验证 `create table if not exists` 覆盖四张表。

```java
class JdbcToolSchemaInitializerTest {

    @Test
    void createsToolTablesAndIndexes() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        JdbcToolSchemaInitializer initializer = new JdbcToolSchemaInitializer(jdbc);

        initializer.initializeSchema();

        verify(jdbc, atLeastOnce()).execute(contains("create table if not exists ai_component_knowledge_config"));
        verify(jdbc, atLeastOnce()).execute(contains("create table if not exists ai_database_query_config"));
        verify(jdbc, atLeastOnce()).execute(contains("create table if not exists tool_call_records"));
        verify(jdbc, atLeastOnce()).execute(contains("create table if not exists ai_evidence_search_records"));
    }
}
```

- [ ] **Step 2: 写工具调用记录失败测试**

```java
class JdbcToolCallRecorderTest {

    @Test
    void recordsRawArgumentsAndRawResultWithoutSecrets() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC);
        JdbcToolCallRecorder recorder = new JdbcToolCallRecorder(jdbc, clock);

        recorder.record(new ToolCallRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "search_component_evidence",
                "CHAT",
                "user-1",
                "conv-1",
                null,
                "{\"query\":\"spring websocket\"}",
                "{\"status\":\"SUCCESS\"}",
                ToolCallStatus.SUCCESS,
                null,
                12,
                Instant.parse("2026-06-26T00:00:00Z")));

        verify(jdbc).update(
                contains("insert into tool_call_records"),
                any(), eq("search_component_evidence"), eq("CHAT"), eq("user-1"), eq("conv-1"),
                isNull(), eq("{\"query\":\"spring websocket\"}"), eq("{\"status\":\"SUCCESS\"}"),
                eq("SUCCESS"), isNull(), eq(12L), any());
    }
}
```

- [ ] **Step 3: 实现 Schema**

`JdbcToolSchemaInitializer.initializeSchema()` 创建：

```sql
create table if not exists ai_component_knowledge_config (
    id uuid primary key,
    component_code text not null unique,
    component_name text not null,
    aliases_json text not null,
    official_domains_json text not null,
    official_entry_urls_json text not null,
    stackoverflow_tags_json text not null,
    backend_keywords_json text not null,
    priority integer not null,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
)
```

```sql
create table if not exists ai_database_query_config (
    id uuid primary key,
    query_code text not null unique,
    business_type text not null,
    description text not null,
    table_name text not null,
    allowed_fields_json text not null,
    allowed_filter_fields_json text not null,
    required_filter_fields_json text not null,
    user_scope_field text,
    default_order_by text,
    max_limit integer not null,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
)
```

```sql
create table if not exists tool_call_records (
    id uuid primary key,
    tool_name text not null,
    business_type text not null,
    user_id text,
    conversation_id text,
    business_id text,
    raw_arguments_json text not null,
    raw_result_json text not null,
    status text not null,
    error_message text,
    duration_ms bigint not null,
    created_at timestamptz not null
)
```

```sql
create table if not exists ai_evidence_search_records (
    id uuid primary key,
    tool_call_id uuid,
    component_code text,
    search_type text not null,
    query_text text not null,
    raw_request_json text not null,
    raw_response_json text not null,
    matched_urls_json text not null,
    created_at timestamptz not null
)
```

- [ ] **Step 4: 实现 `JdbcToolCallRecorder`**

`ToolCallRecord` 字段与表字段一一对应；`ToolCallStatus` 存入 `status.name()`。

- [ ] **Step 5: 运行任务 2 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=JdbcToolSchemaInitializerTest,JdbcToolCallRecorderTest' test
```

Expected: PASS。

## 任务 3：组件配置读取与内置后端组件种子

**Files:**
- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentKnowledgeConfig.java`
- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentKnowledgeConfigRepository.java`
- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentMatcher.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcComponentKnowledgeConfigRepository.java`
- Modify: `src/main/java/com/example/demoscope/common/jdbc/JdbcToolSchemaInitializer.java`
- Test: `src/test/java/com/example/demoscope/biz/evidence/ComponentMatcherTest.java`
- Test: `src/test/java/com/example/demoscope/common/jdbc/JdbcComponentKnowledgeConfigRepositoryTest.java`

- [ ] **Step 1: 写组件匹配失败测试**

```java
class ComponentMatcherTest {

    @Test
    void matchesByComponentNameAliasAndBackendKeyword() {
        ComponentMatcher matcher = new ComponentMatcher(List.of(
                new ComponentKnowledgeConfig(
                        "spring-websocket",
                        "Spring WebSocket",
                        List.of("spring ws", "websocket spring"),
                        List.of("docs.spring.io"),
                        List.of("https://docs.spring.io/spring-framework/reference/web/websocket.html"),
                        List.of("spring-websocket"),
                        List.of("websocket", "stomp"),
                        100,
                        true)));

        assertThat(matcher.match("Spring WS 认证怎么做", null, List.of()))
                .extracting(ComponentKnowledgeConfig::componentCode)
                .containsExactly("spring-websocket");
    }

    @Test
    void returnsAmbiguousWhenMultipleComponentsMatch() {
        ComponentMatcher matcher = new ComponentMatcher(List.of(
                config("spring-framework", "Spring Framework", List.of("spring")),
                config("spring-boot", "Spring Boot", List.of("spring"))));

        assertThat(matcher.match("spring 配置", null, List.of())).hasSize(2);
    }
}
```

- [ ] **Step 2: 写 JDBC 配置读取失败测试**

验证只读取 `enabled=true`，并解析 JSON 数组字段。

- [ ] **Step 3: 实现 `ComponentKnowledgeConfig`**

```java
public record ComponentKnowledgeConfig(
        String componentCode,
        String componentName,
        List<String> aliases,
        List<String> officialDomains,
        List<String> officialEntryUrls,
        List<String> stackOverflowTags,
        List<String> backendKeywords,
        int priority,
        boolean enabled) {
}
```

- [ ] **Step 4: 实现内置种子写入**

在 `JdbcToolSchemaInitializer.initializeDefaultComponentConfigs()` 中使用 `insert ... on conflict (component_code) do nothing` 初始化：

- `spring-boot`
- `spring-framework`
- `spring-websocket`
- `spring-security`
- `mybatis`
- `mybatis-plus`
- `ruoyi`
- `sa-token`
- `redisson`
- `redis`
- `postgresql`
- `pgvector`
- `nacos`
- `kafka`
- `rabbitmq`
- `openai-api`
- `langchain4j`

每条种子至少包含一个官方域名和一个后端关键词。例如 `spring-websocket`：

```json
{
  "componentCode": "spring-websocket",
  "componentName": "Spring WebSocket",
  "aliases": ["spring websocket", "spring ws", "stomp websocket"],
  "officialDomains": ["docs.spring.io"],
  "officialEntryUrls": ["https://docs.spring.io/spring-framework/reference/web/websocket.html"],
  "stackOverflowTags": ["spring-websocket", "spring", "websocket"],
  "backendKeywords": ["websocket", "stomp", "handshake", "TextWebSocketHandler"],
  "priority": 100,
  "enabled": true
}
```

- [ ] **Step 5: 运行任务 3 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ComponentMatcherTest,JdbcComponentKnowledgeConfigRepositoryTest' test
```

Expected: PASS。

## 任务 4：Tavily Search 客户端

**Files:**
- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchClient.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/DefaultTavilySearchClient.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchRequest.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchResponse.java`
- Create: `src/main/java/com/example/demoscope/common/tavily/TavilySearchResult.java`
- Test: `src/test/java/com/example/demoscope/common/tavily/DefaultTavilySearchClientTest.java`

- [ ] **Step 1: 写 Tavily 请求构造失败测试**

使用 JDK `HttpServer` 捕获请求，验证：

- URL 为 `/search`。
- Header 为 `Authorization: Bearer test-tavily-key`。
- 请求 body 包含 `query`、`include_domains`、`max_results`。
- 捕获的原始请求 JSON 不包含 API key。

```java
@Test
void postsSearchRequestWithBearerAuthAndDomainFilter() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/search", exchange -> {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = """
                {"results":[{"title":"Spring Docs","url":"https://docs.spring.io/a","content":"doc","score":0.9}]}
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    });
    server.start();
    try {
        DefaultTavilySearchClient client = new DefaultTavilySearchClient(
                "test-tavily-key",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(5));

        TavilySearchResponse response = client.search(new TavilySearchRequest(
                "spring websocket auth",
                List.of("docs.spring.io"),
                3));

        assertThat(authorization.get()).isEqualTo("Bearer test-tavily-key");
        assertThat(requestBody.get()).contains("\"query\":\"spring websocket auth\"");
        assertThat(requestBody.get()).contains("\"include_domains\":[\"docs.spring.io\"]");
        assertThat(requestBody.get()).contains("\"max_results\":3");
        assertThat(requestBody.get()).doesNotContain("test-tavily-key");
        assertThat(response.results()).hasSize(1);
    } finally {
        server.stop(0);
    }
}
```

- [ ] **Step 2: 实现 Tavily DTO 与客户端**

`TavilySearchRequest`：

```java
public record TavilySearchRequest(
        String query,
        List<String> includeDomains,
        int maxResults) {
}
```

`DefaultTavilySearchClient.search(...)` 使用 JDK `HttpClient`，请求 body 结构：

```json
{
  "query": "spring websocket auth",
  "include_domains": ["docs.spring.io"],
  "max_results": 3
}
```

如果 API key 为空，抛出 `IllegalStateException("Tavily API key is not configured")`，由工具层转为 `FAILED`。

- [ ] **Step 3: 运行任务 4 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=DefaultTavilySearchClientTest' test
```

Expected: PASS。

## 任务 5：组件证据搜索工具

**Files:**
- Create: `src/main/java/com/example/demoscope/biz/evidence/ComponentEvidenceTool.java`
- Create: `src/main/java/com/example/demoscope/biz/evidence/EvidenceSearchRecorder.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcEvidenceSearchRecorder.java`
- Test: `src/test/java/com/example/demoscope/biz/evidence/ComponentEvidenceToolTest.java`
- Test: `src/test/java/com/example/demoscope/common/jdbc/JdbcEvidenceSearchRecorderTest.java`

- [ ] **Step 1: 写命中官方文档与 Stack Overflow 的失败测试**

```java
@Test
void searchesOfficialDocsAndStackOverflowForMatchedComponent() {
    FakeTavilySearchClient tavily = new FakeTavilySearchClient(Map.of(
            "docs.spring.io", List.of(result("Official", "https://docs.spring.io/spring", "official", 0.9)),
            "stackoverflow.com", List.of(result("SO", "https://stackoverflow.com/questions/1", "community", 0.7))));
    ComponentEvidenceTool tool = new ComponentEvidenceTool(
            () -> List.of(springWebSocketConfig()),
            tavily,
            new NoopEvidenceSearchRecorder(),
            new ObjectMapper(),
            5);

    ToolCallResult result = tool.execute(arguments("""
            {"question":"Spring WebSocket 认证怎么做","componentHint":"Spring WebSocket","keywords":["auth"],"maxResults":5}
            """), chatContext());

    assertThat(result.status()).isEqualTo(ToolCallStatus.SUCCESS);
    assertThat(result.evidenceCount()).isEqualTo(2);
    assertThat(result.payload().toString()).contains("OFFICIAL_DOC");
    assertThat(result.payload().toString()).contains("STACK_OVERFLOW");
}
```

- [ ] **Step 2: 写组件不明确失败测试**

当 `ComponentMatcher` 返回多个组件时，工具返回 `NEED_CLARIFICATION`。

- [ ] **Step 3: 实现工具定义 schema**

`definition()` 返回 OpenAI function schema：

```java
new FunctionToolDefinition(
        "search_component_evidence",
        "Search official component documentation and Stack Overflow evidence before answering backend technical questions.",
        Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of("type", "string"),
                        "componentHint", Map.of("type", "string"),
                        "keywords", Map.of("type", "array", "items", Map.of("type", "string")),
                        "maxResults", Map.of("type", "integer", "minimum", 1, "maximum", 10)),
                "required", List.of("question")))
```

- [ ] **Step 4: 实现搜索逻辑**

执行逻辑：

1. 解析 `question`、`componentHint`、`keywords`、`maxResults`。
2. 读取启用组件配置。
3. 使用 `ComponentMatcher` 匹配组件。
4. 无匹配或多匹配时返回 `NEED_CLARIFICATION`。
5. Tavily 搜官方域名。
6. Tavily 搜 `stackoverflow.com`。
7. URL 去重，官方证据排在社区证据前。
8. 写 `ai_evidence_search_records`。
9. 返回 `ToolCallResult.success(...)`。

- [ ] **Step 5: 运行任务 5 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ComponentEvidenceToolTest,JdbcEvidenceSearchRecorderTest' test
```

Expected: PASS。

## 任务 6：RAG 知识库查询工具

**Files:**
- Create: `src/main/java/com/example/demoscope/biz/rag/RagKnowledgeTool.java`
- Test: `src/test/java/com/example/demoscope/biz/rag/RagKnowledgeToolTest.java`

- [ ] **Step 1: 写 RAG 查询失败测试**

```java
@Test
void returnsKnowledgeChunksAsToolEvidence() {
    EmbeddingClient embeddingClient = text -> new float[] {0.1f, 0.2f};
    KnowledgeRetriever retriever = query -> List.of(
            new KnowledgeChunk("doc-1", "HashMap resize evidence"),
            new KnowledgeChunk("doc-2", "ConcurrentHashMap evidence"));
    RagKnowledgeTool tool = new RagKnowledgeTool(embeddingClient, retriever, new ObjectMapper(), 5);

    ToolCallResult result = tool.execute(arguments("""
            {"query":"HashMap 扩容","topK":2,"domain":"interview"}
            """), interviewContext());

    assertThat(result.status()).isEqualTo(ToolCallStatus.SUCCESS);
    assertThat(result.evidenceCount()).isEqualTo(2);
    assertThat(result.payload().toString()).contains("doc-1");
}
```

- [ ] **Step 2: 实现工具 schema**

工具名为 `query_rag_knowledge`，参数包含 `query`、`topK`、`domain`。

- [ ] **Step 3: 实现查询逻辑**

1. `query` 为空时返回 `NEED_CLARIFICATION`。
2. `topK` 小于 1 时使用 5，大于配置上限时裁剪。
3. 用 `EmbeddingClient.embed(query)` 生成向量。
4. 用 `KnowledgeRetriever.retrieve(new SemanticQuery(query, embedding))` 查询。
5. 返回 `source` 与 `content`，每个 content 限制到 800 字符。
6. 捕获运行时异常并返回 `FAILED`。

- [ ] **Step 4: 运行任务 6 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RagKnowledgeToolTest' test
```

Expected: PASS。

## 任务 7：数据库只读白名单查询工具

**Files:**
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryConfig.java`
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryConfigRepository.java`
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryExecutor.java`
- Create: `src/main/java/com/example/demoscope/biz/database/DatabaseQueryTool.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcDatabaseQueryConfigRepository.java`
- Create: `src/main/java/com/example/demoscope/common/jdbc/JdbcDatabaseQueryExecutor.java`
- Test: `src/test/java/com/example/demoscope/biz/database/DatabaseQueryToolTest.java`
- Test: `src/test/java/com/example/demoscope/common/jdbc/JdbcDatabaseQueryExecutorTest.java`

- [ ] **Step 1: 写拒绝未知字段失败测试**

```java
@Test
void rejectsFieldsOutsideWhitelist() {
    DatabaseQueryTool tool = new DatabaseQueryTool(
            code -> Optional.of(config("interview_session_summary")),
            new FakeDatabaseQueryExecutor(),
            new ObjectMapper(),
            20);

    ToolCallResult result = tool.execute(arguments("""
            {"queryCode":"interview_session_summary","fields":["password"],"filters":{"status":"FINISHED"},"limit":10}
            """), chatContext());

    assertThat(result.status()).isEqualTo(ToolCallStatus.FAILED);
    assertThat(result.errorMessage()).contains("field is not allowed");
}
```

- [ ] **Step 2: 写自动追加用户隔离条件测试**

```java
@Test
void appendsUserScopeFilterFromContext() {
    CapturingDatabaseQueryExecutor executor = new CapturingDatabaseQueryExecutor();
    DatabaseQueryTool tool = new DatabaseQueryTool(
            code -> Optional.of(configWithUserScope("interview_session_summary", "user_id")),
            executor,
            new ObjectMapper(),
            20);

    ToolCallResult result = tool.execute(arguments("""
            {"queryCode":"interview_session_summary","fields":["interview_id","status"],"filters":{"status":"FINISHED"},"limit":50}
            """), new ToolCallContext("user-1", "conv-1", "CHAT", null, ToolCallEventSink.noop()));

    assertThat(result.status()).isEqualTo(ToolCallStatus.SUCCESS);
    assertThat(executor.filters()).containsEntry("user_id", "user-1");
    assertThat(executor.limit()).isEqualTo(20);
}
```

- [ ] **Step 3: 写 SQL 生成测试**

`JdbcDatabaseQueryExecutor` 生成参数绑定 SQL：

```sql
select interview_id, status
from interview_sessions
where status = ? and user_id = ?
order by created_at desc
limit ?
```

测试验证 `JdbcOperations.queryForList(sql, args...)` 参数顺序为 `FINISHED`、`user-1`、`20`。

- [ ] **Step 4: 实现白名单配置记录**

```java
public record DatabaseQueryConfig(
        String queryCode,
        String businessType,
        String description,
        String tableName,
        List<String> allowedFields,
        List<String> allowedFilterFields,
        List<String> requiredFilterFields,
        String userScopeField,
        String defaultOrderBy,
        int maxLimit,
        boolean enabled) {
}
```

- [ ] **Step 5: 实现工具安全规则**

1. `queryCode` 不存在：`FAILED`。
2. `fields` 为空：使用配置的 `allowedFields` 前 10 个字段。
3. `fields` 有未知字段：`FAILED`。
4. `filters` 有未知字段：`FAILED`。
5. `requiredFilterFields` 缺失：`NEED_CLARIFICATION`。
6. `userScopeField` 存在时，从 `ToolCallContext.userId()` 自动覆盖同名 filter。
7. `limit` 不得超过 `min(request.limit, config.maxLimit, globalDefaultLimit)`。
8. SQL 只由后端生成，工具参数中出现 `sql` 字段时直接 `FAILED`。

- [ ] **Step 6: 运行任务 7 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=DatabaseQueryToolTest,JdbcDatabaseQueryExecutorTest' test
```

Expected: PASS。

## 任务 8：OpenAI-compatible Tool Call 模型客户端

**Files:**
- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiChatMessage.java`
- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiChatCompletion.java`
- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiToolCall.java`
- Create: `src/main/java/com/example/demoscope/common/llm/OpenAiChatCompletionClient.java`
- Modify: `src/main/java/com/example/demoscope/common/llm/AgentScopeChatTextModel.java`
- Test: `src/test/java/com/example/demoscope/common/llm/AgentScopeChatTextModelToolCallTest.java`

- [ ] **Step 1: 写 tool call 请求解析失败测试**

本地 `HttpServer` 第一次返回 tool call，测试客户端能解析：

```json
{
  "id": "chatcmpl-tool-1",
  "choices": [
    {
      "message": {
        "role": "assistant",
        "tool_calls": [
          {
            "id": "call-1",
            "type": "function",
            "function": {
              "name": "search_component_evidence",
              "arguments": "{\"question\":\"Spring WebSocket 认证\"}"
            }
          }
        ]
      }
    }
  ],
  "usage": {"prompt_tokens": 10, "completion_tokens": 4, "total_tokens": 14}
}
```

断言：

- request body 包含 `"tools"`。
- request body 包含 `"tool_choice":"auto"`。
- response 中 `toolCalls().get(0).functionName()` 是 `search_component_evidence`。

- [ ] **Step 2: 定义 DTO**

```java
public record OpenAiToolCall(
        String id,
        String functionName,
        String argumentsJson) {
}

public record OpenAiChatMessage(
        String role,
        String content,
        List<OpenAiToolCall> toolCalls,
        String toolCallId,
        String name) {
}

public record OpenAiChatCompletion(
        String responseId,
        String content,
        List<OpenAiToolCall> toolCalls) {
}
```

- [ ] **Step 3: 扩展模型客户端接口**

```java
public interface OpenAiChatCompletionClient {
    OpenAiChatCompletion complete(
            List<OpenAiChatMessage> messages,
            List<FunctionToolDefinition> tools);

    void completeStream(
            List<OpenAiChatMessage> messages,
            Consumer<String> onDelta);
}
```

`completeStream` 第一版只负责最终答案流式输出，不解析流式 tool call；tool call 轮次由 `complete(...)` 非流式完成。

- [ ] **Step 4: 修改 `AgentScopeChatTextModel`**

把现有 `generate`、`generateStream` 改为复用一个私有 `sendCompletion(ObjectNode requestBody, boolean streaming, Consumer<String> onDelta)`，保持现有 token usage 记录不丢。

tool 请求 body 结构：

```json
{
  "model": "test-model",
  "stream": false,
  "messages": [],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "search_component_evidence",
        "description": "...",
        "parameters": {}
      }
    }
  ],
  "tool_choice": "auto"
}
```

- [ ] **Step 5: 运行任务 8 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgentScopeChatTextModelTest,AgentScopeChatTextModelToolCallTest' test
```

Expected: PASS。

## 任务 9：ToolCallingModelService 编排闭环

**Files:**
- Create: `src/main/java/com/example/demoscope/biz/tool/ToolCallingModelService.java`
- Create: `src/main/java/com/example/demoscope/biz/tool/ToolCallingPromptPolicy.java`
- Test: `src/test/java/com/example/demoscope/biz/tool/ToolCallingModelServiceTest.java`

- [ ] **Step 1: 写模型调用工具闭环失败测试**

测试流程：

1. Fake model 第一次返回 `OpenAiToolCall("call-1", "query_rag_knowledge", "{\"query\":\"HashMap\"}")`。
2. Fake tool 返回 `ToolCallResult.success(...)`。
3. Fake model 第二次收到 `role=tool` 消息后返回最终 content。
4. 断言最终答案来自第二次模型响应。
5. 断言事件顺序为 started -> finished。

- [ ] **Step 2: 写超过最大轮数失败测试**

当模型连续返回 tool call 超过 `agentscope.tools.max-rounds`，服务返回固定文本：

```text
当前无法在限定工具调用轮次内确认答案，请补充组件、业务范围或查询条件。
```

- [ ] **Step 3: 实现 prompt policy**

系统规则追加到原 system prompt：

```text
你可以调用后端工具获取证据。
技术事实、组件用法、系统数据和知识库内容必须先调用工具确认。
没有工具证据时不要猜测结论。
组件不明确、证据不足或查询条件缺失时，追问用户。
最终回答必须列出来源 URL、知识库来源或数据库查询依据。
```

- [ ] **Step 4: 实现工具循环**

伪代码：

```java
List<OpenAiChatMessage> messages = new ArrayList<>();
messages.add(OpenAiChatMessage.system(promptPolicy.apply(systemPrompt)));
messages.add(OpenAiChatMessage.user(userPrompt));
for (int round = 0; round < maxRounds; round++) {
    OpenAiChatCompletion completion = model.complete(messages, registry.definitions());
    if (completion.toolCalls().isEmpty()) {
        return completion.content();
    }
    messages.add(OpenAiChatMessage.assistantToolCalls(completion.toolCalls()));
    for (OpenAiToolCall call : completion.toolCalls()) {
        FunctionTool tool = registry.findRequired(call.functionName());
        context.eventSink().started(tool.name(), "正在调用 " + tool.name());
        ToolCallResult result = executeAndRecord(tool, call, context);
        context.eventSink().finished(tool.name(), result.status(), result.evidenceCount());
        messages.add(OpenAiChatMessage.tool(call.id(), tool.name(), objectMapper.writeValueAsString(result)));
    }
}
return "当前无法在限定工具调用轮次内确认答案，请补充组件、业务范围或查询条件。";
```

- [ ] **Step 5: 实现流式最终回答**

`generateStream(...)` 先用非流式 `complete(...)` 完成 tool call 轮次；当最后一轮不再需要工具时，用包含 tool result 的 messages 调用 `model.completeStream(messages, onDelta)`，并把 delta 拼接为返回值。

- [ ] **Step 6: 运行任务 9 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ToolCallingModelServiceTest' test
```

Expected: PASS。

## 任务 10：Spring 配置装配

**Files:**
- Create: `src/main/java/com/example/demoscope/config/tool/ToolCallingConfig.java`
- Modify: `src/main/java/com/example/demoscope/config/memory/AgentMemoryConfig.java`
- Test: `src/test/java/com/example/demoscope/config/tool/ToolCallingConfigTest.java`

- [ ] **Step 1: 写无 Tavily key 不启动失败的配置测试**

`ApplicationContextRunner` 设置 `agentscope.tools.enabled=true` 但不配置 `agentscope.tavily.api-key`，断言 context 启动成功，`ComponentEvidenceTool` 存在，执行工具时返回 `FAILED`。

- [ ] **Step 2: 写 JdbcOperations 存在时初始化 schema 测试**

验证 `JdbcToolSchemaInitializer.initializeSchema()` 被调用。

- [ ] **Step 3: 实现配置 bean**

`ToolCallingConfig` 提供：

- `JdbcToolSchemaInitializer`
- `ToolCallRecorder`
- `ComponentKnowledgeConfigRepository`
- `DatabaseQueryConfigRepository`
- `DatabaseQueryExecutor`
- `TavilySearchClient`
- `ComponentEvidenceTool`
- `RagKnowledgeTool`
- `DatabaseQueryTool`
- `FunctionToolRegistry`
- `ToolCallingModelService`

条件：

- `agentscope.tools.enabled=false` 时注册空工具注册表，chat/interview 仍可启动。
- 无 `JdbcOperations` 时配置读取仓库返回空列表，记录器为 no-op。
- 无 Tavily key 时 Tavily 客户端对象可创建，但 `search(...)` 抛出配置错误，由工具转为 `FAILED`。

- [ ] **Step 4: 运行任务 10 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ToolCallingConfigTest' test
```

Expected: PASS。

## 任务 11：chat 接入 Function Calling

**Files:**
- Modify: `src/main/java/com/example/demoscope/service/chat/OpenAiAgentChatService.java`
- Modify: `src/main/java/com/example/demoscope/service/chat/StreamingAgentChatService.java`
- Test: `src/test/java/com/example/demoscope/service/chat/OpenAiAgentChatServiceToolCallingTest.java`
- Test: `src/test/java/com/example/demoscope/service/chat/StreamingAgentChatServiceToolCallingTest.java`

- [ ] **Step 1: 写同步 chat 工具上下文测试**

断言 `ToolCallingModelService.generate(...)` 收到：

- `ToolCallContext.userId=user-42`
- `conversationId=conversation-a`
- `businessType=CHAT`
- `businessId=null`

- [ ] **Step 2: 写流式 chat 工具事件测试**

Fake `ToolCallingModelService.generateStream(...)` 调用 event sink：

```java
context.eventSink().started("search_component_evidence", "正在检索 Spring WebSocket 官方文档和 Stack Overflow");
context.eventSink().finished("search_component_evidence", ToolCallStatus.SUCCESS, 2);
onDelta.accept("answer");
```

断言服务返回完整 answer，并把 delta 交给外层消费者。

- [ ] **Step 3: 修改 `OpenAiAgentChatService`**

把：

```java
String answer = chatTextModel.generate(systemPrompt, modelPrompt);
```

改为：

```java
String answer = toolCallingModelService.generate(
        systemPrompt,
        modelPrompt,
        new ToolCallContext(userId, conversationId, "CHAT", null, ToolCallEventSink.noop()));
```

- [ ] **Step 4: 修改 `StreamingAgentChatService`**

增加重载：

```java
public String chat(
        String userId,
        String conversationId,
        String message,
        Consumer<String> onDelta,
        ToolCallEventSink eventSink)
```

原方法保留，内部传 `ToolCallEventSink.noop()`。

- [ ] **Step 5: 运行任务 11 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=OpenAiAgentChatServiceMemoryTest,OpenAiAgentChatServiceToolCallingTest,StreamingAgentChatServiceTest,StreamingAgentChatServiceToolCallingTest' test
```

Expected: PASS。

## 任务 12：interview 接入 Function Calling

**Files:**
- Modify: `src/main/java/com/example/demoscope/common/llm/InterviewAiJsonClient.java`
- Modify: `src/main/java/com/example/demoscope/service/interview/InterviewService.java`
- Modify: `src/main/java/com/example/demoscope/service/interview/InterviewStreamingFacade.java`
- Test: `src/test/java/com/example/demoscope/common/llm/InterviewAiJsonClientToolCallingTest.java`
- Test: `src/test/java/com/example/demoscope/service/interview/InterviewServiceToolCallingContextTest.java`

- [ ] **Step 1: 写 InterviewAiJsonClient 工具调用测试**

Fake `ToolCallingModelService.generate(...)` 返回 JSON：

```json
{"question":"HashMap 如何扩容？","skillTags":["java"],"evidenceIds":["doc-1"]}
```

断言 `InterviewAiJsonClient.call(...)` 仍能解析目标类型。

- [ ] **Step 2: 写 interview 上下文测试**

创建 interview 后生成题目时，断言工具上下文为：

- `businessType=INTERVIEW`
- `userId` 为当前用户
- `businessId` 为 interviewId

- [ ] **Step 3: 修改 `InterviewAiJsonClient`**

构造函数改为接收 `ToolCallingModelService`，保留 `ObjectMapper`。`generate(...)` 中：

```java
ToolCallContext context = ToolCallContextHolder.current();
if ("UNKNOWN".equals(context.businessType())) {
    context = new ToolCallContext(null, null, "INTERVIEW", null, ToolCallEventSink.noop());
}
return toolCallingModelService.generate(systemPrompt, userPrompt, context);
```

- [ ] **Step 4: 修改 `InterviewService.withInterviewTokenUsageContext`**

同时设置 `TokenUsageContextHolder` 和 `ToolCallContextHolder`，事件 sink 从当前上下文继承：

```java
ToolCallContext toolContext = new ToolCallContext(
        snapshot.session().userId(),
        null,
        "INTERVIEW",
        snapshot.session().id().toString(),
        ToolCallContextHolder.current().eventSink());
```

- [ ] **Step 5: 修改 `InterviewStreamingFacade`**

增加带 `ToolCallEventSink` 的重载方法，WebSocket 调用这些重载，HTTP 同步接口仍走 no-op。

- [ ] **Step 6: 运行任务 12 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewAiJsonClientTest,InterviewAiJsonClientToolCallingTest,InterviewServiceCreationTest,InterviewServiceToolCallingContextTest' test
```

Expected: PASS。

## 任务 13：WebSocket tool call 状态事件

**Files:**
- Modify: `src/main/java/com/example/demoscope/controller/stream/StreamMessage.java`
- Modify: `src/main/java/com/example/demoscope/controller/chat/ChatWebSocketHandler.java`
- Modify: `src/main/java/com/example/demoscope/controller/interview/InterviewWebSocketHandler.java`
- Test: `src/test/java/com/example/demoscope/controller/stream/StreamMessageToolCallTest.java`
- Test: `src/test/java/com/example/demoscope/controller/chat/ChatWebSocketHandlerToolCallTest.java`
- Test: `src/test/java/com/example/demoscope/controller/interview/InterviewWebSocketHandlerToolCallTest.java`

- [ ] **Step 1: 写 StreamMessage 测试**

```java
@Test
void serializesToolCallStartedAndFinished() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();

    assertThat(objectMapper.writeValueAsString(
            StreamMessage.toolCallStarted("search_component_evidence", "正在检索 Spring WebSocket 官方文档和 Stack Overflow")))
            .contains("\"type\":\"tool_call_started\"")
            .contains("\"toolName\":\"search_component_evidence\"");

    assertThat(objectMapper.writeValueAsString(
            StreamMessage.toolCallFinished("search_component_evidence", "SUCCESS", 2)))
            .contains("\"type\":\"tool_call_finished\"")
            .contains("\"evidenceCount\":2");
}
```

- [ ] **Step 2: 修改 StreamMessage**

增加字段：

```java
String toolName,
String status,
Integer evidenceCount,
String finishReason
```

增加工厂方法：

```java
public static StreamMessage toolCallStarted(String toolName, String message)
public static StreamMessage toolCallFinished(String toolName, String status, int evidenceCount)
public static StreamMessage done(String finishReason)
```

保持原 `done()` 方法不变，调用 `done(null)`。

- [ ] **Step 3: 修改 ChatWebSocketHandler**

构造 event sink：

```java
ToolCallEventSink eventSink = new ToolCallEventSink() {
    @Override
    public void started(String toolName, String message) {
        sendUnchecked(session, StreamMessage.toolCallStarted(toolName, message));
    }

    @Override
    public void finished(String toolName, ToolCallStatus status, int evidenceCount) {
        sendUnchecked(session, StreamMessage.toolCallFinished(toolName, status.name(), evidenceCount));
    }
};
```

调用新的 streaming service 重载。

- [ ] **Step 4: 修改 InterviewWebSocketHandler**

与 chat 相同，调用 `InterviewStreamingFacade` 带 event sink 的重载。事件应在 `start` 后、`delta` 前发送。

- [ ] **Step 5: 运行任务 13 测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=StreamMessageToolCallTest,ChatWebSocketHandlerToolCallTest,InterviewWebSocketHandlerToolCallTest' test
```

Expected: PASS。

## 任务 14：回归验证与闭环检查

**Files:**
- Modify only if previous tests reveal a compile or behavior issue.

- [ ] **Step 1: 运行工具相关测试集合**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=FunctionToolRegistryTest,ToolCallContextHolderTest,JdbcToolSchemaInitializerTest,JdbcToolCallRecorderTest,ComponentMatcherTest,JdbcComponentKnowledgeConfigRepositoryTest,DefaultTavilySearchClientTest,ComponentEvidenceToolTest,JdbcEvidenceSearchRecorderTest,RagKnowledgeToolTest,DatabaseQueryToolTest,JdbcDatabaseQueryExecutorTest,AgentScopeChatTextModelToolCallTest,ToolCallingModelServiceTest,ToolCallingConfigTest' test
```

Expected: PASS。

- [ ] **Step 2: 运行 chat/interview 接入测试集合**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=OpenAiAgentChatServiceMemoryTest,OpenAiAgentChatServiceToolCallingTest,StreamingAgentChatServiceTest,StreamingAgentChatServiceToolCallingTest,InterviewAiJsonClientTest,InterviewAiJsonClientToolCallingTest,InterviewServiceCreationTest,InterviewServiceToolCallingContextTest,StreamMessageToolCallTest,ChatWebSocketHandlerTest,ChatWebSocketHandlerToolCallTest,InterviewWebSocketHandlerTest,InterviewWebSocketHandlerToolCallTest' test
```

Expected: PASS。

- [ ] **Step 3: 运行完整测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B test
```

Expected: PASS。

- [ ] **Step 4: 人工检查安全闭环**

检查项：

- `rg -n "api-key|Authorization|password" src/main/java/com/example/demoscope/common/tool src/main/java/com/example/demoscope/common/jdbc src/main/java/com/example/demoscope/biz` 不应显示把敏感值写入记录表的代码。
- `query_database` 工具参数中出现 `sql` 字段时测试必须失败。
- `search_component_evidence` 无官方文档结果时，payload 不能把 Stack Overflow 标记为官方证据。
- `ToolCallingModelService` 超过工具调用轮数时返回追问文本，不输出猜测答案。
- chat 和 interview 的 WebSocket 都能发送 `tool_call_started`、`tool_call_finished`、`delta`、`done`。

## 自检结果

- 设计文档中的三类工具都有对应任务。
- chat 和 interview 都有业务接入任务。
- Tavily、RAG、数据库白名单、tool call 记录、WebSocket 事件都有测试任务。
- 数据库查询没有模型直写 SQL 的路径。
- 计划没有管理后台和配置 CRUD，符合当前范围。
- 执行计划保留现有 HTTP 与 WebSocket 接口，不要求一次性替换外部协议。
