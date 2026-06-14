# Layered TopK Retrieval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single TopK and JSON-wide memory lookup with user-scoped, layered pgvector retrieval for knowledge and long-term memory, using the RuoYi-Vue-Plus token user ID.

**Architecture:** Introduce validated retrieval settings and a reusable `SemanticQuery`, then let knowledge and long-term repositories apply `vectorTopK -> minScore -> finalTopN`. `MemoryOrchestrator` creates one query embedding per chat request, while `CurrentUserProvider` isolates the RuoYi-Vue-Plus `LoginHelper.getUserId()` dependency from application code.

**Tech Stack:** Java 17, Spring Boot 4.0.6, Spring MVC, Spring JDBC, PostgreSQL, pgvector, AgentScope 1.0.12, RuoYi-Vue-Plus token context, JUnit 5, Mockito.

---

## File Structure

New focused units:

- `RetrievalSettings.java`: validates candidate, final, and threshold settings.
- `SemanticQuery.java`: carries query text and the one reusable embedding.
- `CurrentUserProvider.java`: application authentication boundary.
- `RuoYiPlusCurrentUserProvider.java`: runtime adapter for `LoginHelper.getUserId()`.
- `UnauthenticatedUserException.java`: framework-neutral authentication failure.
- `PgVectorLongTermMemoryRepository.java`: user-scoped vector persistence and retrieval.
- `AgentScopeChatTextModel.java`: AgentScope/OpenAI-compatible model adapter.
- `AgentMemoryConfig.java`: Spring wiring for retrieval, memory, embeddings, and chat.

Existing units to change:

- `KnowledgeRetriever.java` and `PgVectorKnowledgeStore.java`: accept a precomputed semantic query and apply layered retrieval.
- `LongTermMemory.java` and `LongTermMemoryRepository.java`: include user ownership and semantic query methods.
- `JsonLongTermMemoryRepository.java`: remain as a user-aware legacy implementation, but no longer be wired.
- `MemoryOrchestrator.java`: reuse one query embedding and pass user ID through memory operations.
- `AgentChatService.java`, `AgentChatController.java`, and `OpenAiAgentChatService.java`: carry authenticated user ID and activate layered context.
- `AgentRuntimeConfigController.java` and `application.properties`: expose and configure the new retrieval settings.
- `schema.sql`: add the pgvector long-term memory table.

## Task 1: Retrieval Value Objects

**Files:**
- Create: `src/main/java/com/example/demoscope/RetrievalSettings.java`
- Create: `src/main/java/com/example/demoscope/SemanticQuery.java`
- Create: `src/test/java/com/example/demoscope/RetrievalSettingsTest.java`
- Create: `src/test/java/com/example/demoscope/SemanticQueryTest.java`

- [ ] **Step 1: Write failing settings validation tests**

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RetrievalSettingsTest {

    @Test
    void acceptsLayeredRetrievalSettings() {
        assertDoesNotThrow(() -> new RetrievalSettings(30, 6, 0.70));
    }

    @Test
    void rejectsInvalidLimitsAndThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(0, 1, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 0, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 6, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 3, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 3, 1.01));
    }
}
```

- [ ] **Step 2: Write the failing immutable embedding test**

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SemanticQueryTest {

    @Test
    void protectsItsEmbeddingFromExternalMutation() {
        float[] embedding = {0.1f, 0.2f};
        SemanticQuery query = new SemanticQuery("memory", embedding);
        embedding[0] = 9.0f;

        assertEquals("memory", query.text());
        assertArrayEquals(new float[] {0.1f, 0.2f}, query.embedding());

        float[] returned = query.embedding();
        returned[1] = 8.0f;
        assertArrayEquals(new float[] {0.1f, 0.2f}, query.embedding());
    }
}
```

- [ ] **Step 3: Run the tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=RetrievalSettingsTest,SemanticQueryTest test
```

Expected: compilation fails because `RetrievalSettings` and `SemanticQuery` do not exist.

- [ ] **Step 4: Implement the value objects**

```java
package com.example.demoscope;

public record RetrievalSettings(int vectorTopK, int finalTopN, double minScore) {

    public RetrievalSettings {
        if (vectorTopK < 1) {
            throw new IllegalArgumentException("vectorTopK must be at least 1");
        }
        if (finalTopN < 1) {
            throw new IllegalArgumentException("finalTopN must be at least 1");
        }
        if (vectorTopK < finalTopN) {
            throw new IllegalArgumentException("vectorTopK must be greater than or equal to finalTopN");
        }
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be between 0 and 1");
        }
    }
}
```

```java
package com.example.demoscope;

import java.util.Objects;

public record SemanticQuery(String text, float[] embedding) {

