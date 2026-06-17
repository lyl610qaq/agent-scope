package com.example.demoscope;

public class ModelInterviewMemoryManagerAgent
        implements InterviewMemoryManagerAgent {

    private static final String SYSTEM_PROMPT = """
            You are an interview memory management agent.
            Return one JSON object only. Do not use markdown.
            Suggest concise memory writes. Do not include secrets, tokens,
            raw candidate answers, or raw model JSON.
            Schema:
            {"shortTermWrites":["non-blank"],"longTermWrites":["non-blank"],
            "reason":"non-blank"}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewMemoryManagerAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public MemoryWriteDecision decide(
            AgentPromptContext context,
            InterviewAgentOutput output) {
        String prompt = """
                Memory write suggestions for interview step.
                Task: %s
                Agent: %s
                Output type: %s
                Direction: %s
                Difficulty: %s
                Do not include candidate secrets or raw model JSON.
                """.formatted(
                context.task().type(),
                output.agentName(),
                output.type(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty());
        return aiClient.call(SYSTEM_PROMPT, prompt, MemoryWriteDecision.class);
    }
}
