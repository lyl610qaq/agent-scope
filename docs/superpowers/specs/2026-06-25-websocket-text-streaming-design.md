# WebSocket 文字流式输出设计

## 目标

为聊天和面试流程增加基于 WebSocket 的文字流式输出。前端应能逐段收到可读文字，同时后端仍然返回最终结构化状态，用于持久化和页面状态更新。

## 范围

本设计包含：

- 聊天流式接口 `/ws/chat`。
- 面试流式接口 `/ws/interviews`。
- 聊天和面试统一使用纯文本 `delta` 消息。
- 面试动作最终返回结构化 `snapshot` 消息。
- 过渡期保留现有 HTTP 接口兼容能力。

本设计不包含 function calling、tool calling 或模型 token 用量统计。

## WebSocket 协议

客户端每次用户动作发送一条 JSON 命令。鉴权继续使用现有 RuoYi token。由于浏览器原生 WebSocket 不能稳定设置自定义鉴权 Header，token 放在命令 payload 中传递。

聊天请求：

```json
{
  "token": "RuoYi token",
  "conversationId": "conversation-1",
  "message": "Explain Java virtual threads"
}
```

面试请求：

```json
{
  "token": "RuoYi token",
  "action": "answer",
  "interviewId": "00000000-0000-0000-0000-000000000001",
  "questionId": "00000000-0000-0000-0000-000000000002",
  "answer": "candidate answer"
}
```

服务端响应使用统一信封：

```json
{"type":"start","action":"answer"}
{"type":"delta","content":"可读文字片段"}
{"type":"snapshot","data":{}}
{"type":"done"}
{"type":"error","message":"可读错误信息"}
```

## 聊天流程

聊天 WebSocket handler 校验 token，构建记忆上下文，调用流式模型并将模型文本逐段发送为 `delta`。模型输出结束后，服务端拼出完整回答并写入短期/长期记忆，最后发送 `done`。

现有同步接口 `/api/chat` 保留。

## 面试流程

面试 WebSocket handler 支持以下动作：

- `create`
- `current`
- `get`
- `answer`
- `finish`

每个动作都会先校验 RuoYi token，再调用现有面试服务路径，并发送可读文字：

- `create`：如果生成了题目，则流式输出题目文本。
- `current` 和 `get`：流式输出当前题目或报告摘要。
- `answer`：根据结果状态，流式输出下一题、追问、报告摘要或等待评分提示。
- `finish`：完成时流式输出报告摘要；非报告状态输出 pending/cancelled 等状态说明。

每个面试动作还会发送最终 `snapshot`，其数据结构与 HTTP controller 当前返回的 `InterviewResponse` 保持一致。这样前端可以稳定拿到 `questionId`、状态、下一步动作和报告结构，数据库状态也继续由原有服务保证一致性。

现有同步接口 `/api/interviews/**` 在过渡期保留。

## 架构

新增代码遵循当前分层边界：

- `controller/chat`：WebSocket handler 和请求/响应 DTO。
- `service/chat`：聊天流式编排。
- `controller/interview`：面试 WebSocket 命令 handler。
- `service/interview`：将 `InterviewSnapshot` 转为流式文本和响应数据。
- `common/llm`：流式模型抽象与 OpenAI compatible SSE 解析。
- `config/chat`：WebSocket 注册配置。

聊天流应使用真正的模型流式输出。面试流应输出用户可读文字片段，最终持久化状态仍来自结构化服务结果。

## 错误处理

参数校验失败、鉴权失败、业务冲突和模型失败都通过 `error` 消息返回，并结束当前动作。除非协议本身无法解析，WebSocket 连接可以保持打开以接收后续命令。

面试评分失败保持现有可重试 pending 行为。

## 测试

测试应覆盖：

- WebSocket handler 拒绝缺失或无效 token。
- 聊天流式输出 `start`、`delta` 和 `done`。
- 聊天只在完整回答组装完成后写入记忆。
- 面试 `answer` 输出可读 `delta` 文本和最终 `snapshot`。
- 面试 `finish` 输出报告文本和最终 `snapshot`。
- 现有 HTTP controller 测试继续通过。
