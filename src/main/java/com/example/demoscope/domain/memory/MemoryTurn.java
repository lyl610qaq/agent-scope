package com.example.demoscope.domain.memory;

import java.time.Instant;

public record MemoryTurn(String userMessage, String assistantMessage, Instant createdAt) {
}
