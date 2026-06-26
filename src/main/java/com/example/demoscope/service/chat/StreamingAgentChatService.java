package com.example.demoscope.service.chat;

import com.example.demoscope.biz.chat.PromptContextBuilder;
import com.example.demoscope.common.llm.StreamingChatTextModel;
import com.example.demoscope.common.llm.TokenUsageContext;
import com.example.demoscope.common.llm.TokenUsageContextHolder;
import com.example.demoscope.domain.memory.MemoryContext;
import com.example.demoscope.service.memory.MemoryOrchestrator;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StreamingAgentChatService {

    private final StreamingChatTextModel chatTextModel;
    private final MemoryOrchestrator memoryOrchestrator;
    private final PromptContextBuilder promptContextBuilder;
    private final String systemPrompt;

    public StreamingAgentChatService(
            StreamingChatTextModel chatTextModel,
            MemoryOrchestrator memoryOrchestrator,
            PromptContextBuilder promptContextBuilder,
            @Value("${agentscope.chat.system-prompt:You are a helpful AI assistant.}") String systemPrompt) {
        this.chatTextModel = chatTextModel;
        this.memoryOrchestrator = memoryOrchestrator;
        this.promptContextBuilder = promptContextBuilder;
        this.systemPrompt = systemPrompt;
    }

    public String chat(
            String userId,
            String conversationId,
            String message,
            Consumer<String> onDelta) {
        Objects.requireNonNull(onDelta, "onDelta");
        return TokenUsageContextHolder.callWithContext(
                new TokenUsageContext(userId, conversationId, "CHAT", null),
                () -> {
                    MemoryContext memoryContext = memoryOrchestrator.prepare(userId, conversationId, message);
                    String modelPrompt = promptContextBuilder.build(systemPrompt, memoryContext, message);
                    StringBuilder answer = new StringBuilder();

                    chatTextModel.generateStream(systemPrompt, modelPrompt, delta -> {
                        answer.append(delta);
                        onDelta.accept(delta);
                    });

                    String completedAnswer = answer.toString();
                    memoryOrchestrator.recordTurn(userId, conversationId, message, completedAnswer);
                    return completedAnswer;
                });
    }
}
