package com.example.demoscope;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentRuntimeConfigController {

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final boolean ragEnabled;
    private final String ragKnowledgeDir;
    private final int ragTopK;

    public AgentRuntimeConfigController(
            @Value("${agentscope.openai.api-key:}") String apiKey,
            @Value("${agentscope.openai.model-name:Pro/zai-org/GLM-4.7}") String modelName,
            @Value("${agentscope.openai.base-url:}") String baseUrl,
            @Value("${agentscope.rag.enabled:true}") boolean ragEnabled,
            @Value("${agentscope.rag.knowledge-dir:data/knowledge}") String ragKnowledgeDir,
            @Value("${agentscope.rag.top-k:3}") int ragTopK) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.ragEnabled = ragEnabled;
        this.ragKnowledgeDir = ragKnowledgeDir;
        this.ragTopK = ragTopK;
    }

    @GetMapping("/api/config")
    public AgentRuntimeConfigResponse config() {
        return new AgentRuntimeConfigResponse(
                modelName,
                baseUrl,
                StringUtils.hasText(apiKey),
                ragEnabled,
                ragKnowledgeDir,
                ragTopK);
    }

    public record AgentRuntimeConfigResponse(
            String modelName,
            String baseUrl,
            boolean apiKeyConfigured,
            boolean ragEnabled,
            String ragKnowledgeDir,
            int ragTopK) {
    }
}
