# Agent Memory Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前 Spring Boot AgentScope demo 增加按 `conversationId` 隔离的短期记忆、本地 JSON 长期记忆，以及基于 SiliconFlow embedding 和 PostgreSQL pgvector 的 RAG。

**Architecture:** `MemoryOrchestrator` 统一读取三层上下文并在模型回复后更新记忆。短期记忆使用进程内存，长期记忆使用 JSON 文件，知识文件通过手动 reindex 接口切块、生成 1024 维向量并写入 pgvector；聊天时仅做向量检索，不扫描文件。

**Tech Stack:** Java 17、Spring Boot 4、Spring MVC、Spring JDBC、PostgreSQL JDBC、Jackson、AgentScope 1.0.12、JUnit 5、MockMvc。

---

### Task 1: 会话契约与短期记忆

**Files:**
- Modify: `src/main/java/com/example/demoscope/AgentChatService.java`
- Modify: `src/main/java/com/example/demoscope/AgentChatController.java`
- Create: `src/main/java/com/example/demoscope/MemoryTurn.java`
- Create: `src/main/java/com/example/demoscope/ShortTermMemoryStore.java`
- Create: `src/main/java/com/example/demoscope/InMemoryShortTermMemoryStore.java`
- Create: `src/test/java/com/example/demoscope/InMemoryShortTermMemoryStoreTest.java`
- Modify: `src/test/java/com/example/demoscope/AgentChatControllerTest.java`

- [ ] 先写失败测试：不同 `conversationId` 不串话，超过 `maxTurns` 时丢弃最旧轮次。
- [ ] 运行 `mvn -Dtest=InMemoryShortTermMemoryStoreTest test`，确认因类型不存在而失败。
- [ ] 实现 `MemoryTurn`、`ShortTermMemoryStore` 和线程安全的 `InMemoryShortTermMemoryStore`。
- [ ] 运行短期记忆测试，确认通过。
- [ ] 修改控制器测试，要求请求同时包含 `conversationId` 和 `message`，空会话 ID 返回 400。
- [ ] 运行控制器测试，确认旧契约导致失败。
- [ ] 将 `AgentChatService.chat` 改为 `chat(String conversationId, String message)`，同步更新控制器。
- [ ] 运行相关测试并提交 `feat: add conversation-scoped short-term memory`。

### Task 2: Prompt 组装与记忆编排边界

**Files:**
- Create: `src/main/java/com/example/demoscope/MemoryContext.java`
- Create: `src/main/java/com/example/demoscope/PromptContextBuilder.java`
- Create: `src/main/java/com/example/demoscope/MemoryOrchestrator.java`
- Create: `src/main/java/com/example/demoscope/KnowledgeRetriever.java`
- Create: `src/main/java/com/example/demoscope/LongTermMemoryRepository.java`
- Create: `src/test/java/com/example/demoscope/PromptContextBuilderTest.java`
- Create: `src/test/java/com/example/demoscope/MemoryOrchestratorTest.java`

- [ ] 写失败测试：prompt 按系统指令、短期记忆、长期记忆、RAG、用户问题分区；空分区不输出标题。
- [ ] 运行测试确认失败。
- [ ] 实现不可变 `MemoryContext` 和 `PromptContextBuilder`。
- [ ] 写失败测试：RAG/长期记忆读取异常时编排器降级为空，但短期记忆仍返回。
- [ ] 实现 `MemoryOrchestrator.prepare` 和 `recordTurn` 的最小接口。
- [ ] 运行相关测试并提交 `feat: orchestrate layered memory context`。

### Task 3: 长期记忆 JSON 存储与安全过滤

**Files:**
- Create: `src/main/java/com/example/demoscope/LongTermMemoryCategory.java`
- Create: `src/main/java/com/example/demoscope/LongTermMemory.java`
- Create: `src/main/java/com/example/demoscope/LongTermMemoryCandidate.java`
- Create: `src/main/java/com/example/demoscope/LongTermMemoryExtractor.java`
- Create: `src/main/java/com/example/demoscope/LongTermMemoryPolicy.java`
- Create: `src/main/java/com/example/demoscope/JsonLongTermMemoryRepository.java`
- Create: `src/test/java/com/example/demoscope/LongTermMemoryPolicyTest.java`
- Create: `src/test/java/com/example/demoscope/JsonLongTermMemoryRepositoryTest.java`

- [ ] 写失败测试：仅接受 `preference`、`project_convention`、`stable_fact`、`common_config`。
- [ ] 写失败测试：包含 API key、token、password、secret 等敏感模式的候选项被拒绝。
- [ ] 实现 `LongTermMemoryPolicy` 并运行测试。
- [ ] 写失败测试：JSON repository 能持久化、重新加载，并按规范化文本去重更新。
- [ ] 实现原子写入策略：写临时文件后移动替换目标文件。
- [ ] 运行长期记忆测试并提交 `feat: persist filtered long-term memories`。

### Task 4: 模型驱动的长期记忆提取

**Files:**
- Create: `src/main/java/com/example/demoscope/ChatTextModel.java`
- Create: `src/main/java/com/example/demoscope/AgentScopeChatTextModel.java`
- Create: `src/main/java/com/example/demoscope/ModelLongTermMemoryExtractor.java`
- Create: `src/test/java/com/example/demoscope/ModelLongTermMemoryExtractorTest.java`

