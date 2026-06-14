# Layered TopK Retrieval Design

Date: 2026-06-13

## Goal

Reimplement retrieval so the application no longer treats `topK` as one number
that is both the database candidate limit and the amount of context sent to the
model.

The new design applies the same layered retrieval model to:

- PostgreSQL pgvector knowledge RAG.
- User-scoped long-term memory stored in PostgreSQL pgvector.

Each semantic retrieval path uses:

```text
vectorTopK -> minScore filter -> finalTopN -> prompt
```

The first version does not call an external reranker, but it preserves a clear
candidate-selection boundary so reranking can be inserted later.

## Current Context

The current Spring Boot application has two partially connected implementations:

- The active chat path still uses `LocalKnowledgeStore` and `RagPromptBuilder`.
- `PgVectorKnowledgeStore`, `MemoryOrchestrator`, `PromptContextBuilder`, and the
  long-term memory abstractions exist, but they are not wired into
  `OpenAiAgentChatService`.

`PgVectorKnowledgeStore` currently has one `topK` value and an optional
`maxDistance`. `JsonLongTermMemoryRepository.findRelevant` currently returns all
stored memories without semantic filtering.

The target deployment uses RuoYi-Vue-Plus authentication. The current standalone
repository does not contain the RuoYi common modules, so authentication access
must be kept behind an adapter boundary.

## Decisions

- Knowledge and long-term memory both use PostgreSQL + pgvector cosine search.
- Knowledge remains shared and is not filtered by user.
- Long-term memory is strictly filtered by the authenticated RuoYi user ID.
- `userId` is obtained from the RuoYi-Vue-Plus token context with
  `LoginHelper.getUserId()` and is never accepted from the chat request body.
- `conversationId` remains client-provided and isolates short-term turns.
- One query embedding is generated per chat request and reused by knowledge and
  long-term memory retrieval.
- Similarity is represented consistently as `1 - cosine_distance`.
- JSON long-term memory remains available as legacy code during migration but is
  no longer the active repository.
- Existing JSON data is not imported automatically because migration would
  trigger hidden embedding requests and external cost.
- State or task recovery data is outside semantic TopK retrieval and should use
  exact SQL queries.
- External reranking and dynamic query-complexity TopK are out of scope for this
  version.

## Architecture

### Authentication boundary

Add a small application-facing interface:

```java
public interface CurrentUserProvider {
    long requireUserId();
}
```

The production adapter obtains the ID from RuoYi-Vue-Plus:

```text
RuoYi token
  -> LoginHelper.getUserId()
  -> CurrentUserProvider.requireUserId()
  -> AgentChatController
  -> AgentChatService.chat(userId, conversationId, message)
```

If no user ID is available, the adapter raises an unauthenticated error and the
HTTP layer returns `401`.

The standalone repository does not currently compile against
`ruoyi-common-satoken`. `RuoYiPlusCurrentUserProvider` will therefore resolve
`org.dromara.common.satoken.utils.LoginHelper` at runtime and invoke its static
`getUserId()` method. This keeps the project buildable while still using the
RuoYi token context at runtime. If the helper class is absent or returns no user
ID, `requireUserId()` raises an unauthenticated error. When this code is moved
directly into a RuoYi-Vue-Plus module, only this adapter may be replaced with a
direct typed call.

The upstream RuoYi-Vue-Plus 5.X helper exposes `LoginHelper.getUserId()` as a
`Long`, which is the canonical user ID type for this design.

### Retrieval query

Introduce an immutable query value that carries both the original text and the
already computed embedding:

```java
public record SemanticQuery(String text, float[] embedding) {
}
```

`MemoryOrchestrator.prepare` creates this value once. Repositories do not call
the embedding API during reads.

### Retrieval configuration

Use separate validated configuration for each source:

```java
public record RetrievalSettings(
        int vectorTopK,
        int finalTopN,
        double minScore) {
}
```

Validation rules:

