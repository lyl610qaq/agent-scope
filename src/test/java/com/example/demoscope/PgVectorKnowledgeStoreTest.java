package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

class PgVectorKnowledgeStoreTest {

    @SuppressWarnings("unchecked")
    @Test
    void retrievesNearestChunksWithCosineDistanceThreshold() {
        EmbeddingClient embeddingClient = input -> new float[] {0.1f, 0.2f, 0.3f};
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new KnowledgeChunk("guide.md", "memory design")));
        PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(
                jdbc,
                embeddingClient,
                3,
                0.45);

        List<KnowledgeChunk> result = store.retrieve("how does memory work?");

        assertEquals(List.of(new KnowledgeChunk("guide.md", "memory design")), result);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertTrue(sql.getValue().contains("embedding <=> ?::vector"));
        assertTrue(sql.getValue().contains("<= ?"));
        assertTrue(sql.getValue().contains("limit ?"));
        assertEquals("[0.1,0.2,0.3]", arguments.getValue()[0]);
        assertEquals("[0.1,0.2,0.3]", arguments.getValue()[1]);
        assertEquals(0.45, arguments.getValue()[2]);
        assertEquals(3, arguments.getValue()[3]);
    }

    @SuppressWarnings("unchecked")
    @Test
    void omitsDistanceFilterWhenItIsNotConfigured() throws Exception {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
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
                null);

        List<KnowledgeChunk> result = store.retrieve("question");

        assertEquals(new KnowledgeChunk("faq.txt", "answer"), result.get(0));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertTrue(!sql.getValue().contains("<= ?"));
        assertEquals(2, arguments.getValue().length);
        assertEquals("[1.0,0.0]", arguments.getValue()[0]);
        assertEquals(2, arguments.getValue()[1]);
    }
}
