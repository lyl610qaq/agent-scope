package com.example.demoscope.memory.domain;

import java.time.Instant;

public record LongTermMemory(
        String id,
        String userId,
        LongTermMemoryCategory category,
        String text,
        String sourceConversationId,
        double confidence,
        Instant createdAt,
        Instant updatedAt) {
}
