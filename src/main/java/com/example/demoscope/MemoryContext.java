package com.example.demoscope;

import java.util.List;

public record MemoryContext(
        List<MemoryTurn> shortTermTurns,
        List<LongTermMemory> longTermMemories,
        List<KnowledgeChunk> knowledgeChunks) {

    public MemoryContext {
        shortTermTurns = List.copyOf(shortTermTurns);
        longTermMemories = List.copyOf(longTermMemories);
        knowledgeChunks = List.copyOf(knowledgeChunks);
    }
}
