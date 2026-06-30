package com.example.demoscope.domain.interview;

import com.example.demoscope.biz.interview.MemoryWriteDecision;

public interface InterviewMemoryWriter {

    void write(InterviewSnapshot snapshot, MemoryWriteDecision decision);
}
