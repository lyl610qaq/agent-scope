package com.example.demoscope.memory.infrastructure;

import com.example.demoscope.knowledge.domain.EmbeddingClient;
import com.example.demoscope.knowledge.domain.SemanticQuery;
import com.example.demoscope.knowledge.infrastructure.PgVectorKnowledgeStore;
import com.example.demoscope.memory.application.LongTermMemoryPolicy;
import com.example.demoscope.memory.domain.LongTermMemory;
import com.example.demoscope.memory.domain.LongTermMemoryCandidate;
import com.example.demoscope.memory.domain.LongTermMemoryCategory;
import com.example.demoscope.memory.domain.LongTermMemoryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcOperations;

public class PostgresLongTermMemoryRepository implements LongTermMemoryRepository {

    private final JdbcOperations jdbc;
    private final EmbeddingClient embeddingClient;
    private final LongTermMemoryPolicy policy;
    private final Clock clock;
    private final int topK;
    private final int embeddingDimensions;
    private final Double maxDistance;

    public PostgresLongTermMemoryRepository(
            JdbcOperations jdbc,
            EmbeddingClient embeddingClient,
            LongTermMemoryPolicy policy,
            Clock clock,
            int topK,
            int embeddingDimensions,
            Double maxDistance) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.policy = policy;
        this.clock = clock;
        this.topK = topK;
        this.embeddingDimensions = embeddingDimensions;
        this.maxDistance = maxDistance;
    }

    public void initializeSchema() {
        jdbc.execute("create extension if not exists vector");
        jdbc.execute(createTableSql());
        jdbc.execute("""
                create index if not exists long_term_memories_active_user_idx
                on long_term_memories (user_id, is_active)
                """);
        jdbc.execute("""
                create unique index if not exists long_term_memories_active_unique_idx
                on long_term_memories (user_id, category, normalized_text)
                where is_active
                """);
    }

    private String createTableSql() {
        return """
                create table if not exists long_term_memories (
                    id uuid primary key,
                    user_id text not null,
                    memory_group_id uuid not null,
                    version integer not null,
                    is_active boolean not null,
                    category text not null,
                    text text not null,
                    normalized_text text not null,
                    source_conversation_id text not null,
                    confidence double precision not null,
                    embedding vector(%d) not null,
                    created_at timestamptz not null,
                    updated_at timestamptz not null,
                    unique (memory_group_id, version)
                )
                """.formatted(embeddingDimensions);
    }

    public record ActiveMemoryRow(UUID memoryGroupId, int version) {
    }

    @Override
    public List<LongTermMemory> findRelevant(String userId, SemanticQuery query) {
        String vector = PgVectorKnowledgeStore.serializeVector(query.embedding());
        if (maxDistance == null) {
            return jdbc.query(
                    """
                            select id, user_id, category, text, source_conversation_id,
                                   confidence, created_at, updated_at,
                                   embedding <=> ?::vector as distance
                            from long_term_memories
                            where user_id = ? and is_active = true
                            order by distance
                            limit ?
                            """,
                    this::mapMemory,
                    vector,
                    userId,
                    topK);
        }
        return jdbc.query(
                """
                        select id, user_id, category, text, source_conversation_id,
                               confidence, created_at, updated_at,
                               embedding <=> ?::vector as distance
                        from long_term_memories
                        where user_id = ? and is_active = true and embedding <=> ?::vector <= ?
                        order by distance
                        limit ?
                        """,
                this::mapMemory,
                vector,
                userId,
                vector,
                maxDistance,
                topK);
    }

    @Override
    public void save(String userId, String conversationId, LongTermMemoryCandidate candidate) {
        if (!policy.isAllowed(candidate)) {
            return;
        }

        String normalized = normalize(candidate.text());
        List<ActiveMemoryRow> existing = findActive(userId, candidate.category(), normalized);
        UUID groupId = existing.isEmpty() ? UUID.randomUUID() : existing.get(0).memoryGroupId();
        int version = existing.isEmpty() ? 1 : existing.get(0).version() + 1;
        if (!existing.isEmpty()) {
            jdbc.update(
                    "update long_term_memories set is_active = false where memory_group_id = ? and is_active = true",
                    groupId);
        }
        insert(userId, conversationId, candidate, normalized, groupId, version);
    }

    private List<ActiveMemoryRow> findActive(
            String userId,
            LongTermMemoryCategory category,
            String normalized) {
        return jdbc.query(
                """
                        select memory_group_id, version
                        from long_term_memories
                        where user_id = ? and category = ? and normalized_text = ? and is_active = true
                        """,
                (resultSet, rowNumber) -> new ActiveMemoryRow(
                        resultSet.getObject("memory_group_id", UUID.class),
                        resultSet.getInt("version")),
                userId,
                category.name(),
                normalized.toLowerCase(Locale.ROOT));
    }

    private void insert(
            String userId,
            String conversationId,
            LongTermMemoryCandidate candidate,
            String normalized,
            UUID groupId,
            int version) {
        Instant now = clock.instant();
        jdbc.update(
                """
                        insert into long_term_memories
                            (id, user_id, memory_group_id, version, is_active, category, text,
                             normalized_text, source_conversation_id, confidence, embedding,
                             created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?)
                        """,
                UUID.randomUUID(),
                userId,
                groupId,
                version,
                true,
                candidate.category().name(),
                normalized,
                normalized.toLowerCase(Locale.ROOT),
                conversationId,
                candidate.confidence(),
                PgVectorKnowledgeStore.serializeVector(embeddingClient.embed(normalized)),
                now,
                now);
    }

    private LongTermMemory mapMemory(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LongTermMemory(
                resultSet.getString("id"),
                resultSet.getString("user_id"),
                LongTermMemoryCategory.valueOf(resultSet.getString("category")),
                resultSet.getString("text"),
                resultSet.getString("source_conversation_id"),
                resultSet.getDouble("confidence"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
