package com.example.demoscope;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;

public final class InterviewService {

    private static final String UNSUPPORTED_CONFIGURATION =
            "unsupported interview configuration";
    private static final String INTERVIEW_CONFLICT =
            "interview state conflict";
    private static final String INTERVIEW_NOT_FOUND =
            "interview not found";
    private static final String AI_UNAVAILABLE =
            "interview AI is temporarily unavailable";

    private final InterviewRepository repository;
    private final InterviewQuestionGenerator questionGenerator;
    private final InterviewAnswerEvaluator answerEvaluator;
    private final InterviewReportGenerator reportGenerator;
    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private final int maxMainQuestions;
    private final int maxFollowUps;

    public InterviewService(
            InterviewRepository repository,
            InterviewQuestionGenerator questionGenerator,
            InterviewAnswerEvaluator answerEvaluator,
            InterviewReportGenerator reportGenerator,
            Clock clock,
            Supplier<UUID> idSupplier,
            int maxMainQuestions,
            int maxFollowUps) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.questionGenerator = Objects.requireNonNull(
                questionGenerator,
                "questionGenerator");
        this.answerEvaluator = Objects.requireNonNull(
                answerEvaluator,
                "answerEvaluator");
        this.reportGenerator = Objects.requireNonNull(
                reportGenerator,
                "reportGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
        if (maxMainQuestions != 5 || maxFollowUps != 2) {
            throw new IllegalArgumentException(UNSUPPORTED_CONFIGURATION);
        }
        this.maxMainQuestions = maxMainQuestions;
        this.maxFollowUps = maxFollowUps;
    }

    public InterviewSnapshot createOrResume(
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty) {
        validateConfiguration(userId, direction, difficulty);
        InterviewSnapshot snapshot = repository.findActiveByUserId(userId)
                .orElseGet(() -> createPending(
                        userId,
                        direction,
                        difficulty));
        if (snapshot.session().status()
                != InterviewSession.Status.QUESTION_GENERATION_PENDING) {
            return snapshot;
        }

        int mainQuestionNumber = snapshot.session().mainQuestionCount() + 1;
        if (mainQuestionNumber > maxMainQuestions) {
            throw conflict(snapshot);
        }

        InterviewAiContracts.GeneratedQuestion generated;
        try {
            generated = questionGenerator.generate(
                    snapshot,
                    mainQuestionNumber);
        } catch (RuntimeException exception) {
            InterviewSnapshot latest = reload(snapshot);
            throw new InterviewServiceException(
                    InterviewServiceException.Kind.AI_UNAVAILABLE,
                    AI_UNAVAILABLE,
                    latest,
                    exception);
        }

        Instant now = clock.instant();
        InterviewQuestion question = InterviewQuestion.main(
                idSupplier.get(),
                snapshot.session().id(),
                mainQuestionNumber,
                generated.question(),
                generated.skillTags(),
                generated.evidenceIds(),
                now);
        boolean added = repository.addMainQuestion(
                snapshot.session().id(),
                userId,
                snapshot.session().version(),
                question,
                now);
        if (!added) {
            return reloadOrConflict(snapshot);
        }
        return reloadOrConflict(snapshot);
    }

    public InterviewSnapshot current(String userId) {
        validateUserId(userId);
        return repository.findActiveByUserId(userId)
                .orElseThrow(this::notFound);
    }

    public InterviewSnapshot get(String userId, UUID interviewId) {
        validateUserId(userId);
        if (interviewId == null) {
            throw new InterviewServiceException(
                    InterviewServiceException.Kind.BAD_REQUEST,
                    UNSUPPORTED_CONFIGURATION,
                    null);
        }
        return repository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(this::notFound);
    }

