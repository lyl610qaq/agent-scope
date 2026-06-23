package com.example.demoscope.agent.application;

import com.example.demoscope.knowledge.application.PromptContextBuilder;
import com.example.demoscope.llm.domain.ChatTextModel;
import com.example.demoscope.memory.application.MemoryOrchestrator;
import com.example.demoscope.memory.domain.MemoryContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiAgentChatService implements AgentChatService {

    private final ChatTextModel chatTextModel;
    private final MemoryOrchestrator memoryOrchestrator;
    private final PromptContextBuilder promptContextBuilder;
    private final String systemPrompt;

    public OpenAiAgentChatService(
            ChatTextModel chatTextModel,
            MemoryOrchestrator memoryOrchestrator,
            PromptContextBuilder promptContextBuilder,
            @Value("${agentscope.chat.system-prompt:You are a helpful AI assistant.}") String systemPrompt) {
        this.chatTextModel = chatTextModel;
        this.memoryOrchestrator = memoryOrchestrator;
        this.promptContextBuilder = promptContextBuilder;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String chat(String userId, String conversationId, String message) {
        MemoryContext memoryContext = memoryOrchestrator.prepare(userId, conversationId, message);
        String modelPrompt = promptContextBuilder.build(systemPrompt, memoryContext, message);
        String answer = chatTextModel.generate(systemPrompt, modelPrompt);
        memoryOrchestrator.recordTurn(userId, conversationId, message, answer);
        return answer;
    }
}
