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
    void recallsCandidatesThenFiltersAndLimitsFinalKnowledge() throws Exception {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(
                            mapKnowledge(mapper, "a.md", "best", 0.91),
                            mapKnowledge(mapper, "b.md", "second", 0.80),
                            mapKnowledge(mapper, "c.md", "noise", 0.50));
                });
        PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(
                jdbc,
                new RetrievalSettings(30, 1, 0.70));

        List<KnowledgeChunk> result = store.retrieve(
                new SemanticQuery("question", new float[] {0.1f, 0.2f}));

        assertEquals(List.of(new KnowledgeChunk("a.md", "best")), result);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertTrue(sql.getValue().contains("1 - (embedding <=> ?::vector) as similarity"));
        assertTrue(sql.getValue().contains("order by embedding <=> ?::vector"));
        assertTrue(sql.getValue().contains("limit ?"));
        assertEquals("[0.1,0.2]", arguments.getValue()[0]);
        assertEquals("[0.1,0.2]", arguments.getValue()[1]);
        assertEquals(30, arguments.getValue()[2]);
    }

    private Object mapKnowledge(
            RowMapper<?> mapper,
            String source,
            String content,
            double similarity) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("source")).thenReturn(source);
        when(resultSet.getString("content")).thenReturn(content);
        when(resultSet.getDouble("similarity")).thenReturn(similarity);
        return mapper.mapRow(resultSet, 0);
    }
}
