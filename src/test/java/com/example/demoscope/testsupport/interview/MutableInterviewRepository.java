package com.example.demoscope.testsupport.interview;

import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewRepository;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MutableInterviewRepository implements InterviewRepository {

    private final Map<UUID, InterviewSnapshot> snapshots =
            new LinkedHashMap<>();
    private boolean failNextMutation;
    private InterviewSnapshot winnerOnNextMutation;

    public void seed(InterviewSnapshot snapshot) {
        snapshots.put(snapshot.session().id(), snapshot);
    }

    public void failNextMutation() {
        failNextMutation = true;
    }

    public void winnerOnNextMutation(InterviewSnapshot winner) {
        winnerOnNextMutation = winner;
    }

    @Override
    public Optional<InterviewSnapshot> findActiveByUserId(String userId) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.session().userId().equals(userId))
                .filter(snapshot -> snapshot.session().unfinished())
                .findFirst();
    }

    @Override
    public Optional<InterviewSnapshot> findByIdAndUserId(
            UUID interviewId,
            String userId) {
        return Optional.ofNullable(snapshots.get(interviewId))
                .filter(snapshot -> snapshot.session().userId().equals(userId));
    }

    @Override
    public InterviewSnapshot createPending(
            UUID interviewId,
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty,
            Instant now) {
        InterviewSnapshot snapshot = new InterviewSnapshot(
                new InterviewSession(
                        interviewId,
                        userId,
                        direction,
                        difficulty,
                        InterviewSession.Status.QUESTION_GENERATION_PENDING,
                        0,
                        null,
                        0,
                        0,
                        now,
                        now,
                        null),
                List.of(),
                List.of(),
                null);
        seed(snapshot);
        return snapshot;
    }

    @Override
    public boolean addMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewQuestion question,
            Instant now) {
        return mutate(
                interviewId,
                userId,
                expectedVersion,
                snapshot -> {
                    InterviewSession session = snapshot.session();
                    InterviewSession updatedSession = session(
                            session,
                            InterviewSession.Status.IN_PROGRESS,
                            session.mainQuestionCount() + 1,
                            question.id(),
                            session.answeredQuestionCount(),
                            now,
                            null);
                    List<InterviewQuestion> questions =
                            new ArrayList<>(snapshot.questions());
                    questions.add(question);
                    return new InterviewSnapshot(
                            updatedSession,
                            questions,
                            snapshot.answers(),
                            snapshot.report());
                });
    }

    @Override
    public boolean recordAnswerAndFollowUp(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            InterviewQuestion followUp,
            Instant now) {
        return mutate(
                interviewId,
                userId,
                expectedVersion,
                snapshot -> answered(
                        snapshot,
                        answer,
                        InterviewSession.Status.IN_PROGRESS,
                        followUp.id(),
                        followUp,
                        now));
    }

    @Override
    public boolean recordAnswerAndAwaitMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now) {
        return mutate(
                interviewId,
                userId,
                expectedVersion,
                snapshot -> answered(
                        snapshot,
                        answer,
                        InterviewSession.Status.QUESTION_GENERATION_PENDING,
                        null,
                        null,
                        now));
    }

    @Override
    public boolean recordAnswerAndAwaitScoring(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now) {
        return mutate(
                interviewId,
                userId,
                expectedVersion,
                snapshot -> answered(
                        snapshot,
                        answer,
                        InterviewSession.Status.SCORING_PENDING,
                        null,
                        null,
                        now));
    }

    @Override
    public boolean markScoringPending(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now) {
        return changeStatus(
                interviewId,
                userId,
                expectedVersion,
                InterviewSession.Status.SCORING_PENDING,
                now,
                null);
    }

    @Override
    public boolean cancel(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now) {
        return changeStatus(
                interviewId,
                userId,
                expectedVersion,
                InterviewSession.Status.CANCELLED,
                now,
                now);
    }

    @Override
    public boolean completeReport(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewReport report,
            Instant now) {
        return mutate(
                interviewId,
                userId,
                expectedVersion,
                snapshot -> new InterviewSnapshot(
                        session(
                                snapshot.session(),
                                InterviewSession.Status.COMPLETED,
                                snapshot.session().mainQuestionCount(),
                                null,
                                snapshot.session().answeredQuestionCount(),
                                now,
                                now),
                        snapshot.questions(),
                        snapshot.answers(),
                        report));
    }

    private boolean changeStatus(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewSession.Status status,
            Instant now,
            Instant completedAt) {
        return mutate(
                interviewId,
                userId,
                expectedVersion,
                snapshot -> new InterviewSnapshot(
                        session(
                                snapshot.session(),
                                status,
                                snapshot.session().mainQuestionCount(),
                                null,
                                snapshot.session().answeredQuestionCount(),
                                now,
                                completedAt),
                        snapshot.questions(),
                        snapshot.answers(),
                        snapshot.report()));
    }

    private InterviewSnapshot answered(
            InterviewSnapshot snapshot,
            InterviewAnswer answer,
            InterviewSession.Status status,
            UUID currentQuestionId,
            InterviewQuestion followUp,
            Instant now) {
        List<InterviewQuestion> questions = new ArrayList<>();
        for (InterviewQuestion question : snapshot.questions()) {
            if (question.id().equals(answer.questionId())) {
                questions.add(new InterviewQuestion(
                        question.id(),
                        question.interviewId(),
                        question.type(),
                        question.mainQuestionNumber(),
                        question.followUpNumber(),
                        question.parentQuestionId(),
                        question.text(),
                        question.skillTags(),
                        question.evidenceIds(),
                        InterviewQuestion.Status.ANSWERED,
                        question.createdAt(),
                        now));
            } else {
                questions.add(question);
            }
        }
        if (followUp != null) {
            questions.add(followUp);
        }
        List<InterviewAnswer> answers = new ArrayList<>(snapshot.answers());
        answers.add(answer);
        InterviewSession session = snapshot.session();
        return new InterviewSnapshot(
                session(
                        session,
                        status,
                        session.mainQuestionCount(),
                        currentQuestionId,
                        session.answeredQuestionCount() + 1,
                        now,
                        null),
                questions,
                answers,
                snapshot.report());
    }

    private boolean mutate(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Mutation mutation) {
        InterviewSnapshot current = snapshots.get(interviewId);
        if (winnerOnNextMutation != null) {
            snapshots.put(interviewId, winnerOnNextMutation);
            winnerOnNextMutation = null;
            return false;
        }
        if (failNextMutation) {
            failNextMutation = false;
            return false;
        }
        if (current == null
                || !current.session().userId().equals(userId)
                || current.session().version() != expectedVersion) {
            return false;
        }
        snapshots.put(interviewId, mutation.apply(current));
        return true;
    }

    private InterviewSession session(
            InterviewSession current,
            InterviewSession.Status status,
            int mainQuestionCount,
            UUID currentQuestionId,
            int answeredQuestionCount,
            Instant now,
            Instant completedAt) {
        return new InterviewSession(
                current.id(),
                current.userId(),
                current.direction(),
                current.difficulty(),
                status,
                mainQuestionCount,
                currentQuestionId,
                answeredQuestionCount,
                current.version() + 1,
                current.createdAt(),
                now,
                completedAt);
    }

    @FunctionalInterface
    private interface Mutation {
        InterviewSnapshot apply(InterviewSnapshot snapshot);
    }
}
