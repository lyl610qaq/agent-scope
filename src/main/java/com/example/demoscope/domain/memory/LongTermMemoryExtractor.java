package com.example.demoscope.domain.memory;

import java.util.List;

@FunctionalInterface
public interface LongTermMemoryExtractor {

    List<LongTermMemoryCandidate> extract(MemoryTurn turn);
}
