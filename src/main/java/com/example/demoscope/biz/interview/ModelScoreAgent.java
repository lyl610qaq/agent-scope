package com.example.demoscope.biz.interview;

import com.example.demoscope.biz.interview.InterviewTranscriptRenderer;
import com.example.demoscope.common.llm.InterviewAiJsonClient;

public class ModelScoreAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a Java backend interview scoring agent.
            Return one JSON object only. Do not use markdown.
            Return an InterviewAgentOutput whose agentName is SCORE and type is SCORE_REPORT.
            Scores are integers from 0 to 100.
            Schema:
            {"agentName":"SCORE","type":"SCORE_REPORT",
            "reportDraft":{"overallScore":0,
            "scores":{"javaFundamentals":0,"concurrency":0,"jvm":0,
            "spring":0,"database":0,"engineering":0},
            "strengths":["non-blank"],"weaknesses":["non-blank"],
            "improvementSuggestions":["non-blank"]},
            "usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelScoreAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.SCORE;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        String reviewFeedback = context.task().reviewFeedback() == null
                ? "None"
                : context.task().reviewFeedback();
        String prompt = """
                Direction: %s
                Difficulty: %s
                Router focus: %s
                Score review feedback:
                %s
                Evidence:
                %s
                Transcript:
                %s
                """.formatted(
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.routerDecision() == null
                        ? ""
                        : context.routerDecision().suggestedFocus(),
                reviewFeedback,
                context.ragEvidence(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt,
                InterviewAgentOutput.class);
    }
}
