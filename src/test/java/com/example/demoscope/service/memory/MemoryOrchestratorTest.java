package com.example.demoscope.service.memory;

import com.example.demoscope.common.embedding.EmbeddingClient;
import com.example.demoscope.domain.rag.KnowledgeChunk;
import com.example.demoscope.domain.rag.KnowledgeRetriever;
import com.example.demoscope.domain.rag.SemanticQuery;
import com.example.demoscope.biz.memory.LongTermMemoryPolicy;
import com.example.demoscope.service.memory.MemoryOrchestrator;
import com.example.demoscope.domain.memory.LongTermMemory;
import com.example.demoscope.domain.memory.LongTermMemoryCandidate;
import com.example.demoscope.domain.memory.LongTermMemoryCategory;
import com.example.demoscope.domain.memory.LongTermMemoryExtractor;
import com.example.demoscope.domain.memory.LongTermMemoryRepository;
import com.example.demoscope.domain.memory.MemoryContext;
import com.example.demoscope.domain.memory.MemoryTurn;
import com.example.demoscope.domain.memory.ShortTermMemoryStore;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class MemoryOrchestratorTest {

    @Test
    void preparesContextUsingAuthenticatedUserId() {
        CapturingShortTermStore shortTerm = new CapturingShortTermStore();
        CapturingLongTermRepository longTerm = new CapturingLongTermRepository();
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(
                shortTerm,
                longTerm,
                query -> List.of(),
                turn -> List.of(),
                new LongTermMemoryPolicy(),
                input -> new float[] {0.1f},
                fixedClock());

        orchestrator.prepare("user-42", "conversation-a", "favorite language?");

        assertEquals("user-42", shortTerm.lastUserId);
        assertEquals("conversation-a", shortTerm.lastConversationId);
        assertEquals("user-42", longTerm.lastUserId);
        assertEquals("favorite language?", longTerm.lastQuery.text());
    }

    @Test
    void degradesFailedLongTermAndKnowledgeReadsToEmptyContext() {
        MemoryTurn turn = new MemoryTurn("old", "answer", Instant.parse("2026-06-06T10:00:00Z"));
        ShortTermMemoryStore shortTerm = new FixedShortTermStore(List.of(turn));
        LongTermMemoryRepository longTerm = new FailingLongTermRepository();
        KnowledgeRetriever knowledge = query -> {
            throw new IllegalStateException("database unavailable");
        };

        MemoryOrchestrator orchestrator = new MemoryOrchestrator(
                shortTerm,
                longTerm,
                knowledge,
                ignored -> List.of(),
                new LongTermMemoryPolicy(),
                input -> new float[] {0.1f},
                fixedClock());

        MemoryContext context = orchestrator.prepare("user-42", "conversation-a", "question");

        assertEquals(List.of(turn), context.shortTermTurns());
        assertEquals(List.of(), context.longTermMemories());
        assertEquals(List.of(), context.knowledgeChunks());
    }

    @Test
    void recordsTurnAndOnlyPersistsAllowedCandidates() {
        CapturingShortTermStore shortTerm = new CapturingShortTermStore();
        CapturingLongTermRepository longTerm = new CapturingLongTermRepository();
        LongTermMemoryExtractor extractor = ignored -> List.of(
                new LongTermMemoryCandidate(
                        LongTermMemoryCategory.PREFERENCE,
                        "user prefers concise answers",
                        0.9),
                new LongTermMemoryCandidate(
                        LongTermMemoryCategory.COMMON_CONFIG,
                        "api_key=secret",
                        0.9));
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(
                shortTerm,
                longTerm,
                query -> List.of(),
                extractor,
                new LongTermMemoryPolicy(),
                input -> new float[] {0.1f},
                fixedClock());

        orchestrator.recordTurn("user-42", "conversation-a", "hello", "hello");

        assertEquals(1, shortTerm.turns.size());
        assertEquals("user-42", shortTerm.lastUserId);
        assertEquals("conversation-a", shortTerm.lastConversationId);
        assertEquals(1, longTerm.saved.size());
        assertEquals("user-42", longTerm.savedUserId);
        assertEquals("conversation-a", longTerm.savedConversationId);
        assertEquals("user prefers concise answers", longTerm.saved.get(0).text());
    }

    @Test
    void createsOneEmbeddingForBothSemanticSources() {
        AtomicInteger embeddingCalls = new AtomicInteger();
        AtomicReference<SemanticQuery> knowledgeQuery = new AtomicReference<>();
        CapturingLongTermRepository longTerm = new CapturingLongTermRepository();
        EmbeddingClient embeddingClient = input -> {
            embeddingCalls.incrementAndGet();
            return new float[] {0.2f, 0.8f};
        };
        KnowledgeRetriever knowledge = query -> {
            knowledgeQuery.set(query);
            return List.of();
        };
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(
                new CapturingShortTermStore(),
                longTerm,
                knowledge,
                turn -> List.of(),
                new LongTermMemoryPolicy(),
                embeddingClient,
                fixedClock());

        orchestrator.prepare("user-42", "conversation-a", "question");

        assertEquals(1, embeddingCalls.get());
        assertArrayEquals(
                knowledgeQuery.get().embedding(),
                longTerm.lastQuery.embedding());
    }

    @Test
    void keepsShortTermMemoryWhenEmbeddingFails() {
        MemoryTurn turn = new MemoryTurn("old", "answer", Instant.parse("2026-06-06T10:00:00Z"));
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(
                new FixedShortTermStore(List.of(turn)),
                new CapturingLongTermRepository(),
                query -> List.of(new KnowledgeChunk("guide.md", "content")),
                ignored -> List.of(),
                new LongTermMemoryPolicy(),
                input -> {
                    throw new IllegalStateException("embedding unavailable");
                },
                fixedClock());

        MemoryContext context = orchestrator.prepare(
                "user-42",
                "conversation-a",
                "question");

        assertEquals(List.of(turn), context.shortTermTurns());
        assertEquals(List.of(), context.longTermMemories());
        assertEquals(List.of(), context.knowledgeChunks());
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
    }

    private static final class FixedShortTermStore implements ShortTermMemoryStore {
        private final List<MemoryTurn> turns;

        private FixedShortTermStore(List<MemoryTurn> turns) {
            this.turns = turns;
        }

        @Override
        public void append(String userId, String conversationId, MemoryTurn turn) {
        }

        @Override
        public List<MemoryTurn> recent(String userId, String conversationId) {
            return turns;
        }
    }

    private static final class CapturingShortTermStore implements ShortTermMemoryStore {
        private final List<MemoryTurn> turns = new ArrayList<>();
        private String lastUserId;
        private String lastConversationId;

        @Override
        public void append(String userId, String conversationId, MemoryTurn turn) {
            this.lastUserId = userId;
            this.lastConversationId = conversationId;
            turns.add(turn);
        }

        @Override
        public List<MemoryTurn> recent(String userId, String conversationId) {
            this.lastUserId = userId;
            this.lastConversationId = conversationId;
            return List.copyOf(turns);
        }
    }

    private static final class FailingLongTermRepository implements LongTermMemoryRepository {
        @Override
        public List<LongTermMemory> findRelevant(String userId, SemanticQuery query) {
            throw new IllegalStateException("file unavailable");
        }

        @Override
        public void save(String userId, String conversationId, LongTermMemoryCandidate candidate) {
        }
    }

    private static final class CapturingLongTermRepository implements LongTermMemoryRepository {
        private final List<LongTermMemoryCandidate> saved = new ArrayList<>();
        private String lastUserId;
        private SemanticQuery lastQuery;
        private String savedUserId;
        private String savedConversationId;

        @Override
        public List<LongTermMemory> findRelevant(String userId, SemanticQuery query) {
            this.lastUserId = userId;
            this.lastQuery = query;
            return List.of();
        }

        @Override
        public void save(String userId, String conversationId, LongTermMemoryCandidate candidate) {
            this.savedUserId = userId;
            this.savedConversationId = conversationId;
            saved.add(candidate);
        }
    }
}
