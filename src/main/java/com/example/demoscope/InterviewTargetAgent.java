package com.example.demoscope;

public interface InterviewTargetAgent {

    InterviewAgentName name();

    InterviewAgentOutput run(AgentPromptContext context);
}
