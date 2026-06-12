# Interview Multi-Agent Orchestration Design

Date: 2026-06-07

## Goal

将当前单 Agent 聊天后端演进为 Java 后端技术面试多 Agent 编排体系。第一版优先实现后端编排能力，不重做前端 UI，不引入候选人管理后台。

系统应支持：

- RouterAgent 决定下一步由哪个 Agent 处理。
- RagQueryPlannerAgent 决定当前轮检索什么证据。
- InterviewerAgent、ProjectAgent、JavaSkillAgent、ScoreAgent 生成下一问或评分。
- MemoryManagerAgent 判断本轮应写入哪些短期或长期记忆。
- 短期记忆、长期记忆和 RAG Evidence 作为独立上下文输入，不再只把 history 塞进 prompt。

## Current Project Context

当前项目是 Spring Boot Java 17 demo。`AgentChatController` 暴露 `POST /api/chat`，请求已要求包含 `conversationId` 和 `message`。

仓库中已经存在一批新记忆/RAG 组件，包括：

- `MemoryOrchestrator`
- `PromptContextBuilder`
- `ShortTermMemoryStore`
- `LongTermMemoryRepository`
- `PgVectorKnowledgeStore`
- `SiliconFlowEmbeddingClient`

但当前 `OpenAiAgentChatService` 仍直接使用旧的 `LocalKnowledgeStore` 和 `RagPromptBuilder`。因此多 Agent 第一版必须同时解决一个前置集成问题：让聊天路径真正委托新的 memory/RAG 编排层，而不是继续走旧关键词 RAG。

## Chosen Approach

采用“可测试的确定性编排壳 + 独立模型 Agent 接口”。

新增 `InterviewAgentOrchestrator` 作为后端单轮面试编排入口。它用 Java 代码控制流程和失败降级，每个模型 Agent 只负责接收统一上下文并返回严格 JSON。

这个方案优先保证：

- 工程边界清楚。
- 可用 fake model 做单元测试。
- Router、Planner、目标 Agent、MemoryManager 可以独立演进。
- 不把所有 prompt 分支塞进 `OpenAiAgentChatService`。

## Architecture

`OpenAiAgentChatService` 不再直接拼旧 RAG prompt，而是委托 `InterviewAgentOrchestrator` 完成一轮面试。

核心调用链：

```text
Candidate answer
 -> MemoryOrchestrator.prepare()
 -> RouterAgent
 -> RagQueryPlannerAgent
 -> KnowledgeRetriever / pgvector
 -> InterviewerAgent / ProjectAgent / JavaSkillAgent / ScoreAgent
 -> MemoryManagerAgent
 -> MemoryOrchestrator.recordTurn()
 -> Chat response
```

Agent 边界：

- RouterAgent: 只决定 `nextAgent`，不提问、不评分、不总结。
- RagQueryPlannerAgent: 只生成检索计划，不直接检索。
- Target Agents: 生成候选人可见的问题或最终评分报告。
- MemoryManagerAgent: 只判断记忆写入建议，不直接写存储。
- InterviewAgentOrchestrator: 负责状态推进、失败降级、调用顺序、解析 JSON、写入记忆。

## Data Contracts

第一版新增或调整以下核心类型。

### InterviewRuntimeState

```text
conversationId
candidateId optional
stage: INIT / SELF_INTRODUCTION / TECHNICAL_QUESTIONING / SCORING / FINISHED
turnCount
currentAgent
lastAgent
shouldEnd
```

### AgentPromptContext

```text
runtimeState
shortTermMemory
longTermMemory
ragEvidence
currentAnswer
task
```

所有 Agent 共用这套上下文字段，避免每个 Agent 自己定义一套 prompt 变量。

### RouterDecision

```text
nextAgent
reason
confidence
suggestedFocus
usedEvidenceIds
```

### RagQueryPlan

```text
queries[]
  query
  topK
  filters
  purpose
  expectedEvidenceType
```

