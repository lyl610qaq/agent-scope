package com.example.demoscope.service.chat;

import com.example.demoscope.biz.chat.PromptContextBuilder;
import com.example.demoscope.common.llm.ChatTextModel;
import com.example.demoscope.service.memory.MemoryOrchestrator;
import com.example.demoscope.domain.memory.MemoryContext;
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
