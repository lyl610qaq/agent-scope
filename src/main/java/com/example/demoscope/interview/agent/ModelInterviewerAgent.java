package com.example.demoscope.interview.agent;

import com.example.demoscope.interview.infrastructure.InterviewAiJsonClient;

public class ModelInterviewerAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a general Java technical interviewer agent.
            Return one JSON object only. Do not use markdown.
            Keep the interview coherent and avoid repeating questions.
            Return an InterviewAgentOutput whose agentName is INTERVIEWER.
            For question tasks, type must be QUESTION.
            For answer evaluation tasks, type must be ANSWER_EVALUATION.
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewerAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.INTERVIEWER;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt(context),
                InterviewAgentOutput.class);
    }

    private String prompt(AgentPromptContext context) {
        return ModelJavaSkillAgent.targetPrompt(context);
    }
}
