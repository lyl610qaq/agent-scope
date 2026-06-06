package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryOrchestratorTest {

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
                fixedClock());

        MemoryContext context = orchestrator.prepare("conversation-a", "question");

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
                        "用户偏好中文回答",
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
                fixedClock());

        orchestrator.recordTurn("conversation-a", "hello", "你好");

        assertEquals(1, shortTerm.turns.size());
        assertEquals(1, longTerm.saved.size());
        assertEquals("用户偏好中文回答", longTerm.saved.get(0).text());
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
        public void append(String conversationId, MemoryTurn turn) {
        }

        @Override
        public List<MemoryTurn> recent(String conversationId) {
            return turns;
        }
    }

    private static final class CapturingShortTermStore implements ShortTermMemoryStore {
        private final List<MemoryTurn> turns = new ArrayList<>();

        @Override
        public void append(String conversationId, MemoryTurn turn) {
            turns.add(turn);
        }

        @Override
        public List<MemoryTurn> recent(String conversationId) {
            return List.copyOf(turns);
        }
    }

    private static final class FailingLongTermRepository implements LongTermMemoryRepository {
        @Override
        public List<LongTermMemory> findRelevant(String query) {
            throw new IllegalStateException("file unavailable");
        }

        @Override
        public void save(String conversationId, LongTermMemoryCandidate candidate) {
        }
    }

    private static final class CapturingLongTermRepository implements LongTermMemoryRepository {
        private final List<LongTermMemoryCandidate> saved = new ArrayList<>();

        @Override
        public List<LongTermMemory> findRelevant(String query) {
            return List.of();
        }

        @Override
        public void save(String conversationId, LongTermMemoryCandidate candidate) {
            saved.add(candidate);
        }
    }
}
