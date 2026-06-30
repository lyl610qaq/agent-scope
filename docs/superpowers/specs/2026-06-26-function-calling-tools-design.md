# Function Calling 工具体系设计

## 目标

为 chat 和 interview 两条模型链路增加统一的 Function Calling 工具体系。模型可以根据用户问题调用工具，但工具的执行、校验、记录和证据返回全部由后端控制。

本设计覆盖三类工具：

- `search_component_evidence`：根据后端组件配置，通过 Tavily 搜索官方文档和 Stack Overflow。
- `query_rag_knowledge`：查询现有 RAG 知识库，复用本地知识库、pgvector、`KnowledgeRetriever`、`EmbeddingClient` 等能力。
- `query_database`：查询业务数据库，只允许只读白名单查询，模型不能直接生成或执行 SQL。

核心原则是：技术类回答必须先拿证据；没有证据就追问；不知道就明确说明不知道，不猜测。

## 范围

本设计包含：

- chat 和 interview 都接入 Function Calling。
- 使用 Tavily 作为外部搜索通道。
- 建立组件知识配置表，后端相关组件知识从数据库配置中读取。
- 建立数据库查询白名单配置表。
- 统一记录每次 tool call 的原始请求、原始响应、耗时、状态和业务归属。
- WebSocket 流式输出 tool call 状态事件和最终文字流。

本设计不包含：

- 管理后台页面。
- 组件配置和数据库查询配置的增删改查接口。
- 让模型直接访问数据库、Tavily API key 或底层 SQL。
- 用 Stack Overflow 替代官方文档作为权威结论来源。

## 总体架构

新增统一工具层，建议包结构如下：

- `common/tavily`：Tavily HTTP 客户端、请求/响应 DTO、错误封装。
- `common/tool`：通用 tool call 协议、tool 执行结果、tool 调用记录接口。
- `common/jdbc`：tool 记录落库、配置表读取、白名单 SQL 执行。
- `biz/tool`：工具注册表和编排逻辑。
- `biz/evidence`：组件识别、官方文档/Stack Overflow 搜索、证据筛选。
- `biz/rag`：RAG 查询工具适配，复用现有检索器。
- `biz/database`：数据库白名单查询工具。

chat 和 interview 不直接依赖 Tavily、JDBC 白名单执行器或 RAG 细节，只依赖统一的 `FunctionToolRegistry` 或 `ToolCallingModelService`。

## 模型调用闭环

chat 和 interview 的模型调用流程统一为：

1. 后端构造模型请求，附带可调用工具定义。
2. 模型根据用户问题决定是否发起 tool call。
3. 后端收到 tool call 后，通过 `FunctionToolRegistry` 找到对应工具。
4. 工具执行前做参数校验、权限校验、配置校验。
5. 工具执行后记录 `tool_call_records`。
6. 后端把结构化 tool result 追加回模型上下文。
7. 模型基于 tool result 生成最终回答。
8. 后端做证据闸门检查，技术类回答必须包含来源或数据依据。

如果模型没有调用工具，但问题明显是后端技术事实、组件用法、数据库数据或知识库内容问题，后端应让模型补充工具调用，或返回追问，而不是直接输出猜测答案。

## 工具一：组件证据搜索

工具名：`search_component_evidence`

用途：根据用户问题检索组件官方文档和 Stack Overflow 证据。

输入参数：

```json
{
  "question": "Spring WebSocket 怎么配置认证",
  "componentHint": "Spring WebSocket",
  "keywords": ["authentication", "WebSocket", "Spring"],
  "maxResults": 5
}
```

执行流程：

