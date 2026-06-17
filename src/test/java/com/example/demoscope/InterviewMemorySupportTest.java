package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InterviewMemorySupportTest {

    @Test
    void memoryProviderReadsShortAndLongTermMemoryForInterviewKey() {
        CapturingShortTermMemoryStore shortTerm = new CapturingShortTermMemoryStore();
        CapturingLongTermMemoryRepository longTerm =
                new CapturingLongTermMemoryRepository();
        InterviewSnapshot snapshot = snapshot();
        shortTerm.turns.add(new MemoryTurn(
                "interview-memory",
                "previous note",
                Instant.EPOCH));
        longTerm.memories.add(new LongTermMemory(
                "memory-1",
                "user-42",
                LongTermMemoryCategory.STABLE_FACT,
                "stable memory",
                "conversation",
                0.9,
                Instant.EPOCH,
                Instant.EPOCH));
        InterviewMemoryContextProvider provider =
                new InterviewMemoryContextProvider(
                        shortTerm,
                        longTerm,
                        input -> new float[] {0.1f});

        assertEquals(List.of("previous note"), provider.shortTerm(snapshot)
                .stream()
                .map(MemoryTurn::assistantMessage)
                .toList());
        assertEquals(List.of("stable memory"), provider.longTerm(snapshot)
                .stream()
                .map(LongTermMemory::text)
                .toList());
        assertEquals("user-42", shortTerm.lastUserId);
        assertEquals("interview:" + snapshot.session().id(), shortTerm.lastConversationId);
        assertEquals("user-42", longTerm.lastUserId);
    }

    @Test
    void memoryWriterAppliesPolicyAndUsesInterviewKey() {
        CapturingShortTermMemoryStore shortTerm = new CapturingShortTermMemoryStore();
        CapturingLongTermMemoryRepository longTerm =
                new CapturingLongTermMemoryRepository();
        DefaultInterviewMemoryWriter writer = new DefaultInterviewMemoryWriter(
                shortTerm,
                longTerm,
                new LongTermMemoryPolicy(),
                Clock.fixed(Instant.parse("2026-06-16T10:00:00Z"), ZoneOffset.UTC));
        InterviewSnapshot snapshot = snapshot();

        writer.write(
                snapshot,
                new MemoryWriteDecision(
                        List.of("candidate needs hashmap follow-up"),
                        List.of("safe long memory", "Bearer secret-token"),
                        "continuity"));

        assertEquals("user-42", shortTerm.lastUserId);
        assertEquals("interview:" + snapshot.session().id(), shortTerm.lastConversationId);
        assertEquals(List.of("candidate needs hashmap follow-up"), shortTerm.appended
                .stream()
                .map(MemoryTurn::assistantMessage)
                .toList());
        assertEquals(List.of("safe long memory"), longTerm.saved
                .stream()
                .map(LongTermMemoryCandidate::text)
                .toList());
    }

    private InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000801");
        InterviewSession session = new InterviewSession(
                interviewId,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.IN_PROGRESS,
                1,
                null,
                0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        return new InterviewSnapshot(session, List.of(), List.of(), null);
    }

    private static final class CapturingShortTermMemoryStore
            implements ShortTermMemoryStore {

        private final List<MemoryTurn> turns = new ArrayList<>();
        private final List<MemoryTurn> appended = new ArrayList<>();
        private String lastUserId;
        private String lastConversationId;

        @Override
        public void append(String userId, String conversationId, MemoryTurn turn) {
            lastUserId = userId;
            lastConversationId = conversationId;
            appended.add(turn);
        }

        @Override
        public List<MemoryTurn> recent(String userId, String conversationId) {
            lastUserId = userId;
            lastConversationId = conversationId;
            return List.copyOf(turns);
        }
    }

    private static final class CapturingLongTermMemoryRepository
            implements LongTermMemoryRepository {

        private final List<LongTermMemory> memories = new ArrayList<>();
        private final List<LongTermMemoryCandidate> saved = new ArrayList<>();
        private String lastUserId;

        @Override
        public List<LongTermMemory> findRelevant(String userId, SemanticQuery query) {
            lastUserId = userId;
            return List.copyOf(memories);
        }

        @Override
        public void save(
                String userId,
                String conversationId,
                LongTermMemoryCandidate candidate) {
            lastUserId = userId;
            saved.add(candidate);
        }
    }
}
