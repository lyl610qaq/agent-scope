package com.example.demoscope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryShortTermMemoryStore implements ShortTermMemoryStore {

    private final int maxTurns;
    private final ConcurrentMap<String, Deque<MemoryTurn>> turnsByConversation = new ConcurrentHashMap<>();

    public InMemoryShortTermMemoryStore(int maxTurns) {
        this.maxTurns = Math.max(1, maxTurns);
    }

    @Override
    public void append(String conversationId, MemoryTurn turn) {
        Deque<MemoryTurn> turns = turnsByConversation.computeIfAbsent(
                conversationId,
                ignored -> new ArrayDeque<>());
        synchronized (turns) {
            turns.addLast(turn);
            while (turns.size() > maxTurns) {
                turns.removeFirst();
            }
        }
    }

    @Override
    public List<MemoryTurn> recent(String conversationId) {
        Deque<MemoryTurn> turns = turnsByConversation.get(conversationId);
        if (turns == null) {
            return List.of();
        }
        synchronized (turns) {
            return List.copyOf(new ArrayList<>(turns));
        }
    }
}
