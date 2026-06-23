package com.example.demoscope.interview.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InterviewReport(
        UUID interviewId,
        int overallScore,
        int javaFundamentalsScore,
        int concurrencyScore,
        int jvmScore,
        int springScore,
        int databaseScore,
        int engineeringScore,
        List<String> strengths,
        List<String> weaknesses,
        List<String> improvementSuggestions,
        Instant createdAt) {

    public InterviewReport {
        Objects.requireNonNull(interviewId, "interviewId");
        validateScore(overallScore, "overallScore");
        validateScore(javaFundamentalsScore, "javaFundamentalsScore");
        validateScore(concurrencyScore, "concurrencyScore");
        validateScore(jvmScore, "jvmScore");
        validateScore(springScore, "springScore");
        validateScore(databaseScore, "databaseScore");
        validateScore(engineeringScore, "engineeringScore");
        strengths = requireFeedback(strengths, "strengths");
        weaknesses = requireFeedback(weaknesses, "weaknesses");
        improvementSuggestions = requireFeedback(
                improvementSuggestions,
                "improvementSuggestions");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static void validateScore(int score, String name) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and 100");
        }
    }

    public static List<String> requireFeedback(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        if (copy.isEmpty()
                || copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    name + " must contain non-blank values");
        }
        return copy;
    }
}