    public SemanticQuery {
        text = Objects.requireNonNull(text, "text");
        embedding = Objects.requireNonNull(embedding, "embedding").clone();
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}
```

- [ ] **Step 5: Run the focused tests**

Run:

```powershell
.\mvnw.cmd -Dtest=RetrievalSettingsTest,SemanticQueryTest test
```

Expected: both test classes pass.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/com/example/demoscope/RetrievalSettings.java src/main/java/com/example/demoscope/SemanticQuery.java src/test/java/com/example/demoscope/RetrievalSettingsTest.java src/test/java/com/example/demoscope/SemanticQueryTest.java
git commit -m "feat: add layered retrieval settings"
```

## Task 2: Layered Knowledge Retrieval

**Files:**
- Modify: `src/main/java/com/example/demoscope/KnowledgeRetriever.java`
- Modify: `src/main/java/com/example/demoscope/PgVectorKnowledgeStore.java`
- Modify: `src/test/java/com/example/demoscope/PgVectorKnowledgeStoreTest.java`

- [ ] **Step 1: Replace the existing tests with layered retrieval expectations**

The primary test must return three candidates, discard the low score, and then
enforce `finalTopN=1`:

```java
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
    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(anyString(), any(RowMapper.class), arguments.capture());
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
```

Also assert that SQL contains both:

```java
assertTrue(sql.getValue().contains("1 - (embedding <=> ?::vector) as similarity"));
assertTrue(sql.getValue().contains("order by embedding <=> ?::vector limit ?"));
```

- [ ] **Step 2: Run the test and verify the old constructor/API fails**

Run:

```powershell
.\mvnw.cmd -Dtest=PgVectorKnowledgeStoreTest test
```

Expected: compilation fails because `KnowledgeRetriever` still accepts `String`
and `PgVectorKnowledgeStore` still accepts `EmbeddingClient`, `topK`, and
`maxDistance`.

- [ ] **Step 3: Change the retrieval interface**

```java
package com.example.demoscope;

import java.util.List;

@FunctionalInterface
public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(SemanticQuery query);
}
```

- [ ] **Step 4: Implement candidate recall, threshold filtering, and final limit**

`PgVectorKnowledgeStore` must no longer own an `EmbeddingClient`:

```java
public class PgVectorKnowledgeStore implements KnowledgeRetriever {

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

    // Keep the existing serializeVector implementation.

    private record ScoredKnowledgeChunk(KnowledgeChunk chunk, double similarity) {
    }
}
```

Add `java.util.Comparator` to the imports.

- [ ] **Step 5: Run the knowledge tests**

Run:

```powershell
.\mvnw.cmd -Dtest=PgVectorKnowledgeStoreTest test
```

Expected: all knowledge retrieval tests pass.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/com/example/demoscope/KnowledgeRetriever.java src/main/java/com/example/demoscope/PgVectorKnowledgeStore.java src/test/java/com/example/demoscope/PgVectorKnowledgeStoreTest.java
git commit -m "feat: layer pgvector knowledge retrieval"
```

## Task 3: User-Scoped pgvector Long-Term Memory

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/java/com/example/demoscope/LongTermMemory.java`
- Modify: `src/main/java/com/example/demoscope/LongTermMemoryRepository.java`
- Modify: `src/main/java/com/example/demoscope/JsonLongTermMemoryRepository.java`
- Create: `src/main/java/com/example/demoscope/PgVectorLongTermMemoryRepository.java`
- Create: `src/test/java/com/example/demoscope/PgVectorLongTermMemoryRepositoryTest.java`
- Modify: `src/test/java/com/example/demoscope/JsonLongTermMemoryRepositoryTest.java`
- Modify: `src/test/java/com/example/demoscope/PromptContextBuilderTest.java`

- [ ] **Step 1: Write failing pgvector read-isolation test**

```java
@SuppressWarnings("unchecked")
@Test
void retrievesOnlyCandidatesForTheRequestedUser() throws Exception {
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(
                        mapMemory(mapper, "memory-1", 42L, "中文回答", 0.90),
                        mapMemory(mapper, "memory-2", 42L, "unrelated", 0.60));
            });
    PgVectorLongTermMemoryRepository repository =
            new PgVectorLongTermMemoryRepository(
                    jdbc,
                    input -> new float[] {0.5f},
                    new RetrievalSettings(20, 5, 0.72),
                    Clock.systemUTC());

    List<LongTermMemory> memories = repository.findRelevant(
            42L,
            new SemanticQuery("中文", new float[] {0.1f, 0.2f}));

    assertEquals(1, memories.size());
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
    assertTrue(sql.getValue().contains("where user_id = ?"));
    assertEquals(42L, arguments.getValue()[1]);
    assertEquals(20, arguments.getValue()[3]);
}
```

Add this helper to the same test class:

```java
private Object mapMemory(
        RowMapper<?> mapper,
        String id,
        long userId,
        String text,
        double similarity) throws Exception {
    ResultSet resultSet = mock(ResultSet.class);
    Instant createdAt = Instant.parse("2026-06-13T09:00:00Z");
    Instant updatedAt = Instant.parse("2026-06-13T10:00:00Z");
    when(resultSet.getString("id")).thenReturn(id);
    when(resultSet.getLong("user_id")).thenReturn(userId);
    when(resultSet.getString("category")).thenReturn("PREFERENCE");
    when(resultSet.getString("text")).thenReturn(text);
    when(resultSet.getString("source_conversation_id")).thenReturn("conversation-a");
    when(resultSet.getDouble("confidence")).thenReturn(0.9);
    when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
    when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));
    when(resultSet.getDouble("similarity")).thenReturn(similarity);
    return mapper.mapRow(resultSet, 0);
}
```

- [ ] **Step 2: Write failing upsert test**

```java
@Test
void embedsAndUpsertsMemoryWithinUserScope() {
    JdbcOperations jdbc = mock(JdbcOperations.class);
    EmbeddingClient embeddingClient = input -> {
        assertEquals("用户偏好中文回答", input);
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
            42L,
            "conversation-a",
            new LongTermMemoryCandidate(
                    LongTermMemoryCategory.PREFERENCE,
                    "  用户偏好中文回答  ",
                    0.9));

    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), arguments.capture());
    assertEquals(42L, arguments.getValue()[1]);
    assertEquals("用户偏好中文回答", arguments.getValue()[3]);
    assertEquals("用户偏好中文回答", arguments.getValue()[4]);
    assertEquals("[0.4,0.6]", arguments.getValue()[7]);
}
```

- [ ] **Step 3: Run the tests and verify missing types/signatures**

Run:

```powershell
.\mvnw.cmd -Dtest=PgVectorLongTermMemoryRepositoryTest test
```

Expected: compilation fails because the repository and user-scoped methods do
not exist.

- [ ] **Step 4: Add the user ID to the domain and repository contract**

```java
public record LongTermMemory(
        String id,
        long userId,
        LongTermMemoryCategory category,
        String text,
        String sourceConversationId,
        double confidence,
        Instant createdAt,
        Instant updatedAt) {
}
```

```java
public interface LongTermMemoryRepository {

    List<LongTermMemory> findRelevant(long userId, SemanticQuery query);

    void save(long userId, String conversationId, LongTermMemoryCandidate candidate);
}
```

Update existing test constructors to pass a user ID after `id`.

- [ ] **Step 5: Add the PostgreSQL schema**

Append:

```sql
create table if not exists agent_long_term_memories (
    id text primary key,
    user_id bigint not null,
    category varchar(64) not null,
    text text not null,
    normalized_text text not null,
    source_conversation_id text not null,
    confidence numeric(4, 3) not null,
    embedding vector(1024) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (user_id, category, normalized_text)
);
```

- [ ] **Step 6: Implement `PgVectorLongTermMemoryRepository`**

```java
package com.example.demoscope;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcOperations;

public class PgVectorLongTermMemoryRepository implements LongTermMemoryRepository {

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

    @Override
    public List<LongTermMemory> findRelevant(long userId, SemanticQuery query) {
        String vector = PgVectorKnowledgeStore.serializeVector(query.embedding());
        List<ScoredMemory> candidates = jdbc.query(
                SEARCH_SQL,
                (resultSet, rowNumber) -> new ScoredMemory(
                        new LongTermMemory(
                                resultSet.getString("id"),
                                resultSet.getLong("user_id"),
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
            long userId,
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
```

Change `PgVectorKnowledgeStore.serializeVector` from `private static` to
package-private `static`.

- [ ] **Step 7: Keep the JSON repository compiling as a user-aware legacy store**

Change the read method to:

```java
@Override
public synchronized List<LongTermMemory> findRelevant(
        long userId,
        SemanticQuery query) {
    return load().stream()
            .filter(memory -> memory.userId() == userId)
            .toList();
}
```

Change `save` to accept `userId`, pass it into each new or updated
`LongTermMemory`, and pass it into `findExisting`. Replace the duplicate
condition with:

```java
if (memory.userId() == userId
        && memory.category() == category
        && normalize(memory.text()).toLowerCase(Locale.ROOT)
                .equals(normalizedText.toLowerCase(Locale.ROOT))) {
    return i;
}
```

Update `JsonLongTermMemoryRepositoryTest` to call:

```java
repository.save(42L, "conversation-a", candidate);
repository.findRelevant(42L, new SemanticQuery("中文", new float[] {0.1f}));
```

Add this assertion after saving user `42L`:

```java
assertEquals(
        List.of(),
        repository.findRelevant(
                43L,
                new SemanticQuery("中文", new float[] {0.1f})));
```

- [ ] **Step 8: Run repository and prompt tests**

Run:

```powershell
.\mvnw.cmd -Dtest=PgVectorLongTermMemoryRepositoryTest,JsonLongTermMemoryRepositoryTest,PromptContextBuilderTest test
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```powershell
git add -- src/main/resources/schema.sql src/main/java/com/example/demoscope/LongTermMemory.java src/main/java/com/example/demoscope/LongTermMemoryRepository.java src/main/java/com/example/demoscope/JsonLongTermMemoryRepository.java src/main/java/com/example/demoscope/PgVectorLongTermMemoryRepository.java src/test/java/com/example/demoscope/PgVectorLongTermMemoryRepositoryTest.java src/test/java/com/example/demoscope/JsonLongTermMemoryRepositoryTest.java src/test/java/com/example/demoscope/PromptContextBuilderTest.java
git commit -m "feat: persist user scoped vector memories"
```

## Task 4: One Query Embedding Per Chat Preparation

**Files:**
- Modify: `src/main/java/com/example/demoscope/MemoryOrchestrator.java`
- Modify: `src/test/java/com/example/demoscope/MemoryOrchestratorTest.java`

- [ ] **Step 1: Write the failing embedding-reuse test**

```java
@Test
void createsOneEmbeddingForBothSemanticSources() {
    AtomicInteger embeddingCalls = new AtomicInteger();
    AtomicReference<SemanticQuery> knowledgeQuery = new AtomicReference<>();
    AtomicReference<SemanticQuery> memoryQuery = new AtomicReference<>();

    EmbeddingClient embeddingClient = input -> {
        embeddingCalls.incrementAndGet();
        return new float[] {0.2f, 0.8f};
    };
    KnowledgeRetriever knowledge = query -> {
        knowledgeQuery.set(query);
        return List.of();
    };
    LongTermMemoryRepository longTerm = new CapturingLongTermRepository(memoryQuery);
    MemoryOrchestrator orchestrator = orchestrator(
            embeddingClient,
            knowledge,
            longTerm);

    orchestrator.prepare(42L, "conversation-a", "question");

    assertEquals(1, embeddingCalls.get());
    assertArrayEquals(knowledgeQuery.get().embedding(), memoryQuery.get().embedding());
}
```

Add a second test where `EmbeddingClient.embed` throws. It must still return
short-term memory while both semantic result lists are empty.

- [ ] **Step 2: Run the orchestrator tests and verify signature failures**

Run:

```powershell
.\mvnw.cmd -Dtest=MemoryOrchestratorTest test
```

Expected: compilation fails because `prepare` and repository methods do not take
`userId`/`SemanticQuery`.

- [ ] **Step 3: Implement shared query embedding**

Change the constructor to:

```java
public MemoryOrchestrator(
        ShortTermMemoryStore shortTermMemoryStore,
        LongTermMemoryRepository longTermMemoryRepository,
        KnowledgeRetriever knowledgeRetriever,
        LongTermMemoryExtractor longTermMemoryExtractor,
        LongTermMemoryPolicy longTermMemoryPolicy,
        EmbeddingClient embeddingClient,
        Clock clock) {
    this.shortTermMemoryStore = shortTermMemoryStore;
    this.longTermMemoryRepository = longTermMemoryRepository;
    this.knowledgeRetriever = knowledgeRetriever;
    this.longTermMemoryExtractor = longTermMemoryExtractor;
    this.longTermMemoryPolicy = longTermMemoryPolicy;
    this.embeddingClient = embeddingClient;
    this.clock = clock;
}
```

Change public methods to:

```java
public MemoryContext prepare(long userId, String conversationId, String query)

public void recordTurn(
        long userId,
        String conversationId,
        String userMessage,
        String assistantMessage)
```

The semantic part of `prepare` must be:

```java
SemanticQuery semanticQuery;
try {
    semanticQuery = new SemanticQuery(query, embeddingClient.embed(query));
} catch (RuntimeException ex) {
    log.warn("Failed to embed retrieval query", ex);
    return new MemoryContext(shortTerm, List.of(), List.of());
}

List<LongTermMemory> longTerm = safelyReadLongTerm(userId, semanticQuery);
List<KnowledgeChunk> knowledge = safelyRetrieveKnowledge(semanticQuery);
return new MemoryContext(shortTerm, longTerm, knowledge);
```

`recordTurn` must call:

```java
longTermMemoryRepository.save(userId, conversationId, candidate);
```

Keep independent exception handling for knowledge read, memory read, extraction,
and persistence.

- [ ] **Step 4: Run the orchestrator tests**

Run:

```powershell
.\mvnw.cmd -Dtest=MemoryOrchestratorTest test
```

Expected: all tests pass, including exactly one query embedding call.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/example/demoscope/MemoryOrchestrator.java src/test/java/com/example/demoscope/MemoryOrchestratorTest.java
git commit -m "feat: share query embedding across retrieval"
```

## Task 5: RuoYi-Vue-Plus Current User Boundary

**Files:**
- Create: `src/main/java/com/example/demoscope/CurrentUserProvider.java`
- Create: `src/main/java/com/example/demoscope/UnauthenticatedUserException.java`
- Create: `src/main/java/com/example/demoscope/RuoYiPlusCurrentUserProvider.java`
- Modify: `src/main/java/com/example/demoscope/AgentChatService.java`
- Modify: `src/main/java/com/example/demoscope/AgentChatController.java`
- Create: `src/test/java/com/example/demoscope/RuoYiPlusCurrentUserProviderTest.java`
- Modify: `src/test/java/com/example/demoscope/AgentChatControllerTest.java`

- [ ] **Step 1: Write failing provider tests**

```java
class RuoYiPlusCurrentUserProviderTest {

    @Test
    void returnsRuoYiUserId() {
        CurrentUserProvider provider = new RuoYiPlusCurrentUserProvider(() -> 42L);
        assertEquals(42L, provider.requireUserId());
    }

    @Test
    void rejectsMissingLoginContext() {
        CurrentUserProvider provider = new RuoYiPlusCurrentUserProvider(() -> null);
        assertThrows(UnauthenticatedUserException.class, provider::requireUserId);
    }
}
```

- [ ] **Step 2: Add controller tests proving the body does not control user ID**

Keep the existing `@SpringBootTest` tests and add:

```java
@MockitoBean
CurrentUserProvider currentUserProvider;

@MockitoBean
JdbcOperations jdbcOperations;

@BeforeEach
void authenticateUser() {
    when(currentUserProvider.requireUserId()).thenReturn(42L);
}
```

Import `org.springframework.test.context.bean.override.mockito.MockitoBean`.
Replace the missing-API-key test with one that verifies the service receives the
authenticated ID by adding a `@MockitoBean AgentChatService` and:

```java
when(agentChatService.chat(42L, "conversation-a", "hello"))
        .thenReturn("answer");

mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "conversationId": "conversation-a",
                          "message": "hello"
                        }
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("answer"));

verify(agentChatService).chat(42L, "conversation-a", "hello");
```

Add an unauthenticated request test:

```java
when(currentUserProvider.requireUserId())
        .thenThrow(new UnauthenticatedUserException("Authentication is required"));

mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "conversationId": "conversation-a",
                          "message": "hello"
                        }
                        """))
        .andExpect(status().isUnauthorized());
```

- [ ] **Step 3: Run tests and verify missing authentication classes**

Run:

```powershell
.\mvnw.cmd -Dtest=RuoYiPlusCurrentUserProviderTest,AgentChatControllerTest test
```

Expected: compilation fails because the provider types do not exist.

- [ ] **Step 4: Implement the authentication boundary**

```java
@FunctionalInterface
public interface CurrentUserProvider {

    long requireUserId();
}
```

```java
public class UnauthenticatedUserException extends RuntimeException {

    public UnauthenticatedUserException(String message) {
        super(message);
    }

    public UnauthenticatedUserException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`RuoYiPlusCurrentUserProvider` uses an injectable lookup for tests and reflection
for the standalone build:

```java
public class RuoYiPlusCurrentUserProvider implements CurrentUserProvider {

