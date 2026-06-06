package com.example.demoscope;

import java.time.Instant;

public record MemoryTurn(String userMessage, String assistantMessage, Instant createdAt) {
}
