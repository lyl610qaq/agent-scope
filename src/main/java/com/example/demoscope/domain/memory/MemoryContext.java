package com.example.demoscope.domain.memory;

import com.example.demoscope.domain.rag.KnowledgeChunk;
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
