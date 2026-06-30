package com.example.demoscope.biz.interview;

public interface InterviewMemoryManagerAgent {

    MemoryWriteDecision decide(
            AgentPromptContext context,
            InterviewAgentOutput output);
}
