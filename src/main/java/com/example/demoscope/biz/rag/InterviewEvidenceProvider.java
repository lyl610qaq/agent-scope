package com.example.demoscope.biz.rag;

import com.example.demoscope.biz.interview.RagQueryPlan;
import com.example.demoscope.common.embedding.EmbeddingClient;
import com.example.demoscope.domain.rag.KnowledgeChunk;
import com.example.demoscope.domain.rag.KnowledgeRetriever;
import com.example.demoscope.domain.rag.SemanticQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public List<KnowledgeChunk> retrieve(
            RagQueryPlan plan,
            int maxEvidence) {
        if (maxEvidence <= 0) {
            return List.of();
        }
        Map<String, KnowledgeChunk> chunksBySource = new LinkedHashMap<>();
        for (RagQueryPlan.Query query : plan.queries()) {
            try {
                float[] embedding = embeddingClient.embed(query.query());
                for (KnowledgeChunk chunk : knowledgeRetriever.retrieve(
                        new SemanticQuery(query.query(), embedding))) {
                    chunksBySource.putIfAbsent(chunk.source(), chunk);
                    if (chunksBySource.size() >= maxEvidence) {
                        return List.copyOf(chunksBySource.values());
                    }
                }
            } catch (RuntimeException exception) {
                log.warn("Interview planned evidence retrieval failed");
            }
        }
        return List.copyOf(chunksBySource.values());
    }
}
