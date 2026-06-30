create extension if not exists vector;

create table if not exists knowledge_chunks (
    id bigserial primary key,
    source text not null,
    chunk_index integer not null,
    content text not null,
    checksum char(64) not null,
    embedding vector(1024) not null,
    updated_at timestamptz not null default now(),
    unique (source, chunk_index)
);

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
    embedding vector(1024) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (memory_group_id, version)
);

create index if not exists long_term_memories_active_user_idx
    on long_term_memories (user_id, is_active);

create unique index if not exists long_term_memories_active_unique_idx
    on long_term_memories (user_id, category, normalized_text)
    where is_active;

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
);
