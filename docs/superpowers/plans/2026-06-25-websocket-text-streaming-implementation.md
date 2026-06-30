# WebSocket 文字流式输出实施计划

> **给 agentic workers：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务执行。本计划使用复选框（`- [ ]`）跟踪进度。

**目标：** 为聊天和面试流程增加 WebSocket 文字流式输出，聊天输出真实模型 `delta`，面试输出可读文字 `delta` 并最终返回结构化 `snapshot`。

**架构：** 保留现有 HTTP 接口。新增 WebSocket handler 只负责协议收发和鉴权；聊天流式编排放在 `service/chat`；面试文字流和响应映射放在 `service/interview`；模型流式能力放在 `common/llm`。

**技术栈：** Spring Boot 4、Spring WebSocket、Java HTTP Client、Jackson、JUnit 6、MockMvc/WebSocket 测试。

---

## 文件结构

- 修改：`pom.xml`，增加 WebSocket starter。
- 新建：`src/main/java/com/example/demoscope/config/chat/WebSocketConfig.java`，注册 `/ws/chat` 和 `/ws/interviews`。
- 新建：`src/main/java/com/example/demoscope/controller/chat/ChatWebSocketHandler.java`，处理聊天 WebSocket 消息。
- 新建：`src/main/java/com/example/demoscope/controller/interview/InterviewWebSocketHandler.java`，处理面试 WebSocket 消息。
- 新建：`src/main/java/com/example/demoscope/controller/stream/StreamMessage.java`，统一 `start/delta/snapshot/done/error` 响应。
- 新建：`src/main/java/com/example/demoscope/service/chat/StreamingAgentChatService.java`，聊天流式编排。
- 新建：`src/main/java/com/example/demoscope/service/interview/InterviewStreamingFacade.java`，面试动作流式编排。
- 新建：`src/main/java/com/example/demoscope/controller/interview/InterviewResponseMapper.java`，复用 HTTP 与 WebSocket 的响应映射。
- 新建：`src/main/java/com/example/demoscope/common/llm/StreamingChatTextModel.java`，模型流式接口。
- 修改：`src/main/java/com/example/demoscope/common/llm/AgentScopeChatTextModel.java`，增加 OpenAI compatible SSE 流式读取。
- 修改：`src/main/java/com/example/demoscope/controller/interview/InterviewController.java`，改用 `InterviewResponseMapper`。
- 修改：`src/main/resources/static/index.html`，前端聊天页面改为 WebSocket 流式展示；面试流式入口先提供后端 `/ws/interviews` 接口。
- 测试：新增或修改 chat、interview、llm、config 相关测试。

## 任务 1：加入 WebSocket 依赖和配置

**文件：**
- 修改：`pom.xml`
- 新建：`src/main/java/com/example/demoscope/config/chat/WebSocketConfig.java`
- 测试：`src/test/java/com/example/demoscope/controller/chat/ChatWebSocketHandlerTest.java` 与 `src/test/java/com/example/demoscope/controller/interview/InterviewWebSocketHandlerTest.java`

- [ ] **Step 1：写失败测试**

通过 handler 测试验证 `/ws/chat` 和 `/ws/interviews` 所需 bean、协议输出和鉴权逻辑可用；完整应用上下文由现有 controller 测试和全量测试覆盖。

