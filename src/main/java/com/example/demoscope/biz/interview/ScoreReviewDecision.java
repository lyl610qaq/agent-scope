package com.example.demoscope.biz.interview;

import java.util.Objects;

public record ScoreReviewDecision(
        Boolean approved,
        String reason,
        String revisionInstructions) {

    public ScoreReviewDecision {
        Objects.requireNonNull(approved, "approved");
        reason = RouterDecision.requireText(reason, "reason");
        if (approved) {
            revisionInstructions = normalizeOptional(revisionInstructions);
        } else {
            revisionInstructions = RouterDecision.requireText(
                    revisionInstructions,
                    "revisionInstructions");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