1. 从 `ai_component_knowledge_config` 读取启用的组件配置。
2. 使用组件名和别名匹配 `componentHint`、`question`、`keywords`。
3. 如果命中多个相近组件，返回 `NEED_CLARIFICATION`，要求模型追问用户。
4. 对官方文档域名执行 Tavily 搜索，例如限制在配置的官方域名内。
5. 对 Stack Overflow 执行 Tavily 搜索，例如限制 `stackoverflow.com` 并叠加配置的 tag/关键词。
6. 合并、去重、排序证据。
7. 返回结构化证据列表。
8. 原始 Tavily 请求和响应写入 `ai_evidence_search_records`。

输出示例：

```json
{
  "status": "OK",
  "component": "Spring WebSocket",
  "evidence": [
    {
      "type": "OFFICIAL_DOC",
      "title": "Spring Framework WebSocket Documentation",
      "url": "https://docs.spring.io/...",
      "snippet": "官方文档摘要",
      "score": 0.92
    },
    {
      "type": "STACK_OVERFLOW",
      "title": "Stack Overflow question title",
      "url": "https://stackoverflow.com/questions/...",
      "snippet": "社区问答摘要",
      "score": 0.71
    }
  ]
}
```

回答规则：

- 官方文档是主证据。
- Stack Overflow 只能作为补充证据，回答中要明确它是社区经验。
- 如果官方文档没有证据，不能把 Stack Overflow 当成官方结论。
- 证据不足时，模型必须说明证据不足并追问。

## 工具二：RAG 知识库查询

工具名：`query_rag_knowledge`

用途：查询项目已有知识库，用于补充本地业务知识、面试知识、文档片段和长期沉淀内容。

输入参数：

```json
{
  "query": "Java HashMap 扩容机制",
  "topK": 5,
  "domain": "interview"
}
```

执行流程：

1. 使用 `EmbeddingClient` 生成查询向量。
2. 调用现有 `KnowledgeRetriever` 或 `InterviewEvidenceProvider`。
3. 对返回的 `KnowledgeChunk` 做数量限制和内容长度限制。
4. 返回 chunk id、内容摘要、来源信息。
5. 写入 `tool_call_records`。

RAG 工具返回的是内部知识证据，不替代官方文档。对于框架、组件、API 版本行为等问题，模型仍应优先调用 `search_component_evidence`。

## 工具三：数据库白名单查询

工具名：`query_database`

用途：让模型查询业务数据，但只能访问配置过的只读白名单。

输入参数：

```json
{
  "queryCode": "interview_session_summary",
  "fields": ["interview_id", "status", "score"],
  "filters": {
    "user_id": "10001",
    "status": "FINISHED"
  },
  "limit": 20
}
```

安全规则：

- 模型不能提交 SQL。
- 后端只接受 `queryCode`、`fields`、`filters`、`limit`。
- `queryCode` 必须存在于 `ai_database_query_config`。
- `fields` 必须在允许字段中。
- `filters` 必须在允许过滤字段中。
- 强制 `SELECT`。
- 强制最大 `limit`。
- 如果配置了用户隔离字段，后端必须自动追加当前 `userId` 条件。
- 不支持 insert、update、delete、ddl、函数调用、多语句、子查询自由拼接。

SQL 由后端根据白名单配置生成，并使用参数绑定执行。

## 数据库表设计

### ai_component_knowledge_config

组件知识配置表。

核心字段：

- `id`：主键。
- `component_code`：组件编码，例如 `spring-websocket`。
- `component_name`：组件名称，例如 `Spring WebSocket`。
- `aliases_json`：别名 JSON，例如 `["spring websocket", "Spring WS"]`。
- `official_domains_json`：官方域名 JSON，例如 `["docs.spring.io"]`。
- `official_entry_urls_json`：官方文档入口 JSON。
- `stackoverflow_tags_json`：Stack Overflow tag JSON。
- `backend_keywords_json`：后端关键词 JSON，用于后端知识命中。
- `priority`：匹配优先级。
- `enabled`：是否启用。
- `created_at`、`updated_at`。

