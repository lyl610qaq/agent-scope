package com.example.demoscope.common.llm;

import com.example.demoscope.common.llm.AgentScopeChatTextModel;
import com.example.demoscope.common.llm.OpenAiRequestLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentScopeChatTextModelTest {

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-25T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void rejectsBlankApiKeyBeforeCreatingRemoteClient() {
        AgentScopeChatTextModel model = new AgentScopeChatTextModel(
                " ",
                "test-model",
                "https://example.invalid/v1",
                new OpenAiRequestLogger());

        assertThrows(
                IllegalStateException.class,
                () -> model.generate("system", "user"));
    }

    @Test
    void parsesOpenAiCompatibleStreamDeltas() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> respondWithStream(exchange, requestBody));
        server.start();
        CapturingTokenUsageRecorder recorder = new CapturingTokenUsageRecorder();
        try {
            AgentScopeChatTextModel model = new AgentScopeChatTextModel(
                    "test-api-key",
                    "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    new OpenAiRequestLogger(),
                    recorder,
                    clock);
            List<String> deltas = new ArrayList<>();

            TokenUsageContextHolder.runWithContext(
                    new TokenUsageContext("user-42", "conversation-a", "CHAT", "business-1"),
                    () -> model.generateStream("system", "user", deltas::add));

            assertEquals(List.of("hello", " world"), deltas);
            assertTrue(requestBody.get().contains("\"stream\":true"));
            assertTrue(requestBody.get().contains("\"stream_options\":{\"include_usage\":true}"));
            assertTrue(requestBody.get().contains("\"model\":\"test-model\""));
            assertEquals(1, recorder.records.size());
            TokenUsageRecord record = recorder.records.get(0);
            assertEquals("user-42", record.userId());
            assertEquals("conversation-a", record.conversationId());
            assertEquals("CHAT", record.businessType());
            assertEquals("business-1", record.businessId());
            assertEquals("test-model", record.modelName());
            assertEquals(true, record.streaming());
            assertEquals(7, record.promptTokens());
            assertEquals(8, record.completionTokens());
            assertEquals(15, record.totalTokens());
            assertTrue(record.rawRequestJson().contains("\"stream\":true"));
            assertFalse(record.requestHash().isBlank());
            assertEquals("SUCCESS", record.status());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recordsNonStreamingTokenUsageAndRawRequest() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> respondWithChatCompletion(exchange, requestBody));
        server.start();
        CapturingTokenUsageRecorder recorder = new CapturingTokenUsageRecorder();
        try {
            AgentScopeChatTextModel model = new AgentScopeChatTextModel(
                    "test-api-key",
                    "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    new OpenAiRequestLogger(),
                    recorder,
                    clock);

            String answer = TokenUsageContextHolder.callWithContext(
                    new TokenUsageContext("user-42", "conversation-a", "CHAT", null),
                    () -> model.generate("system", "user"));

            assertEquals("answer", answer);
            assertTrue(requestBody.get().contains("\"stream\":false"));
            assertEquals(1, recorder.records.size());
            TokenUsageRecord record = recorder.records.get(0);
            assertEquals("chatcmpl-1", record.responseId());
            assertEquals(3, record.promptTokens());
            assertEquals(4, record.completionTokens());
            assertEquals(7, record.totalTokens());
            assertEquals("SUCCESS", record.status());
            assertTrue(record.rawRequestJson().contains("\"messages\""));
            assertTrue(record.usageJson().contains("\"total_tokens\":7"));
        } finally {
            server.stop(0);
        }
    }

    private void respondWithStream(
            HttpExchange exchange,
            AtomicReference<String> requestBody) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = """
                data: {"choices":[{"delta":{"content":"hello"}}]}

                data: {"choices":[{"delta":{"content":" world"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":8,"total_tokens":15}}

                data: [DONE]

                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void respondWithChatCompletion(
            HttpExchange exchange,
            AtomicReference<String> requestBody) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = """
                {
                  "id": "chatcmpl-1",
                  "choices": [
                    {"message": {"content": "answer"}}
                  ],
                  "usage": {
                    "prompt_tokens": 3,
                    "completion_tokens": 4,
                    "total_tokens": 7
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static final class CapturingTokenUsageRecorder implements TokenUsageRecorder {

        private final List<TokenUsageRecord> records = new ArrayList<>();

        @Override
        public void record(TokenUsageRecord record) {
            records.add(record);
        }
    }
}
