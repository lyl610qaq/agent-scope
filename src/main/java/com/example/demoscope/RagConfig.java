package com.example.demoscope;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    LocalKnowledgeStore localKnowledgeStore(
            @Value("${agentscope.rag.knowledge-dir:data/knowledge}") String knowledgeDir,
            @Value("${agentscope.rag.top-k:3}") int topK,
            @Value("${agentscope.rag.max-chunk-chars:2000}") int maxChunkChars,
            @Value("${agentscope.rag.min-score:1}") int minScore) {
        return new LocalKnowledgeStore(knowledgeDir, topK, maxChunkChars, minScore);
    }

    @Bean
    RagPromptBuilder ragPromptBuilder(@Value("${agentscope.rag.enabled:true}") boolean enabled) {
        return new RagPromptBuilder(enabled);
    }
}
