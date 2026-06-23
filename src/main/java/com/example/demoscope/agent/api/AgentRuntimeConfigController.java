package com.example.demoscope.agent.api;

import com.example.demoscope.knowledge.domain.RetrievalSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentRuntimeConfigController {

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final RetrievalSettings knowledgeSettings;
    private final RetrievalSettings longTermMemorySettings;
    private final String tokenHeaderName;

    public AgentRuntimeConfigController(
            @Value("${agentscope.openai.api-key:}") String apiKey,
            @Value("${agentscope.openai.model-name:Pro/zai-org/GLM-4.7}") String modelName,
            @Value("${agentscope.openai.base-url:}") String baseUrl,
            @Qualifier("knowledgeRetrievalSettings") RetrievalSettings knowledgeSettings,
            @Qualifier("longTermMemoryRetrievalSettings") RetrievalSettings longTermMemorySettings,
            @Value("${agentscope.auth.ruoyi.token-name:Authorization}") String tokenHeaderName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.knowledgeSettings = knowledgeSettings;
        this.longTermMemorySettings = longTermMemorySettings;
        this.tokenHeaderName = tokenHeaderName;
    }

    @GetMapping("/api/config")
    public AgentRuntimeConfigResponse config() {
        return new AgentRuntimeConfigResponse(
                modelName,
                baseUrl,
                StringUtils.hasText(apiKey),
                RetrievalConfigResponse.from(knowledgeSettings),
                RetrievalConfigResponse.from(longTermMemorySettings),
                new RuoyiAuthConfigResponse(tokenHeaderName));
    }

    public record AgentRuntimeConfigResponse(
            String modelName,
            String baseUrl,
            boolean apiKeyConfigured,
            RetrievalConfigResponse knowledge,
            RetrievalConfigResponse longTermMemory,
            RuoyiAuthConfigResponse ruoyiAuth) {
    }

    public record RetrievalConfigResponse(
            int vectorTopK,
            int finalTopN,
            double minScore) {

        static RetrievalConfigResponse from(RetrievalSettings settings) {
            return new RetrievalConfigResponse(
                    settings.vectorTopK(),
                    settings.finalTopN(),
                    settings.minScore());
        }
    }

    public record RuoyiAuthConfigResponse(
            String tokenHeaderName) {
    }
}
