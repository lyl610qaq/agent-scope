package com.example.demoscope.memory.domain;

import com.example.demoscope.knowledge.domain.KnowledgeChunk;
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