- [ ] 写失败测试：提取器将对话发给 `ChatTextModel`，解析严格 JSON 数组为候选项。
- [ ] 写失败测试：模型返回非 JSON、未知类别或空文本时返回空候选列表，不阻断聊天。
- [ ] 实现 `ChatTextModel` 适配器和 Jackson JSON 解析。
- [ ] 运行测试并提交 `feat: extract long-term memory candidates`。

### Task 5: SiliconFlow Embedding 客户端

**Files:**
- Create: `src/main/java/com/example/demoscope/EmbeddingClient.java`
- Create: `src/main/java/com/example/demoscope/SiliconFlowEmbeddingClient.java`
- Create: `src/test/java/com/example/demoscope/SiliconFlowEmbeddingClientTest.java`

- [ ] 使用本地 HTTP 测试服务器写失败测试，验证请求发送到 `/embeddings`，包含 bearer token、模型、输入和 `dimensions=1024`。
- [ ] 写失败测试：解析 `data[0].embedding`，维度不等于配置值时抛出清晰异常。
- [ ] 使用 Spring `RestClient` 实现最小客户端。
- [ ] 运行测试并提交 `feat: add SiliconFlow embedding client`。

### Task 6: PostgreSQL pgvector 检索

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/schema.sql`
- Create: `src/main/java/com/example/demoscope/PgVectorKnowledgeStore.java`
- Create: `src/test/java/com/example/demoscope/PgVectorKnowledgeStoreTest.java`

- [ ] 写失败测试：使用 mock `JdbcOperations` 验证查询使用 `embedding <=> ?::vector`、`limit` 和可选 `maxDistance`。
- [ ] 为项目添加 `spring-boot-starter-jdbc` 和 PostgreSQL JDBC 驱动。
- [ ] 添加 `vector` extension 与 `knowledge_chunks` 表结构，字段为 `embedding vector(1024)`。
- [ ] 实现向量字符串序列化、查询和 `KnowledgeChunk` 映射。
- [ ] 运行测试并提交 `feat: retrieve knowledge with pgvector`。

### Task 7: 知识文件重建索引

**Files:**
- Create: `src/main/java/com/example/demoscope/KnowledgeFileChunker.java`
- Create: `src/main/java/com/example/demoscope/KnowledgeIndexer.java`
- Create: `src/main/java/com/example/demoscope/ReindexResult.java`
- Create: `src/main/java/com/example/demoscope/KnowledgeAdminController.java`
- Create: `src/test/java/com/example/demoscope/KnowledgeFileChunkerTest.java`
- Create: `src/test/java/com/example/demoscope/KnowledgeIndexerTest.java`
- Create: `src/test/java/com/example/demoscope/KnowledgeAdminControllerTest.java`

- [ ] 写失败测试：仅扫描 `.md/.txt`，按配置长度切块，生成稳定 source、chunk index 和 SHA-256 checksum。
- [ ] 实现 `KnowledgeFileChunker`。
- [ ] 写失败测试：未变化 checksum 跳过，变化内容调用 embedding 并 upsert；单块失败计入结果但不中止其余块。
- [ ] 实现 `KnowledgeIndexer` 与 JDBC upsert/delete-stale 逻辑。
- [ ] 写失败 MockMvc 测试：`POST /api/knowledge/reindex` 返回 scanned/indexed/skipped/failed。
- [ ] 实现管理控制器，运行测试并提交 `feat: reindex local knowledge into pgvector`。

### Task 8: 集成聊天服务、配置和前端

**Files:**
- Modify: `src/main/java/com/example/demoscope/OpenAiAgentChatService.java`
- Replace: `src/main/java/com/example/demoscope/RagConfig.java`
- Modify: `src/main/java/com/example/demoscope/AgentRuntimeConfigController.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java`
- Create: `src/test/java/com/example/demoscope/OpenAiAgentChatServiceMemoryTest.java`

- [ ] 写失败测试：chat service 调用 orchestrator.prepare，模型回复后调用 recordTurn；memory/RAG 读取失败时仍能调用模型。
- [ ] 将模型调用抽到 `ChatTextModel`，让聊天服务可测试。
- [ ] 创建 Spring 配置 bean：短期 store、JSON repository、policy、extractor、embedding client、pgvector store、indexer、orchestrator。
- [ ] 添加 PostgreSQL、embedding、memory、RAG 配置项和安全默认值。
- [ ] 更新 `/api/config`，只暴露是否配置，不返回任何密钥。
- [ ] 前端在 `sessionStorage` 生成并复用 `conversationId`，聊天请求携带该字段。
- [ ] 运行集成测试并提交 `feat: integrate layered agent memory`。

### Task 9: 回归验证与清理旧 RAG

**Files:**
- Delete: `src/main/java/com/example/demoscope/LocalKnowledgeStore.java`
- Delete: `src/main/java/com/example/demoscope/RagPromptBuilder.java`
- Delete: `src/test/java/com/example/demoscope/LocalKnowledgeStoreTest.java`
- Delete: `src/test/java/com/example/demoscope/RagPromptBuilderTest.java`
- Modify: `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`

- [ ] 先运行完整测试，确认新实现已不依赖旧关键词 RAG。
- [ ] 删除旧实现和旧测试。
- [ ] 运行 `mvn test`，要求全部测试通过。
- [ ] 运行 `mvn package -DskipTests`，要求构建成功。
- [ ] 检查 `git diff --check` 和 `git status --short`。
- [ ] 提交 `refactor: remove legacy keyword rag`。