### AgentOutput

```text
agentName
type: QUESTION / SCORE_REPORT
rawJson
question optional
scoreReport optional
memoryWriteSuggestion optional
usedEvidenceIds
```

### MemoryWriteDecision

```text
shortTermWrites[]
longTermWrites[]
reason
```

第一版 `/api/chat` 请求契约保持不变：

```json
{
  "conversationId": "conversation-a",
  "message": "candidate answer"
}
```

响应也暂时保持：

```json
{
  "answer": "next interview question or score summary"
}
```

后续可以扩展为结构化面试响应，但不作为第一版范围。

## Runtime Flow

一轮 `/api/chat` 的后端流程：

1. 校验 `conversationId` 和 `message`。
2. 读取 conversation 对应的短期记忆、长期记忆和初始 RAG 上下文。
3. 组装 `InterviewRuntimeState`。
4. 调用 RouterAgent 选择 `nextAgent`。
5. 调用 RagQueryPlannerAgent 为目标 Agent 生成检索计划。
6. 使用 `KnowledgeRetriever` 或 pgvector 检索证据，组装 RAG Evidence。
7. 调用目标 Agent 生成下一问或评分报告。
8. 调用 MemoryManagerAgent 生成记忆写入建议。
9. 由 orchestrator 将本轮原始 turn 和允许的记忆写入短期/长期记忆。
10. 返回目标 Agent 的问题文本或评分摘要。

## Error Handling

辅助 Agent 允许降级，目标 Agent 不悄悄编造结果。

- RouterAgent JSON 失败：fallback 到 `interviewer`。
- RagQueryPlannerAgent JSON 失败：fallback 到用 `currentAnswer` 直接检索 topK。
- pgvector 或 embedding 检索失败：RAG Evidence 置空，继续问答。
- 目标 Agent JSON 失败：返回 500，因为这是用户可见核心结果。
- MemoryManagerAgent JSON 失败：跳过模型驱动记忆写入，但仍记录原始 turn。
- 长期记忆写入失败：记录日志，不阻断面试。
- API key 缺失：保持当前 500 行为。

## Testing Plan

测试聚焦后端编排，不调用真实模型和外部服务。

### InterviewAgentOrchestratorTest

- Router 选择 `project`、`java_skill`、`score` 后能调用对应目标 Agent。
- Router 失败时 fallback 到 `interviewer`。
- Planner 失败时 fallback 到 `currentAnswer` 检索。
- RAG 失败时仍能调用目标 Agent。
- 目标 Agent JSON 失败时返回错误。
- MemoryManager 失败时仍记录原始 turn。

### Prompt and Parser Tests

- `RouterDecision` 严格解析。
- `RagQueryPlan` 严格解析。
- Question Agent 输出严格解析。
- Score Agent 输出严格解析。
- 非 JSON 或缺关键字段时按设计失败或降级。

### Chat Service Integration Test

- `/api/chat` 经由 `InterviewAgentOrchestrator`，不再直接依赖旧 `LocalKnowledgeStore`。
- 返回下一问文本。
- `conversationId` 继续隔离短期记忆。

## Out of Scope

- 不重做前端面试 UI。
- 不新增认证、候选人管理或岗位管理。
- 不实现完整题库管理后台。
- 不要求真实 pgvector 集成测试。
- 不在第一版强制删除旧 RAG 文件；只有在新聊天路径测试确认不依赖后再清理。

## Acceptance Criteria

- `OpenAiAgentChatService` 通过 orchestrator 处理一轮面试。
- Router、RAG Planner、目标 Agent、MemoryManager 有清晰接口和 JSON 契约。
- 辅助 Agent 失败能按策略降级。
- 目标 Agent JSON 失败能显式报错。
- 现有 `conversationId` 隔离语义保留。
- 新 memory/RAG 组件进入聊天路径。
- 测试覆盖主要编排分支和失败策略。

