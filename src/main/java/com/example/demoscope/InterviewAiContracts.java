package com.example.demoscope;

import java.util.List;
import java.util.Objects;

public final class InterviewAiContracts {

    private InterviewAiContracts() {
    }

    public record GeneratedQuestion(
            String question,
            List<String> skillTags,
            List<String> evidenceIds) {

        public GeneratedQuestion {
            question = requireText(question, "question");
            skillTags = copyTextList(skillTags, "skillTags");
            evidenceIds = copyTextList(evidenceIds, "evidenceIds");
        }
    }

    public record AnswerEvaluation(
            String internalEvaluation,
            List<String> abilityTags,
            InterviewAnswer.Decision decision,
            String followUpQuestion,
            String decisionReason) {

        public AnswerEvaluation {
            internalEvaluation = requireText(
                    internalEvaluation,
                    "internalEvaluation");
            abilityTags = copyTextList(abilityTags, "abilityTags");
            Objects.requireNonNull(decision, "decision");
            decisionReason = requireText(decisionReason, "decisionReason");
            if (decision == InterviewAnswer.Decision.FOLLOW_UP) {
                followUpQuestion = requireText(
                        followUpQuestion,
                        "followUpQuestion");
            } else if (followUpQuestion != null && !followUpQuestion.isBlank()) {
                throw new IllegalArgumentException(
                        "followUpQuestion must be blank for NEXT_MAIN_QUESTION");
            } else {
                followUpQuestion = null;
            }
        }
    }

    public record ReportDraft(
            int overallScore,
            ScoreBreakdown scores,
            List<String> strengths,
            List<String> weaknesses,
            List<String> improvementSuggestions) {

        public ReportDraft {
            InterviewReport.validateScore(overallScore, "overallScore");
            Objects.requireNonNull(scores, "scores");
            strengths = InterviewReport.requireFeedback(
                    strengths,
                    "strengths");
            weaknesses = InterviewReport.requireFeedback(
                    weaknesses,
                    "weaknesses");
            improvementSuggestions = InterviewReport.requireFeedback(
                    improvementSuggestions,
                    "improvementSuggestions");
        }
    }

    public record ScoreBreakdown(
            int javaFundamentals,
            int concurrency,
            int jvm,
            int spring,
            int database,
            int engineering) {

        public ScoreBreakdown {
            InterviewReport.validateScore(
                    javaFundamentals,
                    "javaFundamentals");
            InterviewReport.validateScore(concurrency, "concurrency");
            InterviewReport.validateScore(jvm, "jvm");
            InterviewReport.validateScore(spring, "spring");
            InterviewReport.validateScore(database, "database");
            InterviewReport.validateScore(engineering, "engineering");
        }
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
            throw new IllegalArgumentException(
                    name + " must contain non-blank values");
        }
        return copy;
    }
}
