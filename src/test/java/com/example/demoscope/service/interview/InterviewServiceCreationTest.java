package com.example.demoscope.service.interview;

import com.example.demoscope.testsupport.interview.MutableInterviewRepository;
import com.example.demoscope.service.interview.InterviewService;
import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewAnswerEvaluator;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewQuestionGenerator;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewReportGenerator;
import com.example.demoscope.domain.interview.InterviewRepository;
import com.example.demoscope.domain.interview.InterviewServiceException;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;
import com.example.demoscope.common.llm.InterviewAiJsonClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewServiceCreationTest {

    private static final String USER_ID = "user-42";
    private static final Instant NOW = Instant.parse("2026-06-16T00:00:00Z");
    private static final UUID INTERVIEW_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000101");
    private static final UUID QUESTION_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000102");

    private MutableInterviewRepository repository;
    private InterviewQuestionGenerator generator;
    private InterviewService service;

    @BeforeEach
    void setUp() {
        repository = new MutableInterviewRepository();
        generator = mock(InterviewQuestionGenerator.class);
        service = new InterviewService(
                repository,
                generator,
                mock(InterviewAnswerEvaluator.class),
                mock(InterviewReportGenerator.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SequenceUuidSupplier(INTERVIEW_ID, QUESTION_ID),
                5,
                2);
    }

    @Test
    void createsPendingSessionGeneratesFirstQuestionAndReturnsInProgress() {
        when(generator.generate(any(), eq(1))).thenReturn(
                new InterviewAiContracts.GeneratedQuestion(
                        "Explain HashMap",
                        List.of("JAVA"),
                        List.of()));

        InterviewSnapshot result = service.createOrResume(
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE);

        assertEquals(InterviewSession.Status.IN_PROGRESS, result.session().status());
        assertEquals(1, result.session().mainQuestionCount());
        assertEquals(
                "Explain HashMap",
                result.currentQuestion().orElseThrow().text());
    }

    @Test
    void returnsExistingInProgressInterviewWithoutGeneratingAnotherQuestion() {
        repository.seed(inProgressSnapshot());

        InterviewSnapshot result = service.createOrResume(
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.SENIOR);

        assertEquals(
                InterviewSession.Difficulty.MIDDLE,
                result.session().difficulty());
        verifyNoInteractions(generator);
    }

    @Test
    void generationFailureLeavesPendingStateAndThrowsSafeUnavailableError() {
        when(generator.generate(any(), eq(1))).thenThrow(
                new InterviewAiJsonClient.InvalidOutputException("bad"));

        InterviewServiceException error = assertThrows(
                InterviewServiceException.class,
                () -> service.createOrResume(
                        USER_ID,
                        InterviewSession.Direction.JAVA_BACKEND,
                        InterviewSession.Difficulty.MIDDLE));

        assertEquals(
                InterviewServiceException.Kind.AI_UNAVAILABLE,
                error.kind());
        assertEquals(
                InterviewSession.Status.QUESTION_GENERATION_PENDING,
                error.snapshot().session().status());
        assertEquals(
                "interview AI is temporarily unavailable",
                error.getMessage());
    }

    @Test
    void optimisticConflictReloadsAndReturnsLatestSnapshot() {
        MutableInterviewRepository delegate = repository;
        InterviewRepository racingRepository = new DelegatingInterviewRepository(
                delegate) {
            @Override
            public boolean addMainQuestion(
                    UUID interviewId,
                    String userId,
                    long expectedVersion,
                    InterviewQuestion question,
                    Instant now) {
                delegate.addMainQuestion(
                        interviewId,
                        userId,
                        expectedVersion,
                        question,
                        now);
                return false;
            }
        };
        service = new InterviewService(
                racingRepository,
                generator,
                mock(InterviewAnswerEvaluator.class),
                mock(InterviewReportGenerator.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SequenceUuidSupplier(INTERVIEW_ID, QUESTION_ID),
                5,
                2);
        when(generator.generate(any(), eq(1))).thenReturn(
                new InterviewAiContracts.GeneratedQuestion(
                        "Winner question",
                        List.of("JAVA"),
                        List.of()));

        InterviewSnapshot result = service.createOrResume(
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE);

        assertEquals("Winner question", result.currentQuestion().orElseThrow().text());
        assertEquals(1, result.session().version());
    }

    @Test
    void constructorRejectsUnsupportedQuestionLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewService(
                        repository,
                        generator,
                        mock(InterviewAnswerEvaluator.class),
                        mock(InterviewReportGenerator.class),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        () -> UUID.randomUUID(),
                        4,
                        2));
    }

    private InterviewSnapshot inProgressSnapshot() {
        InterviewQuestion question = InterviewQuestion.main(
                QUESTION_ID,
                INTERVIEW_ID,
                1,
                "Explain HashMap",
                List.of("JAVA"),
                List.of(),
                NOW);
        InterviewSession session = new InterviewSession(
                INTERVIEW_ID,
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.IN_PROGRESS,
                1,
                QUESTION_ID,
                0,
                1,
                NOW,
                NOW,
                null);
        return new InterviewSnapshot(session, List.of(question), List.of(), null);
    }

    private static final class SequenceUuidSupplier
            implements java.util.function.Supplier<UUID> {

        private final List<UUID> values;
        private int index;

        private SequenceUuidSupplier(UUID... values) {
            this.values = List.of(values);
        }

        @Override
        public UUID get() {
            return values.get(index++);
        }
    }

    private abstract static class DelegatingInterviewRepository
            implements InterviewRepository {

        private final InterviewRepository delegate;

        private DelegatingInterviewRepository(InterviewRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.util.Optional<InterviewSnapshot> findActiveByUserId(
                String userId) {
            return delegate.findActiveByUserId(userId);
        }

        @Override
        public java.util.Optional<InterviewSnapshot> findByIdAndUserId(
                UUID interviewId,
                String userId) {
            return delegate.findByIdAndUserId(interviewId, userId);
        }

        @Override
        public InterviewSnapshot createPending(
                UUID interviewId,
                String userId,
                InterviewSession.Direction direction,
                InterviewSession.Difficulty difficulty,
                Instant now) {
            return delegate.createPending(
                    interviewId,
                    userId,
                    direction,
                    difficulty,
                    now);
        }

        @Override
        public boolean recordAnswerAndFollowUp(
                UUID interviewId,
                String userId,
                long expectedVersion,
                InterviewAnswer answer,
                InterviewQuestion followUp,
                Instant now) {
            return delegate.recordAnswerAndFollowUp(
                    interviewId,
                    userId,
                    expectedVersion,
                    answer,
                    followUp,
                    now);
        }

        @Override
        public boolean recordAnswerAndAwaitMainQuestion(
                UUID interviewId,
                String userId,
                long expectedVersion,
                InterviewAnswer answer,
                Instant now) {
            return delegate.recordAnswerAndAwaitMainQuestion(
                    interviewId,
                    userId,
                    expectedVersion,
                    answer,
                    now);
        }

        @Override
        public boolean recordAnswerAndAwaitScoring(
                UUID interviewId,
                String userId,
                long expectedVersion,
                InterviewAnswer answer,
                Instant now) {
            return delegate.recordAnswerAndAwaitScoring(
                    interviewId,
                    userId,
                    expectedVersion,
                    answer,
                    now);
        }

        @Override
        public boolean markScoringPending(
                UUID interviewId,
                String userId,
                long expectedVersion,
                Instant now) {
            return delegate.markScoringPending(
                    interviewId,
                    userId,
                    expectedVersion,
                    now);
        }

        @Override
        public boolean cancel(
                UUID interviewId,
                String userId,
                long expectedVersion,
                Instant now) {
            return delegate.cancel(
                    interviewId,
                    userId,
                    expectedVersion,
                    now);
        }

        @Override
        public boolean completeReport(
                UUID interviewId,
                String userId,
                long expectedVersion,
                InterviewReport report,
                Instant now) {
            return delegate.completeReport(
                    interviewId,
                    userId,
                    expectedVersion,
                    report,
                    now);
        }
    }
}
