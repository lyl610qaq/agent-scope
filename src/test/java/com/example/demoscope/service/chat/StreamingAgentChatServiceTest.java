package com.example.demoscope.service.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demoscope.biz.chat.PromptContextBuilder;
import com.example.demoscope.common.llm.StreamingChatTextModel;
import com.example.demoscope.common.llm.TokenUsageContext;
import com.example.demoscope.common.llm.TokenUsageContextHolder;
import com.example.demoscope.domain.memory.MemoryContext;
import com.example.demoscope.domain.rag.KnowledgeChunk;
import com.example.demoscope.service.memory.MemoryOrchestrator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StreamingAgentChatServiceTest {

    @Test
    void streamsModelDeltasAndRecordsCompletedAnswer() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.prepare("user-42", "conversation-a", "question"))
                .thenReturn(new MemoryContext(
                        List.of(),
                        List.of(),
                        List.of(new KnowledgeChunk("guide.md", "AgentScope knowledge"))));

        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicReference<String> userPrompt = new AtomicReference<>();
        AtomicReference<TokenUsageContext> tokenUsageContext = new AtomicReference<>();
        StreamingChatTextModel model = new StreamingChatTextModel() {
            @Override
            public void generateStream(String system, String user, java.util.function.Consumer<String> onDelta) {
                systemPrompt.set(system);
                userPrompt.set(user);
                tokenUsageContext.set(TokenUsageContextHolder.current());
                onDelta.accept("hello");
                onDelta.accept(" world");
            }

            @Override
            public String generate(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }
        };
        StreamingAgentChatService service = new StreamingAgentChatService(
                model,
                orchestrator,
                new PromptContextBuilder(),
                "You are a helpful AI assistant.");

        List<String> deltas = new ArrayList<>();
        String answer = service.chat("user-42", "conversation-a", "question", deltas::add);

        assertEquals("hello world", answer);
        assertEquals(List.of("hello", " world"), deltas);
        assertEquals("You are a helpful AI assistant.", systemPrompt.get());
        assertTrue(userPrompt.get().contains("AgentScope knowledge"));
        assertEquals("CHAT", tokenUsageContext.get().businessType());
        assertEquals("user-42", tokenUsageContext.get().userId());
        assertEquals("conversation-a", tokenUsageContext.get().conversationId());
        verify(orchestrator).recordTurn("user-42", "conversation-a", "question", "hello world");
    }

    @Test
    void doesNotRecordTurnWhenStreamingFails() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.prepare("user-42", "conversation-a", "question"))
                .thenReturn(new MemoryContext(List.of(), List.of(), List.of()));
        StreamingChatTextModel model = new StreamingChatTextModel() {
            @Override
            public void generateStream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta) {
                onDelta.accept("partial");
                throw new IllegalStateException("model unavailable");
            }

            @Override
            public String generate(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }
        };
        StreamingAgentChatService service = new StreamingAgentChatService(
                model,
                orchestrator,
                new PromptContextBuilder(),
                "You are a helpful AI assistant.");

        assertThrows(
                IllegalStateException.class,
                () -> service.chat("user-42", "conversation-a", "question", ignored -> {
                }));

        verify(orchestrator, never()).recordTurn(
                anyString(),
                anyString(),
                anyString(),
                anyString());
    }
}
