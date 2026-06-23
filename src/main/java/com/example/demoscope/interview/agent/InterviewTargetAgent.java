package com.example.demoscope.interview.agent;

public interface InterviewTargetAgent {

    InterviewAgentName name();

    InterviewAgentOutput run(AgentPromptContext context);
}
