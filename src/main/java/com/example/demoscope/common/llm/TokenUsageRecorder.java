package com.example.demoscope.common.llm;

@FunctionalInterface
public interface TokenUsageRecorder {

    void record(TokenUsageRecord record);
}
