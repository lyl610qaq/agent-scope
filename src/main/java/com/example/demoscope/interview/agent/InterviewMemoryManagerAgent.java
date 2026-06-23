package com.example.demoscope.interview.agent;

public interface InterviewMemoryManagerAgent {

    MemoryWriteDecision decide(
            AgentPromptContext context,
            InterviewAgentOutput output);
}
