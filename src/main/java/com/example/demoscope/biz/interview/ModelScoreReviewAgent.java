package com.example.demoscope.biz.interview;

import com.example.demoscope.common.llm.InterviewAiJsonClient;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;

public class ModelScoreReviewAgent implements ScoreReviewAgent {

    private static final String SYSTEM_PROMPT = """
            You review a Java backend interview score report before it is saved.
            Return one JSON object only. Do not use markdown.
            Approve only when the scores and feedback are consistent with the
            transcript, answer evaluations, and interview difficulty.
            If approved is false, revisionInstructions must tell the scorer
            exactly what to change.
            Schema:
            {"approved":true,
            "reason":"non-blank",
            "revisionInstructions":null}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelScoreReviewAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public ScoreReviewDecision review(
            InterviewSnapshot snapshot,
            InterviewAiContracts.ReportDraft draft) {
        String prompt = """
                Direction: %s
                Difficulty: %s
                Draft report:
                %s
                Transcript:
                %s
                """.formatted(
                snapshot.session().direction(),
                snapshot.session().difficulty(),
                draft,
                InterviewTranscriptRenderer.transcript(snapshot));
        return aiClient.call(SYSTEM_PROMPT, prompt, ScoreReviewDecision.class);
    }
}
