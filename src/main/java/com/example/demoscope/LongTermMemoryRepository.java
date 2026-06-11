package com.example.demoscope;

import java.util.List;

public interface LongTermMemoryRepository {

    List<LongTermMemory> findRelevant(String userId, String query);

    void save(String userId, String conversationId, LongTermMemoryCandidate candidate);
}
