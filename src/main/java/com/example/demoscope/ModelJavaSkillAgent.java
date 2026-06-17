package com.example.demoscope;

public class ModelJavaSkillAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a Java skill interview agent.
            Return one JSON object only. Do not use markdown.
            Focus on Java fundamentals, JVM, concurrency, collections, and Spring.
            Return an InterviewAgentOutput whose agentName is JAVA_SKILL.
            For question tasks, type must be QUESTION.
            For answer evaluation tasks, type must be ANSWER_EVALUATION.
            Schema for QUESTION:
            {"agentName":"JAVA_SKILL","type":"QUESTION",
            "generatedQuestion":{"question":"non-blank",
            "skillTags":["tag"],"evidenceIds":["id"]},
            "usedEvidenceIds":["id"]}
            Schema for ANSWER_EVALUATION:
            {"agentName":"JAVA_SKILL","type":"ANSWER_EVALUATION",
            "answerEvaluation":{"internalEvaluation":"non-blank",
            "abilityTags":["tag"],"decision":"FOLLOW_UP|NEXT_MAIN_QUESTION",
            "followUpQuestion":"required only for FOLLOW_UP",
            "decisionReason":"non-blank"},"usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelJavaSkillAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.JAVA_SKILL;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        return aiClient.call(
                SYSTEM_PROMPT,
                targetPrompt(context),
                InterviewAgentOutput.class);
    }

    static String targetPrompt(AgentPromptContext context) {
        return """
                Task: %s
                Direction: %s
                Difficulty: %s
                Main question number: %d
                Current question: %s
                Candidate answer: %s
                Router focus: %s
                Evidence:
                %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.task().mainQuestionNumber(),
                context.currentQuestion() == null
                        ? ""
                        : context.currentQuestion().text(),
                context.candidateAnswer() == null
                        ? ""
                        : context.candidateAnswer(),
                context.routerDecision() == null
                        ? ""
                        : context.routerDecision().suggestedFocus(),
                context.ragEvidence(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
    }
}