```java
@SpringBootTest(properties = {
        "agentscope.openai.api-key=",
        "agentscope.interview.enabled=true"
})
class ChatWebSocketHandlerTest {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    private InterviewWebSocketHandler interviewWebSocketHandler;

    @Test
    void websocketHandlersAreWired() {
        assertNotNull(chatWebSocketHandler);
        assertNotNull(interviewWebSocketHandler);
    }
}
```

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ChatWebSocketHandlerTest,InterviewWebSocketHandlerTest' test
```

预期：编译失败，因为 WebSocket handler 和配置类尚不存在。

- [ ] **Step 3：实现最小配置**

在 `pom.xml` 增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

创建 `WebSocketConfig`：

```java
@Configuration(proxyBeanMethods = false)
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatHandler;
    private final ObjectProvider<InterviewWebSocketHandler> interviewHandler;

    public WebSocketConfig(
            ChatWebSocketHandler chatHandler,
            ObjectProvider<InterviewWebSocketHandler> interviewHandler) {
        this.chatHandler = chatHandler;
        this.interviewHandler = interviewHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat").setAllowedOriginPatterns("*");
        interviewHandler.ifAvailable(handler -> registry.addHandler(handler, "/ws/interviews")
                .setAllowedOriginPatterns("*"));
    }
}
```

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 2：定义统一流式协议消息

**文件：**
- 新建：`src/main/java/com/example/demoscope/controller/stream/StreamMessage.java`
- 测试：`src/test/java/com/example/demoscope/controller/stream/StreamMessageTest.java`

- [ ] **Step 1：写失败测试**

```java
@Test
void serializesDeltaAndSnapshotMessages() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    assertEquals(
            "{\"type\":\"delta\",\"content\":\"你好\"}",
            mapper.writeValueAsString(StreamMessage.delta("你好")));
    assertEquals(
            "{\"type\":\"done\"}",
            mapper.writeValueAsString(StreamMessage.done()));
}
```

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=StreamMessageTest' test
```

预期：编译失败，因为 `StreamMessage` 尚不存在。

- [ ] **Step 3：实现协议对象**

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamMessage(
        String type,
        String action,
        String content,
        Object data,
        String message) {

    public static StreamMessage start(String action) {
        return new StreamMessage("start", action, null, null, null);
    }

    public static StreamMessage delta(String content) {
        return new StreamMessage("delta", null, content, null, null);
    }

    public static StreamMessage snapshot(Object data) {
        return new StreamMessage("snapshot", null, null, data, null);
    }

    public static StreamMessage done() {
        return new StreamMessage("done", null, null, null, null);
    }

    public static StreamMessage error(String message) {
        return new StreamMessage("error", null, null, null, message);
    }
}
```

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 3：增加聊天流式服务

**文件：**
- 新建：`src/main/java/com/example/demoscope/service/chat/StreamingAgentChatService.java`
- 新建：`src/main/java/com/example/demoscope/common/llm/StreamingChatTextModel.java`
- 测试：`src/test/java/com/example/demoscope/service/chat/StreamingAgentChatServiceTest.java`

- [ ] **Step 1：写失败测试**

测试应验证模型分两段输出时，listener 收到两个 `delta`，并且完整回答结束后才写入记忆。

```java
@Test
void streamsDeltasAndRecordsFullAnswerAfterCompletion() {
    FakeStreamingModel model = new FakeStreamingModel("你", "好");
    CapturingMemoryOrchestrator memory = new CapturingMemoryOrchestrator();
    StreamingAgentChatService service = new StreamingAgentChatService(
            model,
            memory,
            new PromptContextBuilder(),
            "system");
    List<String> deltas = new ArrayList<>();

    service.chat("user-42", "conversation-1", "你好", deltas::add);

    assertEquals(List.of("你", "好"), deltas);
    assertEquals("你好", memory.lastAssistantAnswer);
}
```

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=StreamingAgentChatServiceTest' test
```

预期：编译失败，因为流式服务和接口尚不存在。

- [ ] **Step 3：实现最小服务**

`StreamingChatTextModel`：

```java
public interface StreamingChatTextModel extends ChatTextModel {

    void generateStream(
            String systemPrompt,
            String userPrompt,
            Consumer<String> onDelta);
}
```

`StreamingAgentChatService`：

```java
public class StreamingAgentChatService {

    public void chat(
            String userId,
            String conversationId,
            String message,
            Consumer<String> onDelta) {
        MemoryContext memoryContext =
                memoryOrchestrator.prepare(userId, conversationId, message);
        String prompt = promptContextBuilder.build(
                systemPrompt,
                memoryContext,
                message);
        StringBuilder answer = new StringBuilder();
        model.generateStream(systemPrompt, prompt, delta -> {
            answer.append(delta);
            onDelta.accept(delta);
        });
        memoryOrchestrator.recordTurn(
                userId,
                conversationId,
                message,
                answer.toString());
    }
}
```

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 4：实现聊天 WebSocket handler

