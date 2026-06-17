package com.example.demoscope;

public interface InterviewMemoryManagerAgent {

    MemoryWriteDecision decide(
            AgentPromptContext context,
            InterviewAgentOutput output);
}