    private static final String LOGIN_HELPER =
            "org.dromara.common.satoken.utils.LoginHelper";

    private final Supplier<Long> userIdLookup;

    public RuoYiPlusCurrentUserProvider() {
        this(RuoYiPlusCurrentUserProvider::lookupFromRuoYi);
    }

    RuoYiPlusCurrentUserProvider(Supplier<Long> userIdLookup) {
        this.userIdLookup = userIdLookup;
    }

    @Override
    public long requireUserId() {
        Long userId;
        try {
            userId = userIdLookup.get();
        } catch (RuntimeException ex) {
            throw new UnauthenticatedUserException("Unable to resolve authenticated user", ex);
        }
        if (userId == null) {
            throw new UnauthenticatedUserException("Authentication is required");
        }
        return userId;
    }

    private static Long lookupFromRuoYi() {
        try {
            Class<?> helper = Class.forName(LOGIN_HELPER);
            return (Long) helper.getMethod("getUserId").invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("RuoYi-Vue-Plus LoginHelper is unavailable", ex);
        }
    }
}
```

- [ ] **Step 5: Pass authenticated user ID into the service**

Change:

```java
String chat(long userId, String conversationId, String message);
```

Inject `CurrentUserProvider` into `AgentChatController` and call:

```java
long userId = currentUserProvider.requireUserId();
return new ChatResponse(agentChatService.chat(
        userId,
        request.conversationId(),
        request.message()));
```

Map `UnauthenticatedUserException` to `401`, while preserving the current
`IllegalStateException -> 500` behavior.

- [ ] **Step 6: Run authentication and controller tests**

Run:

```powershell
.\mvnw.cmd -Dtest=RuoYiPlusCurrentUserProviderTest,AgentChatControllerTest test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```powershell
git add -- src/main/java/com/example/demoscope/CurrentUserProvider.java src/main/java/com/example/demoscope/UnauthenticatedUserException.java src/main/java/com/example/demoscope/RuoYiPlusCurrentUserProvider.java src/main/java/com/example/demoscope/AgentChatService.java src/main/java/com/example/demoscope/AgentChatController.java src/test/java/com/example/demoscope/RuoYiPlusCurrentUserProviderTest.java src/test/java/com/example/demoscope/AgentChatControllerTest.java
git commit -m "feat: resolve chat user from ruoyi token"
```

## Task 6: Activate Memory Context in the Chat Service

**Files:**
- Create: `src/main/java/com/example/demoscope/AgentScopeChatTextModel.java`
- Modify: `src/main/java/com/example/demoscope/OpenAiAgentChatService.java`
- Create: `src/test/java/com/example/demoscope/OpenAiAgentChatServiceMemoryTest.java`
- Create: `src/test/java/com/example/demoscope/AgentScopeChatTextModelTest.java`

- [ ] **Step 1: Write the failing orchestration test**

Use mocked `MemoryOrchestrator` and `ChatTextModel`:

```java
@Test
void preparesPromptAndRecordsCompletedTurn() {
    MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
    MemoryContext context = new MemoryContext(
            List.of(),
            List.of(),
            List.of(new KnowledgeChunk("guide.md", "Java 17")));
    when(orchestrator.prepare(42L, "conversation-a", "question"))
            .thenReturn(context);
    ChatTextModel model = mock(ChatTextModel.class);
    when(model.generate(anyString(), contains("Java 17"))).thenReturn("answer");
    OpenAiAgentChatService service = new OpenAiAgentChatService(
            orchestrator,
            new PromptContextBuilder(),
            model);

    String answer = service.chat(42L, "conversation-a", "question");

    assertEquals("answer", answer);
    verify(orchestrator).recordTurn(
            42L,
            "conversation-a",
            "question",
            "answer");
}
```

Add a test where `model.generate` throws and verify `recordTurn` is not called.

- [ ] **Step 2: Run the service test and verify the old constructor fails**

Run:

```powershell
.\mvnw.cmd -Dtest=OpenAiAgentChatServiceMemoryTest test
```

Expected: compilation fails because the service still depends on
`LocalKnowledgeStore` and `RagPromptBuilder`.

- [ ] **Step 3: Implement the active chat orchestration**

Replace `OpenAiAgentChatService` dependencies with:

```java
private static final String SYSTEM_PROMPT = "You are a helpful AI assistant.";

private final MemoryOrchestrator memoryOrchestrator;
private final PromptContextBuilder promptContextBuilder;
private final ChatTextModel chatTextModel;

public OpenAiAgentChatService(
        MemoryOrchestrator memoryOrchestrator,
        PromptContextBuilder promptContextBuilder,
        ChatTextModel chatTextModel) {
    this.memoryOrchestrator = memoryOrchestrator;
    this.promptContextBuilder = promptContextBuilder;
    this.chatTextModel = chatTextModel;
}
```

Implement:

```java
@Override
public String chat(long userId, String conversationId, String message) {
    MemoryContext context = memoryOrchestrator.prepare(
            userId,
            conversationId,
            message);
    String modelMessage = promptContextBuilder.build(
            SYSTEM_PROMPT,
            context,
            message);
    String answer = chatTextModel.generate(SYSTEM_PROMPT, modelMessage);
    memoryOrchestrator.recordTurn(
            userId,
            conversationId,
            message,
            answer);
    return answer;
}
```

- [ ] **Step 4: Add the AgentScope model adapter**

```java
package com.example.demoscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.util.StringUtils;

public class AgentScopeChatTextModel implements ChatTextModel {

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final OpenAiRequestLogger requestLogger;

    public AgentScopeChatTextModel(
            String apiKey,
            String modelName,
            String baseUrl,
            OpenAiRequestLogger requestLogger) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.requestLogger = requestLogger;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured.");
        }

        requestLogger.logChatRequest(apiKey, baseUrl, modelName, userPrompt);
        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (StringUtils.hasText(baseUrl)) {
            modelBuilder.baseUrl(baseUrl);
        }

        ReActAgent agent = ReActAgent.builder()
                .name("assistant")
                .sysPrompt(systemPrompt)
                .model(modelBuilder.build())
                .build();
        Msg response = agent.call(Msg.builder()
                        .name("user")
                        .role(MsgRole.USER)
                        .textContent(userPrompt)
                        .build())
                .block();
        return response == null ? "" : response.getTextContent();
    }
}
```

The focused test should assert that a blank API key fails before any remote
request.

- [ ] **Step 5: Run the service and adapter tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OpenAiAgentChatServiceMemoryTest,AgentScopeChatTextModelTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/com/example/demoscope/AgentScopeChatTextModel.java src/main/java/com/example/demoscope/OpenAiAgentChatService.java src/test/java/com/example/demoscope/OpenAiAgentChatServiceMemoryTest.java src/test/java/com/example/demoscope/AgentScopeChatTextModelTest.java
git commit -m "feat: activate layered memory chat context"
```

## Task 7: Spring Wiring and Retrieval Properties

**Files:**
- Delete: `src/main/java/com/example/demoscope/RagConfig.java`
- Create: `src/main/java/com/example/demoscope/AgentMemoryConfig.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`

- [ ] **Step 1: Write a failing context test for the new defaults**

Use `@MockitoBean` for `JdbcOperations` so the test context does not require a
real database:

```java
@SpringBootTest(properties = {
        "agentscope.openai.api-key=test-key",
        "agentscope.embedding.api-key=test-embedding-key"
})
class DemoScopeApplicationTests {

