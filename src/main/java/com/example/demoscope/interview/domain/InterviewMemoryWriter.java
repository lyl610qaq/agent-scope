package com.example.demoscope.interview.domain;

import com.example.demoscope.interview.agent.MemoryWriteDecision;

public interface InterviewMemoryWriter {

    void write(InterviewSnapshot snapshot, MemoryWriteDecision decision);
}
