package com.example.demoscope.memory.domain;

public record LongTermMemoryCandidate(
        LongTermMemoryCategory category,
        String text,
        double confidence) {
}
