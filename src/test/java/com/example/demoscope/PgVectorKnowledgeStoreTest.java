package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

class PgVectorKnowledgeStoreTest {

    @Test
    void initializesSchemaWithConfiguredEmbeddingDimension() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(
                jdbc,
                input -> new float[] {0.1f},
                3,
                768,
                null);

        store.initializeSchema();

        verify(jdbc).execute("create extension if not exists vector");
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("embedding vector(768)"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void retrievesNearestChunksWithCosineDistanceThreshold() {
        EmbeddingClient embeddingClient = input -> new float[] {0.1f, 0.2f, 0.3f};
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of(new KnowledgeChunk("guide.md", "memory design")));
        PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(
                jdbc,
                embeddingClient,
                3,
                768,
                0.45);

        List<KnowledgeChunk> result = store.retrieve("how does memory work?");

        assertEquals(List.of(new KnowledgeChunk("guide.md", "memory design")), result);
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("embedding <=> ?::vector")
                                && sql.contains("<= ?")
                                && sql.contains("limit ?")),
                any(RowMapper.class),
                eq("[0.1,0.2,0.3]"),
                eq("[0.1,0.2,0.3]"),
                eq(0.45),
                eq(3));
    }

    @SuppressWarnings("unchecked")
    @Test
    void omitsDistanceFilterWhenItIsNotConfigured() throws Exception {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<KnowledgeChunk> rowMapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("source")).thenReturn("faq.txt");
                    when(resultSet.getString("content")).thenReturn("answer");
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });
        PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(
                jdbc,
                input -> new float[] {1.0f, 0.0f},
                2,
                768,
                null);

        List<KnowledgeChunk> result = store.retrieve("question");

        assertEquals(new KnowledgeChunk("faq.txt", "answer"), result.get(0));
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.argThat(sql -> !sql.contains("<= ?")),
                any(RowMapper.class),
                eq("[1.0,0.0]"),
                eq(2));
    }
}
