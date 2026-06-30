# LLM Token 用量记录实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为所有 OpenAI-compatible LLM 调用建立数据库记录，保存原始请求 body、token usage、调用状态和业务归属。

**Architecture:** 记录能力放在 `common/llm` 与 `common/jdbc` 边界：模型调用层负责构造原始请求、解析 usage、触发记录；业务层只通过 `TokenUsageContextHolder` 标记当前调用属于 chat、interview 或 memory。默认 recorder 写入 PostgreSQL；没有 `JdbcOperations` 时使用 no-op，避免本地无库启动失败。

**Tech Stack:** Spring Boot 4、Spring JDBC、PostgreSQL、Jackson、Java HTTP Client、JUnit、Mockito。

---

## 文件结构

- 新建：`src/main/java/com/example/demoscope/common/llm/TokenUsageRecord.java`，单次 LLM 调用记录。
- 新建：`src/main/java/com/example/demoscope/common/llm/TokenUsageRecorder.java`，记录接口。
- 新建：`src/main/java/com/example/demoscope/common/llm/TokenUsageContext.java`，业务上下文。
- 新建：`src/main/java/com/example/demoscope/common/llm/TokenUsageContextHolder.java`，ThreadLocal 上下文传递。
- 新建：`src/main/java/com/example/demoscope/common/llm/NoopTokenUsageRecorder.java`，无数据库时兜底。
- 新建：`src/main/java/com/example/demoscope/common/jdbc/JdbcTokenUsageRecorder.java`，建表并落库。
- 修改：`src/main/java/com/example/demoscope/common/llm/AgentScopeChatTextModel.java`，同步和流式调用统一记录原始请求与 usage。
- 修改：`src/main/java/com/example/demoscope/config/memory/AgentMemoryConfig.java`，注册 token recorder。
- 修改：`src/main/java/com/example/demoscope/service/chat/OpenAiAgentChatService.java` 与 `StreamingAgentChatService.java`，设置 `CHAT` 上下文。
- 修改：`src/main/java/com/example/demoscope/common/llm/InterviewAiJsonClient.java`，没有显式上下文时设置 `INTERVIEW`。
- 测试：新增 `JdbcTokenUsageRecorderTest`、扩展 `AgentScopeChatTextModelTest`，扩展 chat/interview 服务测试。

## 任务 1：JDBC 记录器

- [ ] **Step 1：写失败测试**

验证 `initializeSchema()` 创建 `llm_call_records` 表和索引；验证 `record()` 插入原始请求、usage、状态和时间。

- [ ] **Step 2：实现最小记录器**

`JdbcTokenUsageRecorder` 使用 `JdbcOperations.execute` 建表，用 `JdbcOperations.update` 插入记录。

- [ ] **Step 3：运行测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=JdbcTokenUsageRecorderTest' test
```

## 任务 2：模型层 usage 解析与原始请求记录

- [ ] **Step 1：写失败测试**

用本地 `HttpServer` 分别模拟非流式和流式响应：

- 非流式响应包含 `usage.prompt_tokens/completion_tokens/total_tokens`。
- 流式最后一个 chunk 包含 `usage`，请求 body 包含 `stream_options.include_usage=true`。

- [ ] **Step 2：实现最小模型记录**

`AgentScopeChatTextModel` 构造 raw request body 后调用 HTTP 接口，解析响应中的 usage，生成 `TokenUsageRecord` 并交给 recorder。失败时记录 `FAILED`。

- [ ] **Step 3：运行测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgentScopeChatTextModelTest' test
```

## 任务 3：业务上下文传递

- [ ] **Step 1：写失败测试**

chat 服务调用模型时，`TokenUsageContextHolder.current()` 能拿到 `businessType=CHAT`、`userId`、`conversationId`。

- [ ] **Step 2：实现 chat 上下文**

同步和流式 chat 都用 `TokenUsageContextHolder.runWithContext(...)` 包裹模型调用。

- [ ] **Step 3：实现 interview 默认上下文**

`InterviewAiJsonClient` 在没有上下文时设置 `businessType=INTERVIEW`。后续如果需要 interviewId 级别精确归属，再由 interview service 设置业务 ID。

## 任务 4：配置装配

- [ ] **Step 1：注册 recorder bean**

有 `JdbcOperations` 时注册 `JdbcTokenUsageRecorder` 并初始化 schema；没有数据库时注册 `NoopTokenUsageRecorder`。

- [ ] **Step 2：更新模型 bean**

`AgentScopeChatTextModel` 构造函数增加 `TokenUsageRecorder` 与 `Clock`。

## 任务 5：回归验证

- [ ] **Step 1：运行目标测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=JdbcTokenUsageRecorderTest,AgentScopeChatTextModelTest,OpenAiAgentChatServiceMemoryTest,StreamingAgentChatServiceTest,InterviewAiJsonClientTest' test
```

- [ ] **Step 2：运行全量测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B test
```

## 自检结果

- 没有记录 API Key 或 Authorization header。
- 原始请求记录的是 OpenAI-compatible JSON body。
- 同步和流式调用都覆盖 usage 记录。
- 数据库不可用时不影响默认启动。
