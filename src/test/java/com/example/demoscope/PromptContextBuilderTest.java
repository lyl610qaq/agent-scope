package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class PromptContextBuilderTest {

    private final PromptContextBuilder builder = new PromptContextBuilder();

    @Test
    void labelsEachAvailableContextLayer() {
        MemoryContext context = new MemoryContext(
                List.of(new MemoryTurn("previous question", "previous answer", Instant.parse("2026-06-06T10:00:00Z"))),
                List.of(new LongTermMemory(
                        "memory-1",
                        LongTermMemoryCategory.PREFERENCE,
                        "user prefers concise answers",
                        "conversation-a",
                        0.9,
                        Instant.parse("2026-06-06T10:00:00Z"),
                        Instant.parse("2026-06-06T10:00:00Z"))),
                List.of(new KnowledgeChunk("guide.md", "Project uses Java 17")));

        String prompt = builder.build("You are helpful.", context, "Which version?");

        assertTrue(prompt.contains("System instructions"));
        assertTrue(prompt.contains("Short-term memory"));
        assertTrue(prompt.contains("Long-term memory"));
        assertTrue(prompt.contains("Knowledge base"));
        assertTrue(prompt.endsWith("User question:\nWhich version?"));
    }

    @Test
    void omitsEmptyContextSections() {
        String prompt = builder.build(
                "You are helpful.",
                new MemoryContext(List.of(), List.of(), List.of()),
                "hello");

        assertFalse(prompt.contains("Short-term memory"));
        assertFalse(prompt.contains("Long-term memory"));
        assertFalse(prompt.contains("Knowledge base"));
    }
}
