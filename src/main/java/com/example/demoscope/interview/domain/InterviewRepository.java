package com.example.demoscope.interview.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository {

    Optional<InterviewSnapshot> findActiveByUserId(String userId);

    Optional<InterviewSnapshot> findByIdAndUserId(
            UUID interviewId,
            String userId);

    InterviewSnapshot createPending(
            UUID interviewId,
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty,
            Instant now);

    boolean addMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewQuestion question,
            Instant now);

    boolean recordAnswerAndFollowUp(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            InterviewQuestion followUp,
            Instant now);

    boolean recordAnswerAndAwaitMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now);

    boolean recordAnswerAndAwaitScoring(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now);

    boolean markScoringPending(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now);

    boolean cancel(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now);

    boolean completeReport(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewReport report,
            Instant now);
}