**文件：**
- 新建：`src/main/java/com/example/demoscope/controller/chat/ChatWebSocketHandler.java`
- 测试：`src/test/java/com/example/demoscope/controller/chat/ChatWebSocketHandlerTest.java`

- [ ] **Step 1：写失败测试**

测试使用假 `WebSocketSession`，发送包含 token、`conversationId`、`message` 的 JSON，断言响应包含 `start`、`delta`、`done`。

```java
@Test
void chatWebSocketStreamsStartDeltaAndDone() throws Exception {
    FakeSession session = new FakeSession();
    handler.handleTextMessage(session, new TextMessage("""
            {"token":"valid-token","conversationId":"c1","message":"hello"}
            """));

    assertThat(session.sentText()).contains("\"type\":\"start\"");
    assertThat(session.sentText()).contains("\"type\":\"delta\"");
    assertThat(session.sentText()).contains("\"type\":\"done\"");
}
```

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ChatWebSocketHandlerTest' test
```

预期：编译失败，因为 handler 尚不存在。

- [ ] **Step 3：实现 handler**

handler 需要：

- 用 `ObjectMapper` 解析请求。
- 用 `SaTokenFacade.getLoginIdByToken(token)` 解析 RuoYi 用户 ID。
- 发送 `StreamMessage.start("chat")`。
- 调用 `StreamingAgentChatService.chat(...)` 并逐段发送 `StreamMessage.delta(delta)`。
- 成功后发送 `StreamMessage.done()`。
- 异常时发送 `StreamMessage.error(message)`。

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 5：抽取面试响应映射和文字渲染

**文件：**
- 新建：`src/main/java/com/example/demoscope/controller/interview/InterviewResponseMapper.java`
- 新建：`src/main/java/com/example/demoscope/service/interview/InterviewStreamTextRenderer.java`
- 修改：`src/main/java/com/example/demoscope/controller/interview/InterviewController.java`
- 测试：`src/test/java/com/example/demoscope/controller/interview/InterviewWebSocketHandlerTest.java`

- [ ] **Step 1：写失败测试**

```java
@Test
void rendersQuestionTextForInProgressSnapshot() {
    InterviewSnapshot snapshot = snapshotWithQuestion("解释 HashMap。");

    assertEquals(
            "解释 HashMap。",
            new InterviewStreamTextRenderer().render(snapshot));
}
```

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewWebSocketHandlerTest' test
```

预期：编译失败，因为渲染器尚不存在。

- [ ] **Step 3：实现映射和渲染**

`InterviewResponseMapper` 复用 `InterviewController` 当前 `toResponse` 逻辑。`InterviewStreamTextRenderer` 根据状态输出：

- 当前题目：题目文本。
- 已完成报告：总分、优势、弱点、改进建议摘要。
- `SCORING_PENDING`：`"正在生成评分报告，请稍后。"`
- `CANCELLED`：`"本次面试已取消。"`

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 6：实现面试 WebSocket handler

**文件：**
- 新建：`src/main/java/com/example/demoscope/controller/interview/InterviewWebSocketHandler.java`
- 新建：`src/main/java/com/example/demoscope/service/interview/InterviewStreamingFacade.java`
- 测试：`src/test/java/com/example/demoscope/controller/interview/InterviewWebSocketHandlerTest.java`

- [ ] **Step 1：写失败测试**

测试 `answer` 动作输出 `start`、可读 `delta`、`snapshot`、`done`。

