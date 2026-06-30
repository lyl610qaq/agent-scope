package com.example.demoscope.common.memory;

import com.example.demoscope.domain.memory.MemoryTurn;
import com.example.demoscope.domain.memory.ShortTermMemoryStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryShortTermMemoryStore implements ShortTermMemoryStore {

    private final int maxTurns;
    private final ConcurrentMap<ConversationKey, Deque<MemoryTurn>> turnsByConversation = new ConcurrentHashMap<>();

    public InMemoryShortTermMemoryStore(int maxTurns) {
        this.maxTurns = Math.max(1, maxTurns);
    }

    @Override
    public void append(String userId, String conversationId, MemoryTurn turn) {
        Deque<MemoryTurn> turns = turnsByConversation.computeIfAbsent(
                new ConversationKey(userId, conversationId),
                ignored -> new ArrayDeque<>());
        synchronized (turns) {
            turns.addLast(turn);
            while (turns.size() > maxTurns) {
                turns.removeFirst();
            }
        }
    }

    @Override
    public List<MemoryTurn> recent(String userId, String conversationId) {
        Deque<MemoryTurn> turns = turnsByConversation.get(new ConversationKey(userId, conversationId));
        if (turns == null) {
            return List.of();
        }
        synchronized (turns) {
            return List.copyOf(new ArrayList<>(turns));
        }
    }

    private record ConversationKey(String userId, String conversationId) {
    }
}