    public InterviewSnapshot answer(
            String userId,
            UUID interviewId,
            UUID questionId,
            String answerText) {
        if (userId == null
                || userId.isBlank()
                || interviewId == null
                || questionId == null
                || answerText == null
                || answerText.isBlank()) {
            throw new InterviewServiceException(
                    InterviewServiceException.Kind.BAD_REQUEST,
                    UNSUPPORTED_CONFIGURATION,
                    null);
        }
        InterviewSnapshot snapshot = repository.findByIdAndUserId(
                        interviewId,
                        userId)
                .orElseThrow(this::notFound);
        if (snapshot.answerFor(questionId).isPresent()) {
            return snapshot;
        }
        InterviewQuestion question = validateCurrentQuestion(
                snapshot,
                questionId);

        InterviewAiContracts.AnswerEvaluation evaluation;
        try {
            evaluation = answerEvaluator.evaluate(
                    snapshot,
                    question,
                    answerText);
        } catch (RuntimeException exception) {
            throw new InterviewServiceException(
                    InterviewServiceException.Kind.AI_UNAVAILABLE,
                    AI_UNAVAILABLE,
                    reload(snapshot),
                    exception);
        }

        Instant now = clock.instant();
        InterviewAnswer answer = new InterviewAnswer(
                idSupplier.get(),
                interviewId,
                questionId,
                answerText,
                evaluation.internalEvaluation(),
                evaluation.abilityTags(),
                evaluation.decision(),
                evaluation.decisionReason(),
                now);
        if (evaluation.decision() == InterviewAnswer.Decision.FOLLOW_UP
                && question.followUpNumber() < maxFollowUps) {
            InterviewQuestion followUp = InterviewQuestion.followUp(
                    idSupplier.get(),
                    interviewId,
                    question.mainQuestionNumber(),
                    question.followUpNumber() + 1,
                    question.id(),
                    evaluation.followUpQuestion(),
                    evaluation.abilityTags(),
                    question.evidenceIds(),
                    now);
            boolean changed = repository.recordAnswerAndFollowUp(
                    interviewId,
                    userId,
                    snapshot.session().version(),
                    answer,
                    followUp,
                    now);
            return reloadOrConflict(snapshot);
        }

        if (question.mainQuestionNumber() < maxMainQuestions) {
            boolean changed = repository.recordAnswerAndAwaitMainQuestion(
                    interviewId,
                    userId,
                    snapshot.session().version(),
                    answer,
                    now);
            if (!changed) {
                return reloadOrConflict(snapshot);
            }
            return createOrResume(
                    userId,
                    snapshot.session().direction(),
                    snapshot.session().difficulty());
        }

        boolean changed = repository.recordAnswerAndAwaitScoring(
                interviewId,
                userId,
                snapshot.session().version(),
                answer,
                now);
        if (!changed) {
            return reloadOrConflict(snapshot);
        }
        return finish(userId, interviewId);
    }

    public InterviewSnapshot finish(String userId, UUID interviewId) {
        if (userId == null || userId.isBlank() || interviewId == null) {
            throw new InterviewServiceException(
                    InterviewServiceException.Kind.BAD_REQUEST,
                    UNSUPPORTED_CONFIGURATION,
                    null);
        }
        InterviewSnapshot snapshot = repository.findByIdAndUserId(
                        interviewId,
                        userId)
                .orElseThrow(this::notFound);
        InterviewSession.Status status = snapshot.session().status();
        if (status == InterviewSession.Status.COMPLETED
                || status == InterviewSession.Status.CANCELLED) {
            return snapshot;
        }

        if (snapshot.answers().isEmpty()) {
            boolean cancelled = repository.cancel(
                    interviewId,
                    userId,
                    snapshot.session().version(),
                    clock.instant());
            return cancelled
                    ? reloadOrConflict(snapshot)
                    : reloadOrConflict(snapshot);
        }

        if (status == InterviewSession.Status.IN_PROGRESS
                || status
                == InterviewSession.Status.QUESTION_GENERATION_PENDING) {
            boolean marked = repository.markScoringPending(
                    interviewId,
                    userId,
                    snapshot.session().version(),
                    clock.instant());
            snapshot = reloadOrConflict(snapshot);
            if (!marked) {
                return snapshot;
            }
        }
        if (snapshot.session().status()
                != InterviewSession.Status.SCORING_PENDING) {
            throw conflict(snapshot);
        }

        InterviewReport report;
        try {
            InterviewAiContracts.ReportDraft draft = Objects.requireNonNull(
                    reportGenerator.generate(snapshot),
                    "reportGenerator returned null");
            report = toReport(interviewId, draft, clock.instant());
        } catch (RuntimeException exception) {
            return reload(snapshot);
        }
        Instant now = clock.instant();
        repository.completeReport(
                interviewId,
                userId,
                snapshot.session().version(),
                report,
                now);
        return reloadOrConflict(snapshot);
    }

