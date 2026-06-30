package com.example.demoscope.domain.interview;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InterviewSnapshot(
        InterviewSession session,
        List<InterviewQuestion> questions,
        List<InterviewAnswer> answers,
        InterviewReport report) {

    public InterviewSnapshot {
        Objects.requireNonNull(session, "session");
        questions = List.copyOf(Objects.requireNonNull(questions, "questions"));
        answers = List.copyOf(Objects.requireNonNull(answers, "answers"));
    }

    public Optional<InterviewQuestion> currentQuestion() {
        UUID currentId = session.currentQuestionId();
        return currentId == null
                ? Optional.empty()
                : questions.stream()
                        .filter(question -> question.id().equals(currentId))
                        .findFirst();
    }

    public Optional<InterviewAnswer> answerFor(UUID questionId) {
        return answers.stream()
                .filter(answer -> answer.questionId().equals(questionId))
                .findFirst();
    }
}
