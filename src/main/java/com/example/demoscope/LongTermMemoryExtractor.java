package com.example.demoscope;

import java.util.List;

@FunctionalInterface
public interface LongTermMemoryExtractor {

    List<LongTermMemoryCandidate> extract(MemoryTurn turn);
}