    @MockitoBean
    JdbcOperations jdbcOperations;

    @Test
    void contextLoads() {
    }
}
```

Import `org.springframework.test.context.bean.override.mockito.MockitoBean`.

- [ ] **Step 2: Run the context test and verify missing beans**

Run:

```powershell
.\mvnw.cmd -Dtest=DemoScopeApplicationTests test
```

Expected: context startup fails because the new orchestrator/repository/model
beans have not been declared.

- [ ] **Step 3: Replace legacy RAG wiring with `AgentMemoryConfig`**

```java
package com.example.demoscope;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;

@Configuration
public class AgentMemoryConfig {

    @Bean
    CurrentUserProvider currentUserProvider() {
        return new RuoYiPlusCurrentUserProvider();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ShortTermMemoryStore shortTermMemoryStore(
            @Value("${agentscope.memory.short-term.max-turns:10}") int maxTurns) {
        return new InMemoryShortTermMemoryStore(maxTurns);
    }

    @Bean
    LongTermMemoryPolicy longTermMemoryPolicy() {
        return new LongTermMemoryPolicy();
    }

    @Bean
    PromptContextBuilder promptContextBuilder() {
        return new PromptContextBuilder();
    }

    @Bean
    ChatTextModel chatTextModel(
            @Value("${agentscope.openai.api-key:}") String apiKey,
            @Value("${agentscope.openai.model-name:Pro/zai-org/GLM-4.7}") String modelName,
            @Value("${agentscope.openai.base-url:}") String baseUrl,
            OpenAiRequestLogger requestLogger) {
        return new AgentScopeChatTextModel(
                apiKey,
                modelName,
                baseUrl,
                requestLogger);
    }

    @Bean
    LongTermMemoryExtractor longTermMemoryExtractor(
            ChatTextModel chatTextModel,
            ObjectMapper objectMapper) {
        return new ModelLongTermMemoryExtractor(chatTextModel, objectMapper);
    }

    @Bean
    EmbeddingClient embeddingClient(
            @Value("${agentscope.embedding.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
            @Value("${agentscope.embedding.api-key:}") String apiKey,
            @Value("${agentscope.embedding.model:Qwen/Qwen3-Embedding-4B}") String model,
            @Value("${agentscope.embedding.dimensions:1024}") int dimensions) {
        return new SiliconFlowEmbeddingClient(baseUrl, apiKey, model, dimensions);
    }

    @Bean("knowledgeRetrievalSettings")
    RetrievalSettings knowledgeRetrievalSettings(
            @Value("${agentscope.retrieval.knowledge.vector-top-k:30}") int vectorTopK,
            @Value("${agentscope.retrieval.knowledge.final-top-n:6}") int finalTopN,
            @Value("${agentscope.retrieval.knowledge.min-score:0.70}") double minScore) {
        return new RetrievalSettings(vectorTopK, finalTopN, minScore);
    }

    @Bean("longTermMemoryRetrievalSettings")
    RetrievalSettings longTermMemoryRetrievalSettings(
            @Value("${agentscope.retrieval.long-term-memory.vector-top-k:20}") int vectorTopK,
            @Value("${agentscope.retrieval.long-term-memory.final-top-n:5}") int finalTopN,
            @Value("${agentscope.retrieval.long-term-memory.min-score:0.72}") double minScore) {
        return new RetrievalSettings(vectorTopK, finalTopN, minScore);
    }

    @Bean
    KnowledgeRetriever knowledgeRetriever(
            JdbcOperations jdbc,
            @Qualifier("knowledgeRetrievalSettings") RetrievalSettings settings) {
        return new PgVectorKnowledgeStore(jdbc, settings);
    }

    @Bean
    LongTermMemoryRepository longTermMemoryRepository(
            JdbcOperations jdbc,
            EmbeddingClient embeddingClient,
            @Qualifier("longTermMemoryRetrievalSettings") RetrievalSettings settings,
            Clock clock) {
        return new PgVectorLongTermMemoryRepository(
                jdbc,
                embeddingClient,
                settings,
                clock);
    }

    @Bean
    MemoryOrchestrator memoryOrchestrator(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            KnowledgeRetriever knowledgeRetriever,
            LongTermMemoryExtractor longTermMemoryExtractor,
            LongTermMemoryPolicy longTermMemoryPolicy,
            EmbeddingClient embeddingClient,
            Clock clock) {
        return new MemoryOrchestrator(
                shortTermMemoryStore,
                longTermMemoryRepository,
                knowledgeRetriever,
                longTermMemoryExtractor,
                longTermMemoryPolicy,
                embeddingClient,
                clock);
    }
}
```

Delete `RagConfig.java` so the legacy `LocalKnowledgeStore` bean is no longer in
the active runtime. Invalid `RetrievalSettings` values now fail during startup.

- [ ] **Step 4: Replace old properties**

Keep OpenAI settings and add:

```properties
agentscope.embedding.api-key=${SILICONFLOW_API_KEY:}
agentscope.embedding.base-url=${AGENTSCOPE_EMBEDDING_BASE_URL:https://api.siliconflow.cn/v1}
agentscope.embedding.model=${AGENTSCOPE_EMBEDDING_MODEL:Qwen/Qwen3-Embedding-4B}
agentscope.embedding.dimensions=${AGENTSCOPE_EMBEDDING_DIMENSIONS:1024}
agentscope.memory.short-term.max-turns=${AGENTSCOPE_MEMORY_SHORT_TERM_MAX_TURNS:10}
agentscope.retrieval.knowledge.vector-top-k=${AGENTSCOPE_KNOWLEDGE_VECTOR_TOP_K:30}
agentscope.retrieval.knowledge.final-top-n=${AGENTSCOPE_KNOWLEDGE_FINAL_TOP_N:6}
agentscope.retrieval.knowledge.min-score=${AGENTSCOPE_KNOWLEDGE_MIN_SCORE:0.70}
agentscope.retrieval.long-term-memory.vector-top-k=${AGENTSCOPE_LONG_TERM_VECTOR_TOP_K:20}
agentscope.retrieval.long-term-memory.final-top-n=${AGENTSCOPE_LONG_TERM_FINAL_TOP_N:5}
agentscope.retrieval.long-term-memory.min-score=${AGENTSCOPE_LONG_TERM_MIN_SCORE:0.72}
```

Remove:

```properties
agentscope.rag.top-k
agentscope.rag.max-chunk-chars
agentscope.rag.min-score
```

Do not add a fallback to the old property names.

- [ ] **Step 5: Run context and focused component tests**

Run:

```powershell
.\mvnw.cmd -Dtest=DemoScopeApplicationTests,MemoryOrchestratorTest,PgVectorKnowledgeStoreTest,PgVectorLongTermMemoryRepositoryTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```powershell
git add -- src/main/java/com/example/demoscope/RagConfig.java src/main/java/com/example/demoscope/AgentMemoryConfig.java src/main/resources/application.properties src/test/java/com/example/demoscope/DemoScopeApplicationTests.java
git commit -m "feat: wire layered retrieval configuration"
```

## Task 8: Runtime Configuration Endpoint

**Files:**
- Modify: `src/main/java/com/example/demoscope/AgentRuntimeConfigController.java`
- Modify: `src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java`

- [ ] **Step 1: Change the endpoint expectations**

Add a context-safe JDBC mock:

```java
@MockitoBean
JdbcOperations jdbcOperations;
```

Configure:

```java
"agentscope.retrieval.knowledge.vector-top-k=30",
"agentscope.retrieval.knowledge.final-top-n=6",
"agentscope.retrieval.knowledge.min-score=0.70",
"agentscope.retrieval.long-term-memory.vector-top-k=20",
"agentscope.retrieval.long-term-memory.final-top-n=5",
"agentscope.retrieval.long-term-memory.min-score=0.72"
```

Assert JSON fields:

```java
.andExpect(jsonPath("$.knowledge.vectorTopK").value(30))
.andExpect(jsonPath("$.knowledge.finalTopN").value(6))
.andExpect(jsonPath("$.knowledge.minScore").value(0.70))
.andExpect(jsonPath("$.longTermMemory.vectorTopK").value(20))
.andExpect(jsonPath("$.longTermMemory.finalTopN").value(5))
.andExpect(jsonPath("$.longTermMemory.minScore").value(0.72))
.andExpect(jsonPath("$.apiKeyConfigured").value(true));
```

Do not expect `ragTopK` or any API key value.

- [ ] **Step 2: Run the endpoint test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=AgentRuntimeConfigControllerTest test
```

Expected: JSON assertions fail because the controller still exposes `ragTopK`.

- [ ] **Step 3: Expose structured retrieval settings**

Inject the two named `RetrievalSettings` beans and return:

```java
public record AgentRuntimeConfigResponse(
        String modelName,
        String baseUrl,
        boolean apiKeyConfigured,
        RetrievalConfigResponse knowledge,
        RetrievalConfigResponse longTermMemory) {
}

public record RetrievalConfigResponse(
        int vectorTopK,
        int finalTopN,
        double minScore) {

    static RetrievalConfigResponse from(RetrievalSettings settings) {
        return new RetrievalConfigResponse(
                settings.vectorTopK(),
                settings.finalTopN(),
                settings.minScore());
    }
}
```

- [ ] **Step 4: Run the endpoint test**

Run:

```powershell
.\mvnw.cmd -Dtest=AgentRuntimeConfigControllerTest test
```

Expected: the endpoint test passes and no secret is present in the response.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/example/demoscope/AgentRuntimeConfigController.java src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java
git commit -m "feat: expose layered retrieval settings"
```

## Task 9: Regression Verification and Legacy Isolation

**Files:**
- Verify: `src/main/java/com/example/demoscope/OpenAiAgentChatService.java`
- Verify: `src/main/java/com/example/demoscope/AgentMemoryConfig.java`
- Verify: `src/main/resources/application.properties`
- Verify: `src/test/java/com/example/demoscope/AgentChatControllerTest.java`
- Verify: `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`

- [ ] **Step 1: Confirm the active runtime has no legacy dependency**

Run:

```powershell
rg -n "LocalKnowledgeStore|RagPromptBuilder|agentscope\\.rag\\.top-k" src/main src/test
```

Expected: matches may remain in the legacy classes and their isolated tests, but
there must be no match in `OpenAiAgentChatService`, `AgentMemoryConfig`,
`AgentChatController`, or `application.properties`.

- [ ] **Step 2: Run all focused retrieval and chat tests**

Run:

```powershell
.\mvnw.cmd -Dtest=RetrievalSettingsTest,SemanticQueryTest,PgVectorKnowledgeStoreTest,PgVectorLongTermMemoryRepositoryTest,MemoryOrchestratorTest,RuoYiPlusCurrentUserProviderTest,OpenAiAgentChatServiceMemoryTest,AgentChatControllerTest,AgentRuntimeConfigControllerTest test
```

Expected: all focused tests pass.

- [ ] **Step 3: Run the full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: Maven exits with `BUILD SUCCESS`.

- [ ] **Step 4: Verify the package build**

Run:

```powershell
.\mvnw.cmd -DskipTests package
```

Expected: Maven exits with `BUILD SUCCESS`.

- [ ] **Step 5: Verify patch hygiene**

Run:

```powershell
git diff --check
git status --short
```

Expected: `git diff --check` prints nothing. `git status --short` lists only the
intended implementation files.

- [ ] **Step 6: Commit final regression fixes if any**

```powershell
git add -- src/main src/test
git commit -m "test: verify layered topk retrieval"
```

Skip this commit when Step 5 shows no uncommitted regression changes.
