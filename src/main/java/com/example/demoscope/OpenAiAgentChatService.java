package com.example.demoscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpenAiAgentChatService implements AgentChatService {

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final OpenAiRequestLogger requestLogger;
    private final LocalKnowledgeStore knowledgeStore;
    private final RagPromptBuilder ragPromptBuilder;

    public OpenAiAgentChatService(
            @Value("${agentscope.openai.api-key:}") String apiKey,
            @Value("${agentscope.openai.model-name:Pro/zai-org/GLM-4.7}") String modelName,
            @Value("${agentscope.openai.base-url:}") String baseUrl,
            OpenAiRequestLogger requestLogger,
            LocalKnowledgeStore knowledgeStore,
            RagPromptBuilder ragPromptBuilder) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.requestLogger = requestLogger;
        this.knowledgeStore = knowledgeStore;
        this.ragPromptBuilder = ragPromptBuilder;
    }

    @Override
    public String chat(String conversationId, String message) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured.");
        }

        String modelMessage = ragPromptBuilder.build(message, knowledgeStore.retrieve(message));
        requestLogger.logChatRequest(apiKey, baseUrl, modelName, modelMessage);

        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);

        if (StringUtils.hasText(baseUrl)) {
            modelBuilder.baseUrl(baseUrl);
        }

        ReActAgent agent = ReActAgent.builder()
                .name("assistant")
                .sysPrompt("You are a helpful AI assistant.")
                .model(modelBuilder.build())
                .build();

        Msg response = agent.call(Msg.builder()
                        .name("user")
                        .role(MsgRole.USER)
                        .textContent(modelMessage)
                        .build())
                .block();

        return response == null ? "" : response.getTextContent();
    }
}
