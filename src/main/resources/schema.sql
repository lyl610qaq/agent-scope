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
