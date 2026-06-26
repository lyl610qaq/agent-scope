package com.example.demoscope.common.jdbc;

import com.example.demoscope.common.llm.TokenUsageRecord;
import com.example.demoscope.common.llm.TokenUsageRecorder;
import java.time.Clock;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcOperations;

public class JdbcTokenUsageRecorder implements TokenUsageRecorder {

    private final JdbcOperations jdbc;
    @SuppressWarnings("unused")
    private final Clock clock;

    public JdbcTokenUsageRecorder(JdbcOperations jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void initializeSchema() {
        jdbc.execute("""
                create table if not exists llm_call_records (
                    id uuid primary key,
                    user_id text,
                    conversation_id text,
                    business_type text not null,
                    business_id text,
                    model_name text not null,
                    provider_base_url text not null,
                    endpoint text not null,
                    streaming boolean not null,
                    raw_request_json text not null,
                    request_hash text not null,
                    response_id text,
                    prompt_tokens integer,
                    completion_tokens integer,
                    total_tokens integer,
                    usage_json text,
                    status text not null,
                    error_message text,
                    started_at timestamptz not null,
                    completed_at timestamptz
                )
                """);
        jdbc.execute("""
                create index if not exists llm_call_records_user_time_idx
                on llm_call_records (user_id, started_at desc)
                """);
        jdbc.execute("""
                create index if not exists llm_call_records_business_time_idx
                on llm_call_records (business_type, started_at desc)
                """);
        jdbc.execute("""
                create index if not exists llm_call_records_conversation_idx
                on llm_call_records (conversation_id)
                """);
    }

    @Override
    public void record(TokenUsageRecord record) {
        jdbc.update(
                """
                        insert into llm_call_records (
                            id,
                            user_id,
                            conversation_id,
                            business_type,
                            business_id,
                            model_name,
                            provider_base_url,
                            endpoint,
                            streaming,
                            raw_request_json,
                            request_hash,
                            response_id,
                            prompt_tokens,
                            completion_tokens,
                            total_tokens,
                            usage_json,
                            status,
                            error_message,
                            started_at,
                            completed_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.id(),
                record.userId(),
                record.conversationId(),
                record.businessType(),
                record.businessId(),
                record.modelName(),
                record.providerBaseUrl(),
                record.endpoint(),
                record.streaming(),
                record.rawRequestJson(),
                record.requestHash(),
                record.responseId(),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                record.usageJson(),
                record.status(),
                record.errorMessage(),
                record.startedAt(),
                record.completedAt());
    }
}
