package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class InMemoryShortTermMemoryStoreTest {

    @Test
    void isolatesTurnsByConversationId() {
        ShortTermMemoryStore store = new InMemoryShortTermMemoryStore(3);
        MemoryTurn first = new MemoryTurn("hello", "hi", Instant.parse("2026-06-06T10:00:00Z"));
        MemoryTurn second = new MemoryTurn("status", "ready", Instant.parse("2026-06-06T10:01:00Z"));

        store.append("conversation-a", first);
        store.append("conversation-b", second);

        assertEquals(List.of(first), store.recent("conversation-a"));
        assertEquals(List.of(second), store.recent("conversation-b"));
    }

    @Test
    void keepsOnlyConfiguredNumberOfRecentTurns() {
        ShortTermMemoryStore store = new InMemoryShortTermMemoryStore(2);
        MemoryTurn first = turn("one");
        MemoryTurn second = turn("two");
        MemoryTurn third = turn("three");

        store.append("conversation", first);
        store.append("conversation", second);
        store.append("conversation", third);

        assertEquals(List.of(second, third), store.recent("conversation"));
    }

    private MemoryTurn turn(String value) {
        return new MemoryTurn(value, value + "-answer", Instant.parse("2026-06-06T10:00:00Z"));
    }
}