- `vectorTopK >= 1`
- `finalTopN >= 1`
- `vectorTopK >= finalTopN`
- `minScore >= 0.0 && minScore <= 1.0`

Configuration:

```yaml
agentscope:
  retrieval:
    knowledge:
      vector-top-k: 30
      final-top-n: 6
      min-score: 0.70
    long-term-memory:
      vector-top-k: 20
      final-top-n: 5
      min-score: 0.72
```

Environment-variable-backed properties should preserve the existing
`application.properties` style.

### Components

- `CurrentUserProvider`: supplies the authenticated RuoYi user ID.
- `SemanticQuery`: carries query text and one reusable query embedding.
- `RetrievalSettings`: validates candidate and final result limits.
- `PgVectorKnowledgeStore`: retrieves shared knowledge candidates.
- `PgVectorLongTermMemoryRepository`: saves and retrieves user-scoped memories.
- `MemoryOrchestrator`: generates one embedding, retrieves context, and handles
  independent degradation.
- `PromptContextBuilder`: formats only the already selected final results.
- `OpenAiAgentChatService`: uses `MemoryOrchestrator` and
  `PromptContextBuilder`, then records the completed turn.

## PostgreSQL Schema

Keep the existing `knowledge_chunks` table and add a long-term memory table:

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

The uniqueness rule prevents one user's repeated memory from creating duplicate
rows while allowing different users to store identical text.

`normalized_text` is produced by trimming, collapsing consecutive whitespace,
and converting to lowercase with `Locale.ROOT`. The original display text stays
in the separate `text` column.

Exact cosine search remains the first-version implementation. Approximate HNSW
or IVFFlat indexes are not added until data volume and query measurements justify
them.

## Knowledge Retrieval

Knowledge retrieval receives `SemanticQuery` and knowledge
`RetrievalSettings`.

The database query first limits candidate recall and returns similarity:

```sql
select source,
       content,
       1 - (embedding <=> ?::vector) as similarity
from knowledge_chunks
order by embedding <=> ?::vector
limit ?;
```

The result pipeline then:

1. Retrieves at most `vectorTopK` candidates.
2. Removes candidates with `similarity < minScore`.
3. Orders by similarity descending.
4. Returns at most `finalTopN` chunks.

The prompt builder never performs another TopK decision.

## Long-Term Memory Writes

After a successful model response:

1. `LongTermMemoryExtractor` extracts candidates.
2. `LongTermMemoryPolicy` rejects unsupported categories and sensitive content.
3. The candidate text is normalized.
4. The embedding client embeds the normalized text.
5. `PgVectorLongTermMemoryRepository` upserts by
   `(user_id, category, normalized_text)`.

For an existing row, the upsert preserves `created_at` and updates:

- `source_conversation_id`
- `confidence`
- `embedding`
- `updated_at`

Embedding or database failure is logged and does not invalidate the chat answer.

## Long-Term Memory Retrieval

Long-term retrieval requires both `userId` and `SemanticQuery`:

```sql
select id,
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
limit ?;
```

The result pipeline uses the long-term memory settings:

1. Retrieve at most `vectorTopK` rows for the authenticated user.
2. Remove rows with `similarity < minScore`.
3. Order by similarity descending.
4. Return at most `finalTopN` memories.

No repository method may omit the user condition. User IDs from request payloads
are not accepted.

## Runtime Chat Flow

1. `AgentChatController` validates `conversationId` and `message`.
2. `CurrentUserProvider.requireUserId()` obtains the user ID from the RuoYi token.
3. `OpenAiAgentChatService` asks `MemoryOrchestrator` to prepare context.
4. The orchestrator reads short-term memory by `conversationId`.
5. It generates one query embedding.
6. It uses the same embedding for knowledge and user-scoped long-term memory
   retrieval.
7. Each semantic source independently applies its
   `vectorTopK -> minScore -> finalTopN` pipeline.
8. `PromptContextBuilder` assembles short-term memory, long-term memory,
   knowledge, and the user question.
