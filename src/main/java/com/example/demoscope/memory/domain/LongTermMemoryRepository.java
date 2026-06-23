package com.example.demoscope.memory.domain;

import com.example.demoscope.knowledge.domain.SemanticQuery;
import java.util.List;

public interface LongTermMemoryRepository {

    List<LongTermMemory> findRelevant(String userId, SemanticQuery query);

    void save(String userId, String conversationId, LongTermMemoryCandidate candidate);
}
