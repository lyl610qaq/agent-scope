package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class OpenAiAgentChatServiceMemoryTest {

    @Test
    void preparesLayeredContextCallsModelAndRecordsTurn() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        MemoryContext context = new MemoryContext(
                List.of(),
                List.of(),
                List.of(new KnowledgeChunk("guide.md", "AgentScope knowledge")));
        when(orchestrator.prepare("user-42", "conversation-a", "question")).thenReturn(context);
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicReference<String> userPrompt = new AtomicReference<>();
        ChatTextModel model = (system, user) -> {
            systemPrompt.set(system);
            userPrompt.set(user);
            return "answer";
        };
        OpenAiAgentChatService service = new OpenAiAgentChatService(
                model,
                orchestrator,
                new PromptContextBuilder(),
                "You are a helpful AI assistant.");

        String answer = service.chat("user-42", "conversation-a", "question");

        assertEquals("answer", answer);
        assertEquals("You are a helpful AI assistant.", systemPrompt.get());
        assertTrue(userPrompt.get().contains("AgentScope knowledge"));
        verify(orchestrator).recordTurn("user-42", "conversation-a", "question", "answer");
    }

    @Test
    void doesNotRecordTurnWhenModelGenerationFails() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.prepare("user-42", "conversation-a", "question"))
                .thenReturn(new MemoryContext(List.of(), List.of(), List.of()));
        ChatTextModel model = (system, user) -> {
            throw new IllegalStateException("model unavailable");
        };
        OpenAiAgentChatService service = new OpenAiAgentChatService(
                model,
                orchestrator,
                new PromptContextBuilder(),
                "You are a helpful AI assistant.");

        assertThrows(
                IllegalStateException.class,
                () -> service.chat("user-42", "conversation-a", "question"));

        verify(orchestrator, never()).recordTurn(
                anyString(),
                anyString(),
                anyString(),
                anyString());
    }
}