初始化数据应覆盖常见后端组件，例如 Spring Boot、Spring Framework、Spring WebSocket、Spring Security、MyBatis、MyBatis-Plus、RuoYi、Sa-Token、Redisson、Redis、PostgreSQL、pgvector、Nacos、Kafka、RabbitMQ、OpenAI API、LangChain4j。

### ai_database_query_config

数据库查询白名单配置表。

核心字段：

- `id`：主键。
- `query_code`：查询编码。
- `business_type`：业务类型，例如 `CHAT`、`INTERVIEW`、`COMMON`。
- `description`：查询说明，供模型理解用途。
- `table_name`：允许查询的表名。
- `allowed_fields_json`：允许返回字段。
- `allowed_filter_fields_json`：允许过滤字段。
- `required_filter_fields_json`：必填过滤字段。
- `user_scope_field`：用户隔离字段，例如 `user_id`。
- `default_order_by`：默认排序字段。
- `max_limit`：最大返回行数。
- `enabled`：是否启用。
- `created_at`、`updated_at`。

### tool_call_records

统一工具调用记录表。

核心字段：

- `id`：主键。
- `tool_name`：工具名。
- `business_type`：`CHAT`、`INTERVIEW`、`MEMORY` 等。
- `user_id`。
- `conversation_id`。
- `business_id`：例如 interviewId。
- `raw_arguments_json`：模型提交的原始工具参数。
- `raw_result_json`：工具返回给模型的原始结果。
- `status`：`SUCCESS`、`FAILED`、`NEED_CLARIFICATION`。
- `error_message`。
- `duration_ms`。
- `created_at`。

记录中不得保存 Tavily API key、Authorization header、数据库密码等敏感信息。

### ai_evidence_search_records

外部证据搜索记录表。

核心字段：

- `id`：主键。
- `tool_call_id`：关联 `tool_call_records`。
- `component_code`。
- `search_type`：`OFFICIAL_DOC` 或 `STACK_OVERFLOW`。
- `query_text`。
- `raw_request_json`：Tavily 请求体，不含 API key。
- `raw_response_json`：Tavily 原始响应。
- `matched_urls_json`。
- `created_at`。

## WebSocket 流式协议

chat 和 interview 的 WebSocket 在 tool calling 场景下增加状态事件：

```json
{"type":"tool_call_started","toolName":"search_component_evidence","message":"正在检索 Spring WebSocket 官方文档和 Stack Overflow"}
{"type":"tool_call_finished","toolName":"search_component_evidence","status":"SUCCESS","evidenceCount":5}
{"type":"delta","content":"根据 Spring 官方文档..."}
{"type":"done"}
```

如果需要追问：

```json
{"type":"tool_call_finished","toolName":"search_component_evidence","status":"NEED_CLARIFICATION"}
{"type":"delta","content":"你问的是 Spring WebSocket 还是 Java 原生 WebSocket？"}
{"type":"done","finishReason":"need_clarification"}
```

如果工具失败：

```json
{"type":"tool_call_finished","toolName":"search_component_evidence","status":"FAILED"}
{"type":"delta","content":"当前无法获取可靠证据，请补充组件名称或稍后重试。"}
{"type":"done","finishReason":"tool_error"}
```

## chat 接入

chat 的用户问题进入模型前，后端提供三类工具定义。模型可以调用一个或多个工具。

推荐规则：

- 用户问组件用法、版本行为、API 参数、错误原因时，优先调用 `search_component_evidence`。
- 用户问项目内部知识、历史沉淀、知识库内容时，调用 `query_rag_knowledge`。
- 用户问当前系统业务数据时，调用 `query_database`。
- 如果问题缺少组件名、业务范围或查询条件，先追问。

chat 的最终回答应包含来源列表或数据依据。没有来源时不能输出确定性技术结论。

## interview 接入

interview 中工具能力用于两个场景：

- 生成问题、追问、解释答案时，可调用 `search_component_evidence` 和 `query_rag_knowledge`。
- 查看当前候选人的面试上下文、历史会话摘要等业务数据时，可调用 `query_database`，但必须受 userId 和 interviewId 隔离。

