package com.example.demoscope.knowledge.infrastructure;

import com.example.demoscope.knowledge.domain.KnowledgeChunk;
import com.example.demoscope.knowledge.domain.KnowledgeRetriever;
import com.example.demoscope.knowledge.domain.RetrievalSettings;
import com.example.demoscope.knowledge.domain.SemanticQuery;
import java.util.Comparator;
import java.util.List;

import org.springframework.jdbc.core.JdbcOperations;

public class PgVectorKnowledgeStore implements KnowledgeRetriever {

    private static final int EMBEDDING_DIMENSIONS = 1024;
    private static final String SEARCH_SQL = """
            select source,
                   content,
                   1 - (embedding <=> ?::vector) as similarity
            from knowledge_chunks
            order by embedding <=> ?::vector
            limit ?
            """;

    private final JdbcOperations jdbc;
    private final RetrievalSettings settings;

    public PgVectorKnowledgeStore(JdbcOperations jdbc, RetrievalSettings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    @Override
    public List<KnowledgeChunk> retrieve(SemanticQuery query) {
        String vector = serializeVector(query.embedding());
        List<ScoredKnowledgeChunk> candidates = jdbc.query(
                SEARCH_SQL,
                (resultSet, rowNumber) -> new ScoredKnowledgeChunk(
                        new KnowledgeChunk(
                                resultSet.getString("source"),
                                resultSet.getString("content")),
                        resultSet.getDouble("similarity")),
                vector,
                vector,
                settings.vectorTopK());

        return candidates.stream()
                .filter(candidate -> candidate.similarity() >= settings.minScore())
                .sorted(Comparator.comparingDouble(ScoredKnowledgeChunk::similarity).reversed())
                .limit(settings.finalTopN())
                .map(ScoredKnowledgeChunk::chunk)
                .toList();
    }

    public void initializeSchema() {
        jdbc.execute("create extension if not exists vector");
        jdbc.execute(createTableSql());
    }

    private String createTableSql() {
        return """
                create table if not exists knowledge_chunks (
                    id bigserial primary key,
                    source text not null,
                    chunk_index integer not null,
                    content text not null,
                    checksum char(64) not null,
                    embedding vector(%d) not null,
                    updated_at timestamptz not null default now(),
                    unique (source, chunk_index)
                )
                """.formatted(EMBEDDING_DIMENSIONS);
    }

    public static String serializeVector(float[] vector) {
        StringBuilder serialized = new StringBuilder(vector.length * 8).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                serialized.append(',');
            }
            serialized.append(vector[index]);
        }
        return serialized.append(']').toString();
    }

    private record ScoredKnowledgeChunk(KnowledgeChunk chunk, double similarity) {
    }
}
