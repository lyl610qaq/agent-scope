package com.example.demoscope;

public class ModelInterviewRouterAgent implements InterviewRouterAgent {

    private static final String SYSTEM_PROMPT = """
            You are an interview Router agent.
            Return one JSON object only. Do not use markdown.
            Choose the next agent from the allowed agent list only.
            Schema:
            {"nextAgent":"INTERVIEWER|PROJECT|JAVA_SKILL|SCORE",
            "reason":"non-blank","confidence":0.0,
            "suggestedFocus":"non-blank","usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewRouterAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public RouterDecision route(AgentPromptContext context) {
        String prompt = """
                Task: %s
                Allowed agents: %s
                Default agent: %s
                Direction: %s
                Difficulty: %s
                Current question: %s
                Candidate answer present: %s
                Short-term memory: %s
                Long-term memory: %s
                Evidence: %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                context.task().allowedAgents(),
                context.task().defaultAgent(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.currentQuestion() == null
                        ? ""
                        : context.currentQuestion().text(),
                context.candidateAnswer() != null,
                context.shortTermMemory(),
                context.longTermMemory(),
                context.ragEvidence(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
        return aiClient.call(SYSTEM_PROMPT, prompt, RouterDecision.class);
    }
}
