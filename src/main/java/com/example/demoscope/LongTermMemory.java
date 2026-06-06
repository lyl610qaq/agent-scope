package com.example.demoscope;

import java.time.Instant;

public record LongTermMemory(
        String id,
        LongTermMemoryCategory category,
        String text,
        String sourceConversationId,
        double confidence,
        Instant createdAt,
        Instant updatedAt) {
}
