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
    private final String embeddingApiKey;
    private final String embeddingModel;
    private final boolean pgVectorEnabled;
    private final String pgVectorUrl;
    private final String ragKnowledgeDir;
    private final int ragTopK;
    private final boolean ruoyiAuthEnabled;
    private final String ruoyiTokenName;
    private final int longTermMemoryTopK;

    public AgentRuntimeConfigController(
            @Value("${agentscope.openai.api-key:}") String apiKey,
            @Value("${agentscope.openai.model-name:Pro/zai-org/GLM-4.7}") String modelName,
            @Value("${agentscope.openai.base-url:}") String baseUrl,
            @Value("${agentscope.embedding.api-key:}") String embeddingApiKey,
            @Value("${agentscope.embedding.model:Qwen/Qwen3-Embedding-4B}") String embeddingModel,
            @Value("${agentscope.pgvector.enabled:false}") boolean pgVectorEnabled,
            @Value("${agentscope.pgvector.url:}") String pgVectorUrl,
            @Value("${agentscope.rag.knowledge-dir:data/knowledge}") String ragKnowledgeDir,
            @Value("${agentscope.rag.top-k:3}") int ragTopK,
            @Value("${agentscope.auth.ruoyi.enabled:true}") boolean ruoyiAuthEnabled,
            @Value("${agentscope.auth.ruoyi.token-name:Authorization}") String ruoyiTokenName,
            @Value("${agentscope.memory.long-term.top-k:5}") int longTermMemoryTopK) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.embeddingApiKey = embeddingApiKey;
        this.embeddingModel = embeddingModel;
        this.pgVectorEnabled = pgVectorEnabled;
        this.pgVectorUrl = pgVectorUrl;
        this.ragKnowledgeDir = ragKnowledgeDir;
        this.ragTopK = ragTopK;
        this.ruoyiAuthEnabled = ruoyiAuthEnabled;
        this.ruoyiTokenName = ruoyiTokenName;
        this.longTermMemoryTopK = longTermMemoryTopK;
    }

    @GetMapping("/api/config")
    public AgentRuntimeConfigResponse config() {
        return new AgentRuntimeConfigResponse(
                modelName,
                baseUrl,
                StringUtils.hasText(apiKey),
                embeddingModel,
                StringUtils.hasText(embeddingApiKey),
                pgVectorEnabled,
                pgVectorEnabled && StringUtils.hasText(pgVectorUrl),
                ragKnowledgeDir,
                ragTopK,
                ruoyiAuthEnabled,
                ruoyiTokenName,
                longTermMemoryTopK);
    }

    public record AgentRuntimeConfigResponse(
            String modelName,
            String baseUrl,
            boolean apiKeyConfigured,
            String embeddingModel,
            boolean embeddingApiKeyConfigured,
            boolean pgVectorEnabled,
            boolean pgVectorConfigured,
            String ragKnowledgeDir,
            int ragTopK,
            boolean ruoyiAuthEnabled,
            String ruoyiTokenName,
            int longTermMemoryTopK) {
    }
}
