package com.example.demoscope.domain.interview;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InterviewQuestion(
        UUID id,
        UUID interviewId,
        Type type,
        int mainQuestionNumber,
        int followUpNumber,
        UUID parentQuestionId,
        String text,
        List<String> skillTags,
        List<String> evidenceIds,
        Status status,
        Instant createdAt,
        Instant answeredAt) {

    public enum Type {
        MAIN,
        FOLLOW_UP
    }

    public enum Status {
        WAITING_FOR_ANSWER,
        ANSWERED
    }

    public InterviewQuestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(interviewId, "interviewId");
        Objects.requireNonNull(type, "type");
        if (mainQuestionNumber < 1 || mainQuestionNumber > 5) {
            throw new IllegalArgumentException(
                    "mainQuestionNumber must be between 1 and 5");
        }
        if (type == Type.MAIN
                && (followUpNumber != 0 || parentQuestionId != null)) {
            throw new IllegalArgumentException(
                    "main questions must have followUpNumber 0 and no parent");
        }
        if (type == Type.FOLLOW_UP
                && (followUpNumber < 1
                || followUpNumber > 2
                || parentQuestionId == null)) {
            throw new IllegalArgumentException(
                    "follow-up questions require a parent and number between 1 and 2");
        }
        text = requireText(text, "text");
        skillTags = copyTextList(skillTags, "skillTags");
        evidenceIds = copyTextList(evidenceIds, "evidenceIds");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static InterviewQuestion main(
            UUID id,
            UUID interviewId,
            int mainQuestionNumber,
            String text,
            List<String> skillTags,
            List<String> evidenceIds,
            Instant createdAt) {
        return new InterviewQuestion(
                id,
                interviewId,
                Type.MAIN,
                mainQuestionNumber,
                0,
                null,
                text,
                skillTags,
                evidenceIds,
                Status.WAITING_FOR_ANSWER,
                createdAt,
                null);
    }

    public static InterviewQuestion followUp(
            UUID id,
            UUID interviewId,
            int mainQuestionNumber,
            int followUpNumber,
            UUID parentQuestionId,
            String text,
            List<String> skillTags,
            List<String> evidenceIds,
            Instant createdAt) {
        return new InterviewQuestion(
                id,
                interviewId,
                Type.FOLLOW_UP,
                mainQuestionNumber,
                followUpNumber,
                parentQuestionId,
                text,
                skillTags,
                evidenceIds,
                Status.WAITING_FOR_ANSWER,
                createdAt,
                null);
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
