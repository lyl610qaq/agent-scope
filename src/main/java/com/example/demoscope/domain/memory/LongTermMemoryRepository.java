package com.example.demoscope.domain.memory;

import com.example.demoscope.domain.rag.SemanticQuery;
import java.util.List;

public interface LongTermMemoryRepository {

    List<LongTermMemory> findRelevant(String userId, SemanticQuery query);

    void save(String userId, String conversationId, LongTermMemoryCandidate candidate);
}
