package com.example.demoscope;

import java.util.List;

public interface ShortTermMemoryStore {

    void append(String conversationId, MemoryTurn turn);

    List<MemoryTurn> recent(String conversationId);
}
