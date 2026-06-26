package com.example.demoscope.common.llm;

import java.time.Instant;
import java.util.UUID;

public record TokenUsageRecord(
        UUID id,
        String userId,
        String conversationId,
        String businessType,
        String businessId,
        String modelName,
        String providerBaseUrl,
        String endpoint,
        boolean streaming,
        String rawRequestJson,
        String requestHash,
        String responseId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String usageJson,
        String status,
        String errorMessage,
        Instant startedAt,
        Instant completedAt) {
}
