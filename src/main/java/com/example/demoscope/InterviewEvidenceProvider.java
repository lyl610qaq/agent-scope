package com.example.demoscope;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterviewEvidenceProvider {

    private static final Logger log =
            LoggerFactory.getLogger(InterviewEvidenceProvider.class);

    private final EmbeddingClient embeddingClient;
    private final KnowledgeRetriever knowledgeRetriever;

    public InterviewEvidenceProvider(
            EmbeddingClient embeddingClient,
            KnowledgeRetriever knowledgeRetriever) {
        this.embeddingClient = embeddingClient;
        this.knowledgeRetriever = knowledgeRetriever;
    }

    public List<KnowledgeChunk> retrieve(String query) {
        try {
            float[] embedding = embeddingClient.embed(query);
            return knowledgeRetriever.retrieve(
                    new SemanticQuery(query, embedding));
        } catch (RuntimeException exception) {
            log.warn("Interview evidence retrieval failed");
            return List.of();
        }
    }
}
