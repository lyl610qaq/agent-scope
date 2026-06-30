package com.example.demoscope.biz.interview;

import com.example.demoscope.biz.interview.InterviewTranscriptRenderer;
import com.example.demoscope.common.llm.InterviewAiJsonClient;
import com.example.demoscope.domain.memory.LongTermMemory;

public class ModelInterviewRagPlannerAgent implements InterviewRagPlannerAgent {

    private static final String SYSTEM_PROMPT = """
            You are an interview RAG planning agent.
            Return one JSON object only. Do not use markdown.
            Produce one to three precise retrieval queries.
            Schema:
            {"queries":[{"query":"non-blank","topK":1,
            "filters":["tag"],"purpose":"non-blank",
            "expectedEvidenceType":"non-blank"}]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewRagPlannerAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public RagQueryPlan plan(AgentPromptContext context) {
        String prompt = """
                Task: %s
                Agent focus: %s
                Direction: %s
                Difficulty: %s
                Current question: %s
                Candidate answer present: %s
                Memory: %s %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                context.routerDecision() == null
                        ? context.task().defaultAgent()
                        : context.routerDecision().suggestedFocus(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.currentQuestion() == null
                        ? ""
                        : context.currentQuestion().text(),
                context.candidateAnswer() != null,
                context.shortTermMemory(),
                context.longTermMemory(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
        return aiClient.call(SYSTEM_PROMPT, prompt, RagQueryPlan.class);
    }
}
