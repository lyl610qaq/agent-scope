# Agent Memory Layer Design

Date: 2026-06-04

## Goal

Add a lightweight but well-separated memory layer to the current Spring Boot AgentScope demo. The agent will use three context sources:

- Short-term memory for recent turns in the active conversation.
- Long-term memory for low-risk stable user/project facts.
- RAG over local knowledge files indexed into PostgreSQL with pgvector.

The design should be practical for the current demo while keeping storage and retrieval boundaries clear enough for future replacement.

## Current Project Context

The current application is a small Spring Boot Java 17 project. `AgentChatController` exposes `POST /api/chat`, and `OpenAiAgentChatService` builds a RAG-enhanced prompt before calling `ReActAgent` with `OpenAIChatModel`. RAG currently uses `LocalKnowledgeStore` to scan `.md` and `.txt` files under `data/knowledge`, score them with keyword matching, and pass chunks to `RagPromptBuilder`.

The new design replaces keyword RAG with pgvector-backed semantic retrieval and adds explicit short-term and long-term memory layers.

## Decisions

- Scope: lightweight demo implementation.
- Short-term boundary: each chat conversation is isolated by `conversationId`.
- Long-term writes: automatic extraction after each turn.
- Long-term save scope: only low-risk information, such as preferences, project conventions, stable facts, and common configuration. Temporary conversation details and sensitive information are not saved.
- Vector store: existing local PostgreSQL database with pgvector installed.
- Knowledge database state: empty; the application must create schema and populate it.
- Embedding API: SiliconFlow embeddings endpoint.
- Embedding model: `Qwen/Qwen3-Embedding-4B`.
- Embedding dimensions: `1024`.
- Knowledge indexing trigger: manual `POST /api/knowledge/reindex`.
- Architecture: layered orchestrator, with short-term memory, long-term memory, and RAG kept separate.

## Architecture

`AgentChatController` accepts `message` and `conversationId`. The frontend will generate and persist a browser-session `conversationId`, and the backend will reject blank `message` or `conversationId`.

`OpenAiAgentChatService` remains the main chat service but delegates memory and RAG retrieval to a `MemoryOrchestrator`. The service then calls the configured chat model and records the result through the memory layer.

The main components are:

- `MemoryOrchestrator`: coordinates all context retrieval and post-response memory updates.
- `ShortTermMemoryStore`: keeps recent turns by `conversationId` in memory.
- `LongTermMemoryStore`: stores durable low-risk memory records in a local JSON file.
- `LongTermMemoryExtractor`: extracts candidate long-term memories after a turn and filters them by allowed categories and sensitive patterns.
- `VectorKnowledgeStore`: queries PostgreSQL + pgvector for relevant knowledge chunks.
- `KnowledgeIndexer`: scans `data/knowledge`, chunks supported files, generates embeddings, and upserts rows into pgvector.
- `SiliconFlowEmbeddingClient`: calls SiliconFlow `POST /v1/embeddings`.
- `PromptContextBuilder`: assembles system prompt, short-term memory, long-term memory, RAG context, and the user message with clear labels.

This keeps personal memory separate from source-grounded knowledge. It also avoids mixing the safety policy for long-term memory with the retrieval policy for project documents.

## Runtime Chat Flow

1. The client sends `POST /api/chat` with `conversationId` and `message`.
2. The controller validates both fields.
3. `MemoryOrchestrator` fetches short-term turns for the conversation.
4. It fetches relevant low-risk long-term memories.
5. It embeds the user question with SiliconFlow and queries pgvector for topK knowledge chunks.
6. `PromptContextBuilder` assembles a prompt with labeled sections:
   - system instructions
   - short-term conversation memory
   - long-term memory
   - source-grounded knowledge
   - user question
7. `OpenAiAgentChatService` calls `ReActAgent`.
8. After the model response, the orchestrator appends the user/assistant turn to short-term memory.
9. The orchestrator runs long-term extraction and saves only allowed, low-risk memory records.

Chat requests do not rescan local files and do not write new RAG chunks. They only query already-indexed pgvector rows.

## Knowledge Reindex Flow

`POST /api/knowledge/reindex` manually synchronizes `data/knowledge` into pgvector.

1. Validate PostgreSQL, pgvector, and SiliconFlow embedding configuration.
2. Ensure the pgvector schema exists.
3. Scan supported files under `data/knowledge`: `.md` and `.txt`.
4. Normalize whitespace and split text into stable chunks.
5. Compute a checksum for each chunk.
6. Skip chunks whose source and checksum already exist.
7. Call SiliconFlow embeddings with:
   - endpoint: `${agentscope.embedding.base-url}/embeddings`
   - model: `Qwen/Qwen3-Embedding-4B`
   - dimensions: `1024`
8. Upsert chunk rows into PostgreSQL.
9. Return indexing statistics: scanned files, generated chunks, skipped chunks, indexed chunks, and failures.

