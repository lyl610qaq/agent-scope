package com.example.demoscope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InterviewSession(
        UUID id,
        String userId,
        Direction direction,
        Difficulty difficulty,
        Status status,
        int mainQuestionCount,
        UUID currentQuestionId,
        int answeredQuestionCount,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public enum Direction {
        JAVA_BACKEND
    }

    public enum Difficulty {
        JUNIOR,
        MIDDLE,
        SENIOR
    }

    public enum Status {
        QUESTION_GENERATION_PENDING,
        IN_PROGRESS,
        SCORING_PENDING,
        COMPLETED,
        CANCELLED
    }

    public InterviewSession {
        Objects.requireNonNull(id, "id");
        userId = requireText(userId, "userId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(status, "status");
        if (mainQuestionCount < 0 || mainQuestionCount > 5) {
            throw new IllegalArgumentException(
                    "mainQuestionCount must be between 0 and 5");
        }
        if (answeredQuestionCount < 0 || version < 0) {
            throw new IllegalArgumentException(
                    "counts and version must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean unfinished() {
        return status != Status.COMPLETED && status != Status.CANCELLED;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
