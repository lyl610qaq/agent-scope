package com.example.demoscope.interview.agent;

import com.example.demoscope.interview.infrastructure.InterviewAiJsonClient;

public class ModelProjectAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a project-depth Java backend interview agent.
            Return one JSON object only. Do not use markdown.
            Focus on project experience, trade-offs, incidents, architecture,
            and engineering judgment.
            Return an InterviewAgentOutput whose agentName is PROJECT.
            For question tasks, type must be QUESTION.
            For answer evaluation tasks, type must be ANSWER_EVALUATION.
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelProjectAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.PROJECT;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        return aiClient.call(
                SYSTEM_PROMPT,
                ModelJavaSkillAgent.targetPrompt(context),
                InterviewAgentOutput.class);
    }
}
