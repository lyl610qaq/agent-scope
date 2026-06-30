package com.example.demoscope.service.runtime;

import com.example.demoscope.constant.auth.AuthConstants;
import com.example.demoscope.domain.rag.RetrievalSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentRuntimeConfigService {

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final RetrievalSettings knowledgeSettings;
    private final RetrievalSettings longTermMemorySettings;
    private final String tokenHeaderName;

    public AgentRuntimeConfigService(
            @Value("${agentscope.openai.api-key:}") String apiKey,
            @Value("${agentscope.openai.model-name:Pro/zai-org/GLM-4.7}") String modelName,
            @Value("${agentscope.openai.base-url:}") String baseUrl,
            @Qualifier("knowledgeRetrievalSettings") RetrievalSettings knowledgeSettings,
            @Qualifier("longTermMemoryRetrievalSettings") RetrievalSettings longTermMemorySettings,
            @Value("${"
                    + AuthConstants.RUOYI_TOKEN_NAME_PROPERTY
                    + ":"
                    + AuthConstants.DEFAULT_TOKEN_HEADER
                    + "}") String tokenHeaderName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.knowledgeSettings = knowledgeSettings;
        this.longTermMemorySettings = longTermMemorySettings;
        this.tokenHeaderName = tokenHeaderName;
    }

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

    public record RuoyiAuthConfigResponse(String tokenHeaderName) {
    }
}
