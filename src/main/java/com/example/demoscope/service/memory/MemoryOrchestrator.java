package com.example.demoscope.service.memory;

import com.example.demoscope.biz.memory.LongTermMemoryPolicy;
import com.example.demoscope.common.embedding.EmbeddingClient;
import com.example.demoscope.domain.rag.KnowledgeChunk;
import com.example.demoscope.domain.rag.KnowledgeRetriever;
import com.example.demoscope.domain.rag.SemanticQuery;
import com.example.demoscope.domain.memory.LongTermMemory;
import com.example.demoscope.domain.memory.LongTermMemoryCandidate;
import com.example.demoscope.domain.memory.LongTermMemoryExtractor;
import com.example.demoscope.domain.memory.LongTermMemoryRepository;
import com.example.demoscope.domain.memory.MemoryContext;
import com.example.demoscope.domain.memory.MemoryTurn;
import com.example.demoscope.domain.memory.ShortTermMemoryStore;
import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemoryOrchestrator.class);

    private final ShortTermMemoryStore shortTermMemoryStore;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final KnowledgeRetriever knowledgeRetriever;
    private final LongTermMemoryExtractor longTermMemoryExtractor;
    private final LongTermMemoryPolicy longTermMemoryPolicy;
    private final EmbeddingClient embeddingClient;
    private final Clock clock;

    public MemoryOrchestrator(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            KnowledgeRetriever knowledgeRetriever,
            LongTermMemoryExtractor longTermMemoryExtractor,
            LongTermMemoryPolicy longTermMemoryPolicy,
            EmbeddingClient embeddingClient,
            Clock clock) {
        this.shortTermMemoryStore = shortTermMemoryStore;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.knowledgeRetriever = knowledgeRetriever;
        this.longTermMemoryExtractor = longTermMemoryExtractor;
        this.longTermMemoryPolicy = longTermMemoryPolicy;
        this.embeddingClient = embeddingClient;
        this.clock = clock;
    }

    public MemoryContext prepare(String userId, String conversationId, String query) {
        List<MemoryTurn> shortTerm = safelyReadShortTerm(userId, conversationId);
        SemanticQuery semanticQuery;
        try {
            semanticQuery = new SemanticQuery(query, embeddingClient.embed(query));
        } catch (RuntimeException ex) {
            log.warn("Failed to embed retrieval query", ex);
            return new MemoryContext(shortTerm, List.of(), List.of());
        }

        List<LongTermMemory> longTerm = safelyReadLongTerm(userId, semanticQuery);
        List<KnowledgeChunk> knowledge = safelyRetrieveKnowledge(semanticQuery);
        return new MemoryContext(shortTerm, longTerm, knowledge);
    }

    public void recordTurn(
            String userId,
            String conversationId,
            String userMessage,
            String assistantMessage) {
        MemoryTurn turn = new MemoryTurn(userMessage, assistantMessage, clock.instant());
        try {
            shortTermMemoryStore.append(userId, conversationId, turn);
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to append short-term memory for user {} conversation {}",
                    userId,
                    conversationId,
                    ex);
        }

        List<LongTermMemoryCandidate> candidates;
        try {
            candidates = longTermMemoryExtractor.extract(turn);
        } catch (RuntimeException ex) {
            log.warn("Failed to extract long-term memory candidates", ex);
            return;
        }

        for (LongTermMemoryCandidate candidate : candidates) {
            if (!longTermMemoryPolicy.isAllowed(candidate)) {
                continue;
            }
            try {
                longTermMemoryRepository.save(userId, conversationId, candidate);
            } catch (RuntimeException ex) {
                log.warn("Failed to persist long-term memory candidate", ex);
            }
        }
    }

    private List<MemoryTurn> safelyReadShortTerm(String userId, String conversationId) {
        try {
            return shortTermMemoryStore.recent(userId, conversationId);
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to read short-term memory for user {} conversation {}",
                    userId,
                    conversationId,
                    ex);
            return List.of();
        }
    }

    private List<LongTermMemory> safelyReadLongTerm(String userId, SemanticQuery query) {
        try {
            return longTermMemoryRepository.findRelevant(userId, query);
        } catch (RuntimeException ex) {
            log.warn("Failed to read long-term memory", ex);
            return List.of();
        }
    }

    private List<KnowledgeChunk> safelyRetrieveKnowledge(SemanticQuery query) {
        try {
            return knowledgeRetriever.retrieve(query);
        } catch (RuntimeException ex) {
            log.warn("Failed to retrieve knowledge context", ex);
            return List.of();
        }
    }
}
