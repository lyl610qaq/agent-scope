package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewReportGenerator;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;
import java.util.Objects;

public class ScoreReviewBiz implements InterviewReportGenerator {

    private final ScoreDraftGenerator draftGenerator;
    private final ScoreReviewAgent reviewAgent;
    private final int maxAttempts;

    public ScoreReviewBiz(
            ScoreDraftGenerator draftGenerator,
            ScoreReviewAgent reviewAgent,
            int maxAttempts) {
        this.draftGenerator = Objects.requireNonNull(
                draftGenerator,
                "draftGenerator");
        this.reviewAgent = Objects.requireNonNull(reviewAgent, "reviewAgent");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public InterviewAiContracts.ReportDraft generate(InterviewSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        String reviewFeedback = null;
        ScoreReviewDecision lastDecision = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            InterviewAiContracts.ReportDraft draft = Objects.requireNonNull(
                    draftGenerator.generate(snapshot, reviewFeedback),
                    "score draft generator returned null");
            ScoreReviewDecision decision = Objects.requireNonNull(
                    reviewAgent.review(snapshot, draft),
                    "score review agent returned null");
            if (decision.approved()) {
                return draft;
            }
            lastDecision = decision;
            reviewFeedback = decision.revisionInstructions();
        }

        throw new ScoreReviewFailedException(lastDecision == null
                ? "Score review failed."
                : lastDecision.reason());
    }

    public static class ScoreReviewFailedException extends RuntimeException {

        public ScoreReviewFailedException(String message) {
            super(message);
        }
    }
}