9. The chat model generates the answer.
10. The orchestrator records the turn and stores allowed long-term memories for
    the authenticated user.

## Failure Handling

- Missing or invalid RuoYi authentication: return `401`.
- Blank `conversationId` or `message`: return `400`.
- Query embedding failure: return empty knowledge and long-term semantic context;
  short-term context remains available.
- Knowledge query failure: return empty knowledge while retaining other context.
- Long-term memory query failure: return empty long-term memory while retaining
  other context.
- Long-term candidate embedding or persistence failure: log and continue after
  returning the chat answer.
- Invalid retrieval configuration: fail application startup with a clear
  validation message.
- Chat model failure: preserve the existing server error behavior and do not
  record an incomplete assistant turn.

## Migration

1. Add the long-term memory pgvector schema.
2. Add layered retrieval configuration with new property names.
3. Add `PgVectorLongTermMemoryRepository`.
4. Wire `MemoryOrchestrator` and `PromptContextBuilder` into the active chat path.
5. Switch the active long-term repository from JSON to PostgreSQL.
6. Stop using `LocalKnowledgeStore` and `RagPromptBuilder` in the chat path.
7. Keep legacy classes temporarily so migration remains reversible.
8. Remove the legacy classes only in a later cleanup after the new path is
   verified.

The old `agentscope.rag.top-k` and distance configuration are replaced by:

- `agentscope.retrieval.knowledge.vector-top-k`
- `agentscope.retrieval.knowledge.final-top-n`
- `agentscope.retrieval.knowledge.min-score`
- `agentscope.retrieval.long-term-memory.vector-top-k`
- `agentscope.retrieval.long-term-memory.final-top-n`
- `agentscope.retrieval.long-term-memory.min-score`

No automatic fallback to the old property is provided, so an obsolete
configuration cannot silently change retrieval behavior.

## Testing

Focused tests must cover:

- `RetrievalSettings` rejects invalid limits and score ranges.
- The authentication adapter returns the RuoYi user ID and rejects missing login
  context.
- The controller never accepts or trusts a user ID from the request body.
- A chat request generates one query embedding for both semantic sources.
- Knowledge retrieval uses `vectorTopK`, `minScore`, and `finalTopN`.
- Long-term memory retrieval always includes `user_id`.
- Identical memory text for two users produces separate rows.
- Repeated memory for one user updates instead of duplicating.
- Long-term memory writes generate and persist an embedding.
- Prompt knowledge is limited to six default chunks.
- Prompt long-term memory is limited to five default records.
- Knowledge and long-term failures degrade independently.
- Query embedding failure still allows short-term context.
- The active chat service uses `MemoryOrchestrator` instead of the legacy local
  RAG path.
- `/api/config` exposes effective retrieval counts and thresholds but no secrets.

Database and HTTP tests should use mocks or local fakes. A real PostgreSQL
integration test is valuable but remains optional for the first implementation
plan.

## Out of Scope

- External cross-encoder or LLM reranking.
- Dynamic TopK based on query complexity.
- Per-agent retrieval profiles.
- Semantic retrieval for task state.
- Automatic JSON memory import.
- Approximate pgvector indexes.
- A memory administration UI.

## Acceptance Criteria

- The authenticated RuoYi user ID controls all long-term memory reads and writes.
- Knowledge and long-term memory both use layered candidate and final limits.
- One chat request makes one query embedding call for retrieval.
- Default knowledge retrieval recalls 30 candidates and sends at most 6 chunks to
  the prompt when similarity is at least `0.70`.
- Default long-term retrieval recalls 20 user-scoped candidates and sends at most
  5 memories to the prompt when similarity is at least `0.72`.
- Long-term memories are persisted with 1024-dimensional embeddings in
  PostgreSQL.
- Semantic retrieval failures do not prevent a chat response when the model is
  otherwise available.
- The active chat path no longer depends on `LocalKnowledgeStore` or
  `RagPromptBuilder`.
