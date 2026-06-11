package com.example.demoscope;

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
    private final Clock clock;

    public MemoryOrchestrator(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            KnowledgeRetriever knowledgeRetriever,
            LongTermMemoryExtractor longTermMemoryExtractor,
            LongTermMemoryPolicy longTermMemoryPolicy,
            Clock clock) {
        this.shortTermMemoryStore = shortTermMemoryStore;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.knowledgeRetriever = knowledgeRetriever;
        this.longTermMemoryExtractor = longTermMemoryExtractor;
        this.longTermMemoryPolicy = longTermMemoryPolicy;
        this.clock = clock;
    }

    public MemoryContext prepare(String userId, String conversationId, String query) {
        List<MemoryTurn> shortTerm = safelyReadShortTerm(conversationId);
        List<LongTermMemory> longTerm = safelyReadLongTerm(userId, query);
        List<KnowledgeChunk> knowledge = safelyRetrieveKnowledge(query);
        return new MemoryContext(shortTerm, longTerm, knowledge);
    }

    public void recordTurn(
            String userId,
            String conversationId,
            String userMessage,
            String assistantMessage) {
        MemoryTurn turn = new MemoryTurn(userMessage, assistantMessage, clock.instant());
        try {
            shortTermMemoryStore.append(conversationId, turn);
        } catch (RuntimeException ex) {
            log.warn("Failed to append short-term memory for conversation {}", conversationId, ex);
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

    private List<MemoryTurn> safelyReadShortTerm(String conversationId) {
        try {
            return shortTermMemoryStore.recent(conversationId);
        } catch (RuntimeException ex) {
            log.warn("Failed to read short-term memory for conversation {}", conversationId, ex);
            return List.of();
        }
    }

    private List<LongTermMemory> safelyReadLongTerm(String userId, String query) {
        try {
            return longTermMemoryRepository.findRelevant(userId, query);
        } catch (RuntimeException ex) {
            log.warn("Failed to read long-term memory", ex);
            return List.of();
        }
    }

    private List<KnowledgeChunk> safelyRetrieveKnowledge(String query) {
        try {
            return knowledgeRetriever.retrieve(query);
        } catch (RuntimeException ex) {
            log.warn("Failed to retrieve knowledge context", ex);
            return List.of();
        }
    }
}