    private InterviewSnapshot createPending(
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty) {
        try {
            return repository.createPending(
                    idSupplier.get(),
                    userId,
                    direction,
                    difficulty,
                    clock.instant());
        } catch (DuplicateKeyException exception) {
            return repository.findActiveByUserId(userId)
                    .orElseThrow(() -> new InterviewServiceException(
                            InterviewServiceException.Kind.CONFLICT,
                            INTERVIEW_CONFLICT,
                            null,
                            exception));
        }
    }

    private void validateConfiguration(
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty) {
        validateUserId(userId);
        if (direction != InterviewSession.Direction.JAVA_BACKEND
                || difficulty == null) {
            throw new InterviewServiceException(
                    InterviewServiceException.Kind.BAD_REQUEST,
                    UNSUPPORTED_CONFIGURATION,
                    null);
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InterviewServiceException(
                    InterviewServiceException.Kind.BAD_REQUEST,
                    UNSUPPORTED_CONFIGURATION,
                    null);
        }
    }

    private InterviewQuestion validateCurrentQuestion(
            InterviewSnapshot snapshot,
            UUID questionId) {
        if (snapshot.session().status() != InterviewSession.Status.IN_PROGRESS
                || !Objects.equals(
                        snapshot.session().currentQuestionId(),
                        questionId)) {
            throw conflict(snapshot);
        }
        InterviewQuestion question = snapshot.currentQuestion()
                .orElseThrow(() -> conflict(snapshot));
        if (question.status()
                != InterviewQuestion.Status.WAITING_FOR_ANSWER) {
            throw conflict(snapshot);
        }
        return question;
    }

    private InterviewReport toReport(
            UUID interviewId,
            InterviewAiContracts.ReportDraft draft,
            Instant now) {
        InterviewAiContracts.ScoreBreakdown scores = draft.scores();
        return new InterviewReport(
                interviewId,
                draft.overallScore(),
                scores.javaFundamentals(),
                scores.concurrency(),
                scores.jvm(),
                scores.spring(),
                scores.database(),
                scores.engineering(),
                draft.strengths(),
                draft.weaknesses(),
                draft.improvementSuggestions(),
                now);
    }

    private InterviewSnapshot reload(InterviewSnapshot fallback) {
        return repository.findByIdAndUserId(
                        fallback.session().id(),
                        fallback.session().userId())
                .orElse(fallback);
    }

    private InterviewSnapshot reloadOrConflict(InterviewSnapshot snapshot) {
        return repository.findByIdAndUserId(
                        snapshot.session().id(),
                        snapshot.session().userId())
                .orElseThrow(() -> conflict(snapshot));
    }

    private InterviewServiceException conflict(InterviewSnapshot snapshot) {
        return new InterviewServiceException(
                InterviewServiceException.Kind.CONFLICT,
                INTERVIEW_CONFLICT,
                snapshot);
    }

    private InterviewServiceException notFound() {
        return new InterviewServiceException(
                InterviewServiceException.Kind.NOT_FOUND,
                INTERVIEW_NOT_FOUND,
                null);
    }
}