评分链路要保持稳定：

- 评分时可以引用 RAG 或官方证据，但不能因为 Stack Overflow 的社区回答直接改变官方事实判断。
- 工具失败时不应把评分直接判为错误；应进入可重试或待评分状态。
- 面试报告应列出使用过的证据来源，便于复盘。

## 证据闸门

新增后端证据闸门，用于防止模型无证据回答。

闸门规则：

- 技术事实类回答必须存在至少一个 tool result。
- 组件官方行为类回答必须存在 `OFFICIAL_DOC` 证据。
- 数据库事实类回答必须来自 `query_database` 结果。
- 知识库事实类回答必须来自 `query_rag_knowledge` 结果。
- 如果 tool result 状态是 `NEED_CLARIFICATION`，最终输出只能是追问。
- 如果 tool result 状态是 `FAILED` 且没有其他证据，最终输出只能说明无法确认。

第一版可以通过 prompt 规则和服务层检查共同实现，不需要复杂的自然语言审查器。

## 配置

新增配置项：

```properties
agentscope.tavily.api-key=
agentscope.tavily.base-url=https://api.tavily.com
agentscope.tavily.timeout=10s
agentscope.tools.enabled=true
agentscope.tools.max-rounds=3
agentscope.tools.evidence.max-results=5
agentscope.tools.database.default-limit=20
```

如果未配置 Tavily API key：

- `search_component_evidence` 返回 `FAILED`。
- chat/interview 不启动失败。
- 工具调用记录失败原因。
- 模型不能编造外部证据。

## 错误处理

- 组件识别不明确：返回 `NEED_CLARIFICATION`。
- Tavily 超时或网络失败：返回 `FAILED`，记录错误，不暴露 API key。
- RAG 检索失败：返回 `FAILED`，chat 可追问或说明知识库暂不可用；interview 保持原有可重试状态。
- 数据库查询配置不存在：返回 `FAILED`。
- 数据库字段不在白名单：返回 `FAILED` 并记录拒绝原因。
- 用户隔离条件缺失：后端自动补充；无法补充时拒绝执行。
- tool call 轮数超过限制：停止调用工具，要求模型追问或说明无法确认。

## 测试方案

需要覆盖以下测试：

- `FunctionToolRegistryTest`：工具注册、按名称查找、未知工具拒绝。
- `ComponentKnowledgeConfigRepositoryTest`：读取组件配置、别名匹配、启用状态过滤。
- `ComponentEvidenceToolTest`：Tavily 请求构造、官方文档域名限制、Stack Overflow 搜索限制、证据去重。
- `RagKnowledgeToolTest`：调用现有 RAG 检索，限制 topK 和返回长度。
- `DatabaseQueryToolTest`：白名单字段、过滤字段、强制 limit、用户隔离、拒绝未知字段。
- `ToolCallRecorderTest`：记录原始参数、原始结果、状态、错误和耗时。
- `ToolCallingChatServiceTest`：chat 模型 tool call 后能拿到 tool result，并基于证据回答。
- `ToolCallingInterviewServiceTest`：interview 生成/评分/报告链路能接收 tool result，并保持业务状态一致。
- `ChatWebSocketToolCallTest`：流式输出 `tool_call_started`、`tool_call_finished`、`delta`、`done` 顺序正确。
- `InterviewWebSocketToolCallTest`：面试流式输出 tool 状态和最终 snapshot。

## 自检结果

- 没有让模型直接执行 SQL。
- 没有把 Tavily API key、数据库密码或 Authorization header 写入记录表。
- chat 和 interview 共用工具体系，避免重复实现。
- 数据库查询使用配置表和参数绑定，默认只读。
- 官方文档与 Stack Overflow 的证据地位已区分。
- 工具失败、证据不足、组件不明确时都有追问或失败路径。
- 设计没有依赖未确认的管理后台能力。
