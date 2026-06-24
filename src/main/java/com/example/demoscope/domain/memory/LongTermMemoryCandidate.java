package com.example.demoscope.domain.memory;

public record LongTermMemoryCandidate(
        LongTermMemoryCategory category,
        String text,
        double confidence) {
}