```java
@Test
void answerActionStreamsTextAndFinalSnapshot() throws Exception {
    FakeSession session = new FakeSession();
    handler.handleTextMessage(session, new TextMessage("""
            {
              "token":"valid-token",
              "action":"answer",
              "interviewId":"00000000-0000-0000-0000-000000000001",
              "questionId":"00000000-0000-0000-0000-000000000002",
              "answer":"candidate answer"
            }
            """));

    assertThat(session.sentText()).contains("\"type\":\"start\"");
    assertThat(session.sentText()).contains("\"type\":\"delta\"");
    assertThat(session.sentText()).contains("\"type\":\"snapshot\"");
    assertThat(session.sentText()).contains("\"type\":\"done\"");
}
```

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewWebSocketHandlerTest' test
```

预期：编译失败，因为 handler 和 facade 尚不存在。

- [ ] **Step 3：实现 facade 和 handler**

`InterviewStreamingFacade` 将 WebSocket 动作转成现有 `InterviewService` 调用：

- `create` -> `createOrResume`
- `current` -> `current`
- `get` -> `get`
- `answer` -> `answer`
- `finish` -> `finish`

handler 发送顺序：

```text
start -> delta -> snapshot -> done
```

错误发送：

```text
error
```

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 7：实现 OpenAI compatible SSE 解析

**文件：**
- 修改：`src/main/java/com/example/demoscope/common/llm/AgentScopeChatTextModel.java`
- 测试：`src/test/java/com/example/demoscope/common/llm/AgentScopeChatTextModelTest.java`

- [ ] **Step 1：写失败测试**

用本地 `HttpServer` 返回 SSE：

```text
data: {"choices":[{"delta":{"content":"你"}}]}

data: {"choices":[{"delta":{"content":"好"}}]}

data: [DONE]
```

断言 `generateStream` 输出 `["你", "好"]`。

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgentScopeChatTextModelTest' test
```

预期：编译失败或方法不存在。

- [ ] **Step 3：实现 SSE 流式读取**

在 `AgentScopeChatTextModel` 中保留同步 `generate()`，新增 `generateStream(...)`：

- 构造 `/chat/completions` 请求。
- body 中包含 `"stream":true`。
- 逐行读取 `data:`。
- 遇到 `[DONE]` 结束。
- 从 `choices[0].delta.content` 提取内容并回调 `onDelta`。

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 8：更新聊天前端页面为 WebSocket 流式展示

**文件：**
- 修改：`src/main/resources/static/index.html`
- 测试：`src/test/java/com/example/demoscope/controller/chat/AgentChatControllerTest.java`

- [ ] **Step 1：写失败测试**

更新 HTML 测试，断言页面包含 `/ws/chat` 和 `WebSocket`。

```java
mockMvc.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("/ws/chat")))
        .andExpect(content().string(containsString("WebSocket")));
```

- [ ] **Step 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgentChatControllerTest' test
```

预期：断言失败，因为页面仍使用 `fetch("/api/chat")`。

- [ ] **Step 3：更新页面脚本**

聊天发送逻辑改为：

```javascript
const socket = new WebSocket(wsUrl("/ws/chat"));
socket.onopen = () => socket.send(JSON.stringify({
    token,
    conversationId,
    message
}));
socket.onmessage = event => {
    const payload = JSON.parse(event.data);
    if (payload.type === "delta") {
        answerBox.textContent += payload.content || "";
    }
};
```

面试页面逻辑按同样协议接 `/ws/interviews`。

- [ ] **Step 4：运行测试确认 GREEN**

运行同一个测试，预期通过。

## 任务 9：回归验证

**文件：**
- 全项目

- [ ] **Step 1：运行 WebSocket 相关测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=StreamMessageTest,StreamingAgentChatServiceTest,ChatWebSocketHandlerTest,InterviewWebSocketHandlerTest,AgentScopeChatTextModelTest,AgentChatControllerTest' test
```

预期：全部通过。

- [ ] **Step 2：运行现有核心测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgentChatControllerTest,InterviewControllerTest,InterviewServiceAnswerTest,InterviewServiceFinishTest,InterviewConfigTest' test
```

预期：全部通过。

- [ ] **Step 3：运行全量测试**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B test
```

预期：`BUILD SUCCESS`，无失败测试。

## 自查结果

- 设计要求覆盖：聊天真流式、面试文字流式、最终 `snapshot`、保留 HTTP 接口、错误消息均已拆入任务。
- 占位符扫描：没有 `TBD`、`TODO` 或未定义任务。
- 类型一致性：协议统一使用 `StreamMessage`，聊天使用 `StreamingAgentChatService`，面试使用 `InterviewStreamingFacade` 和 `InterviewResponseMapper`。
