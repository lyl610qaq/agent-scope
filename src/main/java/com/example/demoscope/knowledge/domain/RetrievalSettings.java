package com.example.demoscope.knowledge.domain;

public record RetrievalSettings(int vectorTopK, int finalTopN, double minScore) {

    public RetrievalSettings {
        if (vectorTopK < 1) {
            throw new IllegalArgumentException("vectorTopK must be at least 1");
        }
        if (finalTopN < 1) {
            throw new IllegalArgumentException("finalTopN must be at least 1");
        }
        if (vectorTopK < finalTopN) {
            throw new IllegalArgumentException(
                    "vectorTopK must be greater than or equal to finalTopN");
        }
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be between 0 and 1");
        }
    }
}
