package com.example.demoscope;

import java.util.List;

import org.springframework.jdbc.core.JdbcOperations;

public class PgVectorKnowledgeStore implements KnowledgeRetriever {

    private static final String SELECT_COLUMNS = """
            select source, content, embedding <=> ?::vector as distance
            from knowledge_chunks
            """;

    private final JdbcOperations jdbc;
    private final EmbeddingClient embeddingClient;
    private final int topK;
    private final Double maxDistance;

    public PgVectorKnowledgeStore(
            JdbcOperations jdbc,
            EmbeddingClient embeddingClient,
            int topK,
            Double maxDistance) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.topK = topK;
        this.maxDistance = maxDistance;
    }

    @Override
    public List<KnowledgeChunk> retrieve(String query) {
        String vector = serializeVector(embeddingClient.embed(query));
        if (maxDistance == null) {
            return jdbc.query(
                    SELECT_COLUMNS + "order by distance limit ?",
                    (resultSet, rowNumber) -> new KnowledgeChunk(
                            resultSet.getString("source"),
                            resultSet.getString("content")),
                    vector,
                    topK);
        }
        return jdbc.query(
                SELECT_COLUMNS + "where embedding <=> ?::vector <= ? order by distance limit ?",
                (resultSet, rowNumber) -> new KnowledgeChunk(
                        resultSet.getString("source"),
                        resultSet.getString("content")),
                vector,
                vector,
                maxDistance,
                topK);
    }

    static String serializeVector(float[] vector) {
        StringBuilder serialized = new StringBuilder(vector.length * 8).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                serialized.append(',');
            }
            serialized.append(vector[index]);
        }
        return serialized.append(']').toString();
    }
}
