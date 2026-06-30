package com.example.demoscope.common.jdbc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.demoscope.common.llm.TokenUsageRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

class JdbcTokenUsageRecorderTest {

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-25T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void initializesSchemaAndIndexes() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        JdbcTokenUsageRecorder recorder = new JdbcTokenUsageRecorder(jdbc, clock);

        recorder.initializeSchema();

        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("create table if not exists llm_call_records"));
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("llm_call_records_user_time_idx"));
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("llm_call_records_business_time_idx"));
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("llm_call_records_conversation_idx"));
    }

    @Test
    void recordsRawRequestAndTokenUsage() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        JdbcTokenUsageRecorder recorder = new JdbcTokenUsageRecorder(jdbc, clock);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        TokenUsageRecord record = new TokenUsageRecord(
                id,
                "user-42",
                "conversation-a",
                "CHAT",
                null,
                "test-model",
                "https://api.example.com/v1",
                "/chat/completions",
                true,
                "{\"model\":\"test-model\"}",
                "hash-123",
                "response-1",
                11,
                22,
                33,
                "{\"prompt_tokens\":11,\"completion_tokens\":22,\"total_tokens\":33}",
                "SUCCESS",
                null,
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:01Z"));

        recorder.record(record);

        verify(jdbc).update(
                org.mockito.ArgumentMatchers.contains("insert into llm_call_records"),
                eq(id),
                eq("user-42"),
                eq("conversation-a"),
                eq("CHAT"),
                eq(null),
                eq("test-model"),
                eq("https://api.example.com/v1"),
                eq("/chat/completions"),
                eq(true),
                eq("{\"model\":\"test-model\"}"),
                eq("hash-123"),
                eq("response-1"),
                eq(11),
                eq(22),
                eq(33),
                eq("{\"prompt_tokens\":11,\"completion_tokens\":22,\"total_tokens\":33}"),
                eq("SUCCESS"),
                eq(null),
                eq(Instant.parse("2026-06-25T00:00:00Z")),
                eq(Instant.parse("2026-06-25T00:00:01Z")));
    }
}
