package com.example.demoscope.common.memory;

import com.example.demoscope.domain.memory.MemoryTurn;
import com.example.demoscope.domain.memory.ShortTermMemoryStore;
import com.example.demoscope.common.memory.InMemoryShortTermMemoryStore;
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

        store.append("user-a", "conversation-a", first);
        store.append("user-a", "conversation-b", second);

        assertEquals(List.of(first), store.recent("user-a", "conversation-a"));
        assertEquals(List.of(second), store.recent("user-a", "conversation-b"));
    }

    @Test
    void isolatesSameConversationIdByUserId() {
        ShortTermMemoryStore store = new InMemoryShortTermMemoryStore(3);
        MemoryTurn first = turn("alice");
        MemoryTurn second = turn("bob");

        store.append("user-a", "shared-conversation", first);
        store.append("user-b", "shared-conversation", second);

        assertEquals(List.of(first), store.recent("user-a", "shared-conversation"));
        assertEquals(List.of(second), store.recent("user-b", "shared-conversation"));
    }

    @Test
    void keepsOnlyConfiguredNumberOfRecentTurns() {
        ShortTermMemoryStore store = new InMemoryShortTermMemoryStore(2);
        MemoryTurn first = turn("one");
        MemoryTurn second = turn("two");
        MemoryTurn third = turn("three");

        store.append("user-a", "conversation", first);
        store.append("user-a", "conversation", second);
        store.append("user-a", "conversation", third);

        assertEquals(List.of(second, third), store.recent("user-a", "conversation"));
    }

    private MemoryTurn turn(String value) {
        return new MemoryTurn(value, value + "-answer", Instant.parse("2026-06-06T10:00:00Z"));
    }
}
