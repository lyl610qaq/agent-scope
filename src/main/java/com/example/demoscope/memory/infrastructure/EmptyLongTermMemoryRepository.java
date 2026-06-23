package com.example.demoscope.memory.infrastructure;

import com.example.demoscope.knowledge.domain.SemanticQuery;
import com.example.demoscope.memory.domain.LongTermMemory;
import com.example.demoscope.memory.domain.LongTermMemoryCandidate;
import com.example.demoscope.memory.domain.LongTermMemoryRepository;
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
