package com.example.demoscope;

import java.util.List;

public class EmptyLongTermMemoryRepository implements LongTermMemoryRepository {

    @Override
    public List<LongTermMemory> findRelevant(String userId, SemanticQuery query) {
        return List.of();
    }

    @Override
    public void save(String userId, String conversationId, LongTermMemoryCandidate candidate) {
    }
}
