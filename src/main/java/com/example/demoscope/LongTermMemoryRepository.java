package com.example.demoscope;

import java.util.List;

public interface LongTermMemoryRepository {

    List<LongTermMemory> findRelevant(String query);

    void save(String conversationId, LongTermMemoryCandidate candidate);
}
