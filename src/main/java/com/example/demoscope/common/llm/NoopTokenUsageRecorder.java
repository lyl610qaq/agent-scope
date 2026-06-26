package com.example.demoscope.common.llm;

public final class NoopTokenUsageRecorder implements TokenUsageRecorder {

    @Override
    public void record(TokenUsageRecord record) {
    }
}
