package com.example.demoscope.common.memory;

import com.example.demoscope.domain.rag.SemanticQuery;
import com.example.demoscope.domain.memory.LongTermMemory;
import com.example.demoscope.domain.memory.LongTermMemoryCandidate;
import com.example.demoscope.domain.memory.LongTermMemoryRepository;
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
