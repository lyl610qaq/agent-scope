package com.example.demoscope.biz.interview;

public interface InterviewTargetAgent {

    InterviewAgentName name();

    InterviewAgentOutput run(AgentPromptContext context);
}
