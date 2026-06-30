package com.example.demoscope.common.llm;

public record TokenUsageContext(
        String userId,
        String conversationId,
        String businessType,
        String businessId) {

    public static TokenUsageContext unknown() {
        return new TokenUsageContext(null, null, "UNKNOWN", null);
    }
}
