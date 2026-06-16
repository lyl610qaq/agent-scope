package com.example.demoscope;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InterviewAnswer(
        UUID id,
        UUID interviewId,
        UUID questionId,
        String answerText,
        String internalEvaluation,
        List<String> abilityTags,
        Decision decision,
        String decisionReason,
        Instant createdAt) {

    public enum Decision {
        FOLLOW_UP,
        NEXT_MAIN_QUESTION
    }

    public InterviewAnswer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(interviewId, "interviewId");
        Objects.requireNonNull(questionId, "questionId");
        answerText = requireText(answerText, "answerText");
        internalEvaluation = requireText(
                internalEvaluation,
                "internalEvaluation");
        abilityTags = copyTextList(abilityTags, "abilityTags");
        Objects.requireNonNull(decision, "decision");
        decisionReason = requireText(decisionReason, "decisionReason");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static List<String> copyTextList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " must contain non-blank values");
        }
        return copy;
    }
}
