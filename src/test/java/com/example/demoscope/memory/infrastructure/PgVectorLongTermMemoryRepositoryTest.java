package com.example.demoscope.memory.infrastructure;

import com.example.demoscope.knowledge.domain.EmbeddingClient;
import com.example.demoscope.knowledge.domain.RetrievalSettings;
import com.example.demoscope.knowledge.domain.SemanticQuery;
import com.example.demoscope.memory.domain.LongTermMemory;
import com.example.demoscope.memory.domain.LongTermMemoryCandidate;
import com.example.demoscope.memory.domain.LongTermMemoryCategory;
import com.example.demoscope.memory.infrastructure.PgVectorLongTermMemoryRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

class PgVectorLongTermMemoryRepositoryTest {

    @Test
    void initializesSchemaForAgentLongTermMemories() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        PgVectorLongTermMemoryRepository repository =
                new PgVectorLongTermMemoryRepository(
                        jdbc,
                        input -> new float[] {0.5f},
                        new RetrievalSettings(20, 5, 0.72),
                        Clock.systemUTC());

        repository.initializeSchema();

        verify(jdbc).execute("create extension if not exists vector");
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains(
                "create table if not exists agent_long_term_memories"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void retrievesOnlyCandidatesForTheRequestedUser() throws Exception {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(
                            mapMemory(mapper, "memory-1", "42", "Chinese answers", 0.90),
                            mapMemory(mapper, "memory-2", "42", "unrelated", 0.60));
                });
        PgVectorLongTermMemoryRepository repository =
                new PgVectorLongTermMemoryRepository(
                        jdbc,
                        input -> new float[] {0.5f},
                        new RetrievalSettings(20, 5, 0.72),
                        Clock.systemUTC());

        List<LongTermMemory> memories = repository.findRelevant(
                "42",
                new SemanticQuery("Chinese", new float[] {0.1f, 0.2f}));

        assertEquals(1, memories.size());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertTrue(sql.getValue().contains("where user_id = ?"));
        assertEquals("42", arguments.getValue()[1]);
        assertEquals(20, arguments.getValue()[3]);
    }

    @Test
    void embedsAndUpsertsMemoryWithinUserScope() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        EmbeddingClient embeddingClient = input -> {
            assertEquals("User prefers Chinese answers", input);
            return new float[] {0.4f, 0.6f};
        };
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-13T10:00:00Z"),
                ZoneOffset.UTC);
        PgVectorLongTermMemoryRepository repository =
                new PgVectorLongTermMemoryRepository(
                        jdbc,
                        embeddingClient,
                        new RetrievalSettings(20, 5, 0.72),
                        clock);

        repository.save(
                "42",
                "conversation-a",
                new LongTermMemoryCandidate(
                        LongTermMemoryCategory.PREFERENCE,
                        "  User prefers Chinese answers  ",
                        0.9));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), arguments.capture());
        assertEquals("42", arguments.getValue()[1]);
        assertEquals("User prefers Chinese answers", arguments.getValue()[3]);
        assertEquals("user prefers chinese answers", arguments.getValue()[4]);
        assertEquals("[0.4,0.6]", arguments.getValue()[7]);
    }

    private Object mapMemory(
            RowMapper<?> mapper,
            String id,
            String userId,
            String text,
            double similarity) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Instant createdAt = Instant.parse("2026-06-13T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-13T10:00:00Z");
        when(resultSet.getString("id")).thenReturn(id);
        when(resultSet.getString("user_id")).thenReturn(userId);
        when(resultSet.getString("category")).thenReturn("PREFERENCE");
        when(resultSet.getString("text")).thenReturn(text);
        when(resultSet.getString("source_conversation_id")).thenReturn("conversation-a");
        when(resultSet.getDouble("confidence")).thenReturn(0.9);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));
        when(resultSet.getDouble("similarity")).thenReturn(similarity);
        return mapper.mapRow(resultSet, 0);
    }
}
