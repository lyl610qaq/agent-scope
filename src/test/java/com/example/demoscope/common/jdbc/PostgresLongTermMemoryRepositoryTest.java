package com.example.demoscope.common.jdbc;

import com.example.demoscope.common.embedding.EmbeddingClient;
import com.example.demoscope.domain.rag.SemanticQuery;
import com.example.demoscope.biz.memory.LongTermMemoryPolicy;
import com.example.demoscope.domain.memory.LongTermMemory;
import com.example.demoscope.domain.memory.LongTermMemoryCandidate;
import com.example.demoscope.domain.memory.LongTermMemoryCategory;
import com.example.demoscope.common.jdbc.PostgresLongTermMemoryRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

class PostgresLongTermMemoryRepositoryTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void initializesSchema() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        PostgresLongTermMemoryRepository repository = repository(jdbc, input -> new float[] {0.1f});

        repository.initializeSchema();

        verify(jdbc).execute("create extension if not exists vector");
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("embedding vector(768)"));
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("long_term_memories_active_user_idx"));
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("long_term_memories_active_unique_idx"));
    }

    @Test
    void insertsNewActiveVersion() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of());
        PostgresLongTermMemoryRepository repository = repository(jdbc, input -> new float[] {0.1f, 0.2f});

        repository.save("user-a", "conversation-a", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                " user prefers concise answers ",
                0.9));

        verify(jdbc).update(
                org.mockito.ArgumentMatchers.contains("insert into long_term_memories"),
                any(),
                eq("user-a"),
                any(),
                eq(1),
                eq(true),
                eq("PREFERENCE"),
                eq("user prefers concise answers"),
                eq("user prefers concise answers"),
                eq("conversation-a"),
                eq(0.9),
                eq("[0.1,0.2]"),
                eq(Instant.parse("2026-06-07T00:00:00Z")),
                eq(Instant.parse("2026-06-07T00:00:00Z")));
    }

    @Test
    void deactivatesOldVersionAndInsertsNextVersion() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        PostgresLongTermMemoryRepository.ActiveMemoryRow row =
                new PostgresLongTermMemoryRepository.ActiveMemoryRow(groupId, 2);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of(row));
        PostgresLongTermMemoryRepository repository = repository(jdbc, input -> new float[] {1.0f});

        repository.save("user-a", "conversation-b", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "user prefers concise answers",
                0.95));

        verify(jdbc).update(
                org.mockito.ArgumentMatchers.contains("set is_active = false"),
                org.mockito.ArgumentMatchers.eq(groupId));
        verify(jdbc).update(
                org.mockito.ArgumentMatchers.contains("insert into long_term_memories"),
                any(),
                eq("user-a"),
                eq(groupId),
                eq(3),
                eq(true),
                eq("PREFERENCE"),
                eq("user prefers concise answers"),
                eq("user prefers concise answers"),
                eq("conversation-b"),
                eq(0.95),
                eq("[1.0]"),
                eq(Instant.parse("2026-06-07T00:00:00Z")),
                eq(Instant.parse("2026-06-07T00:00:00Z")));
    }

    @Test
    void recallsActiveMemoriesForCurrentUserByVectorDistance() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of(memory()));
        PostgresLongTermMemoryRepository repository = repository(jdbc, input -> new float[] {0.4f, 0.5f});

        List<LongTermMemory> memories = repository.findRelevant(
                "user-a",
                new SemanticQuery("favorite language?", new float[] {0.4f, 0.5f}));

        assertEquals(1, memories.size());
        assertEquals("user prefers concise answers", memories.get(0).text());
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.contains("user_id = ? and is_active = true"),
                any(RowMapper.class),
                eq("[0.4,0.5]"),
                eq("user-a"),
                eq(5));
    }

    private LongTermMemory memory() {
        return new LongTermMemory(
                "00000000-0000-0000-0000-000000000002",
                "user-a",
                LongTermMemoryCategory.PREFERENCE,
                "user prefers concise answers",
                "conversation-a",
                0.9,
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:00:00Z"));
    }

    private PostgresLongTermMemoryRepository repository(
            JdbcOperations jdbc,
            EmbeddingClient embeddingClient) {
        return repository(jdbc, embeddingClient, 768);
    }

    private PostgresLongTermMemoryRepository repository(
            JdbcOperations jdbc,
            EmbeddingClient embeddingClient,
            int embeddingDimensions) {
        return new PostgresLongTermMemoryRepository(
                jdbc,
                embeddingClient,
                new LongTermMemoryPolicy(),
                clock,
                5,
                embeddingDimensions,
                null);
    }
}
