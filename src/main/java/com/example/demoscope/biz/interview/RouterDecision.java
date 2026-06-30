package com.example.demoscope.biz.interview;

import java.util.List;
import java.util.Objects;

public record RouterDecision(
        InterviewAgentName nextAgent,
        String reason,
        double confidence,
        String suggestedFocus,
        List<String> usedEvidenceIds) {

    public RouterDecision {
        Objects.requireNonNull(nextAgent, "nextAgent");
        reason = requireText(reason, "reason");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be between 0.0 and 1.0");
        }
        suggestedFocus = requireText(suggestedFocus, "suggestedFocus");
        usedEvidenceIds = copyTextList(usedEvidenceIds, "usedEvidenceIds");
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static List<String> copyTextList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    name + " must contain non-blank values");
        }
        return copy;
    }
}
