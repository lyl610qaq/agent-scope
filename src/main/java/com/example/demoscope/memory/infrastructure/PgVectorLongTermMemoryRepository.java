package com.example.demoscope.memory.infrastructure;

import com.example.demoscope.knowledge.domain.EmbeddingClient;
import com.example.demoscope.knowledge.domain.RetrievalSettings;
import com.example.demoscope.knowledge.domain.SemanticQuery;
import com.example.demoscope.knowledge.infrastructure.PgVectorKnowledgeStore;
import com.example.demoscope.memory.domain.LongTermMemory;
import com.example.demoscope.memory.domain.LongTermMemoryCandidate;
import com.example.demoscope.memory.domain.LongTermMemoryCategory;
import com.example.demoscope.memory.domain.LongTermMemoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcOperations;

public class PgVectorLongTermMemoryRepository implements LongTermMemoryRepository {

    private static final String CREATE_TABLE_SQL = """
            create table if not exists agent_long_term_memories (
                id text primary key,
                user_id text not null,
                category varchar(64) not null,
                text text not null,
                normalized_text text not null,
                source_conversation_id text not null,
                confidence numeric(4, 3) not null,
                embedding vector(1024) not null,
                created_at timestamptz not null,
                updated_at timestamptz not null,
                unique (user_id, category, normalized_text)
            )
            """;

    private static final String SEARCH_SQL = """
            select id,
                   user_id,
                   category,
                   text,
                   source_conversation_id,
                   confidence,
                   created_at,
                   updated_at,
                   1 - (embedding <=> ?::vector) as similarity
            from agent_long_term_memories
            where user_id = ?
            order by embedding <=> ?::vector
            limit ?
            """;

    private static final String UPSERT_SQL = """
            insert into agent_long_term_memories (
                id, user_id, category, text, normalized_text,
                source_conversation_id, confidence, embedding, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?)
            on conflict (user_id, category, normalized_text)
            do update set
                text = excluded.text,
                source_conversation_id = excluded.source_conversation_id,
                confidence = excluded.confidence,
                embedding = excluded.embedding,
                updated_at = excluded.updated_at
            """;

    private final JdbcOperations jdbc;
    private final EmbeddingClient embeddingClient;
    private final RetrievalSettings settings;
    private final Clock clock;

    public PgVectorLongTermMemoryRepository(
            JdbcOperations jdbc,
            EmbeddingClient embeddingClient,
            RetrievalSettings settings,
            Clock clock) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.settings = settings;
        this.clock = clock;
    }

    public void initializeSchema() {
        jdbc.execute("create extension if not exists vector");
        jdbc.execute(CREATE_TABLE_SQL);
    }

    @Override
    public List<LongTermMemory> findRelevant(String userId, SemanticQuery query) {
        String vector = PgVectorKnowledgeStore.serializeVector(query.embedding());
        List<ScoredMemory> candidates = jdbc.query(
                SEARCH_SQL,
                (resultSet, rowNumber) -> new ScoredMemory(
                        new LongTermMemory(
                                resultSet.getString("id"),
                                resultSet.getString("user_id"),
                                LongTermMemoryCategory.valueOf(
                                        resultSet.getString("category")),
                                resultSet.getString("text"),
                                resultSet.getString("source_conversation_id"),
                                resultSet.getDouble("confidence"),
                                resultSet.getTimestamp("created_at").toInstant(),
                                resultSet.getTimestamp("updated_at").toInstant()),
                        resultSet.getDouble("similarity")),
                vector,
                userId,
                vector,
                settings.vectorTopK());

        return candidates.stream()
                .filter(candidate -> candidate.similarity() >= settings.minScore())
                .sorted(Comparator.comparingDouble(ScoredMemory::similarity).reversed())
                .limit(settings.finalTopN())
                .map(ScoredMemory::memory)
                .toList();
    }

    @Override
    public void save(
            String userId,
            String conversationId,
            LongTermMemoryCandidate candidate) {
        String displayText = candidate.text().trim().replaceAll("\\s+", " ");
        String normalizedText = displayText.toLowerCase(Locale.ROOT);
        String vector = PgVectorKnowledgeStore.serializeVector(
                embeddingClient.embed(displayText));
        Instant now = clock.instant();
        jdbc.update(
                UPSERT_SQL,
                UUID.randomUUID().toString(),
                userId,
                candidate.category().name(),
                displayText,
                normalizedText,
                conversationId,
                candidate.confidence(),
                vector,
                now,
                now);
    }

    private record ScoredMemory(LongTermMemory memory, double similarity) {
    }
}
