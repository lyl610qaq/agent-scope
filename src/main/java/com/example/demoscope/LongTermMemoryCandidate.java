package com.example.demoscope;

public record LongTermMemoryCandidate(
        LongTermMemoryCategory category,
        String text,
        double confidence) {
}
