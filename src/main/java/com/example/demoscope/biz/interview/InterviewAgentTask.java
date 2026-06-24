package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import java.util.Objects;
import java.util.Set;

public record InterviewAgentTask(
        Type type,
        Set<InterviewAgentName> allowedAgents,
        InterviewAgentName defaultAgent,
        int mainQuestionNumber,
        InterviewQuestion currentQuestion,
        String candidateAnswer) {

    public enum Type {
        GENERATE_MAIN_QUESTION,
        EVALUATE_ANSWER,
        GENERATE_REPORT
    }

    public InterviewAgentTask {
        Objects.requireNonNull(type, "type");
        allowedAgents = Set.copyOf(
                Objects.requireNonNull(allowedAgents, "allowedAgents"));
        if (allowedAgents.isEmpty()) {
            throw new IllegalArgumentException("allowedAgents must not be empty");
        }
        Objects.requireNonNull(defaultAgent, "defaultAgent");
        if (!allowedAgents.contains(defaultAgent)) {
            throw new IllegalArgumentException(
                    "defaultAgent must be in allowedAgents");
        }
        if (mainQuestionNumber < 0 || mainQuestionNumber > 5) {
            throw new IllegalArgumentException(
                    "mainQuestionNumber must be between 0 and 5");
        }
        if (type == Type.EVALUATE_ANSWER) {
            Objects.requireNonNull(currentQuestion, "currentQuestion");
            candidateAnswer = RouterDecision.requireText(
                    candidateAnswer,
                    "candidateAnswer");
        }
    }

    public static InterviewAgentTask generateMainQuestion(
            InterviewSnapshot snapshot,
            int mainQuestionNumber) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new InterviewAgentTask(
                Type.GENERATE_MAIN_QUESTION,
                Set.of(
                        InterviewAgentName.INTERVIEWER,
                        InterviewAgentName.PROJECT,
                        InterviewAgentName.JAVA_SKILL),
                InterviewAgentName.JAVA_SKILL,
                mainQuestionNumber,
                null,
                null);
    }

    public static InterviewAgentTask evaluateAnswer(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(question, "question");
        return new InterviewAgentTask(
                Type.EVALUATE_ANSWER,
                Set.of(
                        InterviewAgentName.INTERVIEWER,
                        InterviewAgentName.PROJECT,
                        InterviewAgentName.JAVA_SKILL),
                InterviewAgentName.INTERVIEWER,
                question.mainQuestionNumber(),
                question,
                candidateAnswer);
    }

    public static InterviewAgentTask generateReport(InterviewSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new InterviewAgentTask(
                Type.GENERATE_REPORT,
                Set.of(InterviewAgentName.SCORE),
                InterviewAgentName.SCORE,
                snapshot.session().mainQuestionCount(),
                null,
                null);
    }
}
