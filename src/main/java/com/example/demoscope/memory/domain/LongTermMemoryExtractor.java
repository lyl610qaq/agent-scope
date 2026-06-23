package com.example.demoscope.memory.domain;

import java.util.List;

@FunctionalInterface
public interface LongTermMemoryExtractor {

    List<LongTermMemoryCandidate> extract(MemoryTurn turn);
}
