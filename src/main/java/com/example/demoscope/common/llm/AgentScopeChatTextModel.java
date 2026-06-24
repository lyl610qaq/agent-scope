package com.example.demoscope.common.llm;

import com.example.demoscope.common.llm.ChatTextModel;
import com.example.demoscope.common.llm.OpenAiRequestLogger;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.util.StringUtils;

public class AgentScopeChatTextModel implements ChatTextModel {

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final OpenAiRequestLogger requestLogger;

    public AgentScopeChatTextModel(
            String apiKey,
            String modelName,
            String baseUrl,
            OpenAiRequestLogger requestLogger) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.requestLogger = requestLogger;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured.");
        }

        requestLogger.logChatRequest(apiKey, baseUrl, modelName, userPrompt);

        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);

        if (StringUtils.hasText(baseUrl)) {
            modelBuilder.baseUrl(baseUrl);
        }

        ReActAgent agent = ReActAgent.builder()
                .name("assistant")
                .sysPrompt(systemPrompt)
                .model(modelBuilder.build())
                .build();

        Msg response = agent.call(Msg.builder()
                        .name("user")
                        .role(MsgRole.USER)
                        .textContent(userPrompt)
                        .build())
                .block();

        return response == null ? "" : response.getTextContent();
    }
}
