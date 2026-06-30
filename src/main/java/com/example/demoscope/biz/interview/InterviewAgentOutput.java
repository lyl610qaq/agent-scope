package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewAiContracts;
import java.util.List;
import java.util.Objects;

public record InterviewAgentOutput(
        InterviewAgentName agentName,
        Type type,
        InterviewAiContracts.GeneratedQuestion generatedQuestion,
        InterviewAiContracts.AnswerEvaluation answerEvaluation,
        InterviewAiContracts.ReportDraft reportDraft,
        List<String> usedEvidenceIds) {

    public enum Type {
        QUESTION,
        ANSWER_EVALUATION,
        SCORE_REPORT
    }

    public InterviewAgentOutput {
        Objects.requireNonNull(agentName, "agentName");
        Objects.requireNonNull(type, "type");
        usedEvidenceIds = RouterDecision.copyTextList(
                usedEvidenceIds,
                "usedEvidenceIds");
        int payloadCount = (generatedQuestion == null ? 0 : 1)
                + (answerEvaluation == null ? 0 : 1)
                + (reportDraft == null ? 0 : 1);
        if (payloadCount != 1) {
            throw new IllegalArgumentException(
                    "exactly one output payload is required");
        }
        if (type == Type.QUESTION && generatedQuestion == null) {
            throw new IllegalArgumentException(
                    "generatedQuestion is required for QUESTION");
        }
        if (type == Type.ANSWER_EVALUATION && answerEvaluation == null) {
            throw new IllegalArgumentException(
                    "answerEvaluation is required for ANSWER_EVALUATION");
        }
        if (type == Type.SCORE_REPORT && reportDraft == null) {
            throw new IllegalArgumentException(
                    "reportDraft is required for SCORE_REPORT");
        }
    }

    public static InterviewAgentOutput question(
            InterviewAgentName agentName,
            InterviewAiContracts.GeneratedQuestion question,
            List<String> usedEvidenceIds) {
        return new InterviewAgentOutput(
                agentName,
                Type.QUESTION,
                Objects.requireNonNull(question, "question"),
                null,
                null,
                usedEvidenceIds);
    }

    public static InterviewAgentOutput answerEvaluation(
            InterviewAgentName agentName,
            InterviewAiContracts.AnswerEvaluation evaluation,
            List<String> usedEvidenceIds) {
        return new InterviewAgentOutput(
                agentName,
                Type.ANSWER_EVALUATION,
                null,
                Objects.requireNonNull(evaluation, "evaluation"),
                null,
                usedEvidenceIds);
    }

    public static InterviewAgentOutput report(
            InterviewAgentName agentName,
            InterviewAiContracts.ReportDraft report,
            List<String> usedEvidenceIds) {
        return new InterviewAgentOutput(
                agentName,
                Type.SCORE_REPORT,
                null,
                null,
                Objects.requireNonNull(report, "report"),
                usedEvidenceIds);
    }
}
