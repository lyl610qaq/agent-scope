package com.example.demoscope.memory.domain;

import java.time.Instant;

public record MemoryTurn(String userMessage, String assistantMessage, Instant createdAt) {
}