Manual reindex is used because embedding calls are external requests and may have cost.

## PostgreSQL Schema

The implementation will use this `knowledge_chunks` table:

```sql
create extension if not exists vector;

create table if not exists knowledge_chunks (
    id bigserial primary key,
    source text not null,
    chunk_index integer not null,
    content text not null,
    checksum text not null,
    metadata jsonb not null default '{}'::jsonb,
    embedding vector(1024) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (source, chunk_index, checksum)
);
```

The first implementation will use exact cosine-distance search with pgvector's `<=>` operator and `limit topK`. It will not add an approximate HNSW or IVFFlat index yet, because the demo dataset is expected to be small and exact search avoids version-specific pgvector index tuning. Approximate vector indexes are out of scope for the first implementation.

## Configuration

Configuration should be separate for chat, embedding, RAG, and memory.

Expected properties:

- `agentscope.openai.api-key`
- `agentscope.openai.model-name`
- `agentscope.openai.base-url`
- `agentscope.embedding.api-key`
- `agentscope.embedding.base-url=https://api.siliconflow.cn/v1`
- `agentscope.embedding.model=Qwen/Qwen3-Embedding-4B`
- `agentscope.embedding.dimensions=1024`
- `agentscope.rag.enabled=true`
- `agentscope.rag.knowledge-dir=data/knowledge`
- `agentscope.rag.top-k=3`
- `agentscope.rag.max-distance` optional cosine-distance threshold; when omitted, retrieval uses only `top-k`
- `agentscope.memory.short-term.max-turns`
- `agentscope.memory.long-term.path=data/memory/long-term-memory.json`

Embedding and chat configuration are intentionally separate so the chat model can use one provider while embeddings use SiliconFlow.

## Long-Term Memory Policy

The long-term memory extractor will use the configured chat model with a constrained JSON extraction prompt to produce candidate memory records. A deterministic filter then accepts only approved categories and rejects sensitive-looking content before anything is written to disk. The store only accepts records in approved categories:

- `preference`
- `project_convention`
- `stable_fact`
- `common_config`

The extractor rejects:

- API keys, tokens, passwords, secrets, or private credentials.
- One-off task details that do not represent a durable preference or fact.
- Model guesses that are not grounded in the conversation.
- Sensitive personal information.

Records should include text, category, source conversation id, created time, updated time, and optional confidence. Deduplication should normalize memory text and update existing records instead of accumulating repeated copies.

## Error Handling

Chat path:

- Missing or blank `message` or `conversationId`: return `400`.
- Missing chat model API key: return the existing server error behavior.
- Short-term memory failure: log and continue with empty short-term memory.
- Long-term memory read/write failure: log and continue.
- Embedding or pgvector retrieval failure during chat: log and continue without RAG context.
- Chat model failure: return an error because the agent cannot answer.

Reindex path:

- Missing PostgreSQL configuration, unavailable pgvector, missing embedding API key, or embedding dimension mismatch: return a clear error.
- Individual file or chunk failure: continue indexing other chunks and include failures in the response.
- Repeated reindex: skip unchanged chunks by checksum.

## Testing Plan

Focused tests should cover:

- Controller validation for `message` and `conversationId`.
- `conversationId` isolation in `ShortTermMemoryStore`.
- Short-term truncation by configured max turns.
- Long-term extraction accepts only allowed categories.
- Long-term extraction rejects obvious secrets and one-off details.
- `PromptContextBuilder` labels short-term memory, long-term memory, and RAG context clearly.
- Prompt assembly still works when one or more context layers are empty.
- `SiliconFlowEmbeddingClient` parses `data[].embedding` and validates dimensions.
- `KnowledgeIndexer` scans files, chunks content, skips unchanged checksums, and reports failures.
- `VectorKnowledgeStore` maps query embeddings to topK `KnowledgeChunk` records.
- Reindex controller returns useful statistics.

Where external services are involved, tests should use fakes or mocks instead of calling SiliconFlow or PostgreSQL directly. Real local pgvector integration testing is out of scope for the first implementation plan.

## Out of Scope

- Multi-user authentication or authorization.
- Production memory governance UI.
- Automatic background file watching.
- Reindex on every chat request.
- Storing long-term personal memory in pgvector.
- Replacing the chat model provider.

## Acceptance Criteria

- `/api/chat` supports isolated conversation memory through `conversationId`.
- The agent can answer with short-term, long-term, and pgvector RAG context when available.
- Long-term memory persists low-risk records and does not block chat on failure.
- `POST /api/knowledge/reindex` builds pgvector data from `data/knowledge`.
- SiliconFlow embeddings use `Qwen/Qwen3-Embedding-4B` with `dimensions=1024`.
- Missing optional memory/RAG layers degrade gracefully during chat.
- Tests cover the main memory boundaries and indexing behavior.
