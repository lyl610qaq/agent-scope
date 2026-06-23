package com.example.demoscope.interview.application;

import com.example.demoscope.interview.support.MutableInterviewRepository;
import com.example.demoscope.interview.application.InterviewService;
import com.example.demoscope.interview.domain.InterviewAnswer;
import com.example.demoscope.interview.domain.InterviewAnswerEvaluator;
import com.example.demoscope.interview.domain.InterviewQuestion;
import com.example.demoscope.interview.domain.InterviewQuestionGenerator;
import com.example.demoscope.interview.domain.InterviewReportGenerator;
import com.example.demoscope.interview.domain.InterviewServiceException;
import com.example.demoscope.interview.domain.InterviewSession;
import com.example.demoscope.interview.domain.InterviewSnapshot;
import com.example.demoscope.interview.infrastructure.InterviewAiContracts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewServiceAnswerTest {

    private static final String USER_ID = "user-42";
    private static final UUID INTERVIEW_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000201");
    private static final Instant NOW = Instant.parse("2026-06-16T01:00:00Z");

    private MutableInterviewRepository repository;
    private InterviewQuestionGenerator questionGenerator;
    private InterviewAnswerEvaluator evaluator;
    private InterviewService service;

    @BeforeEach
    void setUp() {
        repository = new MutableInterviewRepository();
        questionGenerator = mock(InterviewQuestionGenerator.class);
        evaluator = mock(InterviewAnswerEvaluator.class);
        AtomicLong ids = new AtomicLong(1000);
        service = new InterviewService(
                repository,
                questionGenerator,
                evaluator,
                mock(InterviewReportGenerator.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> new UUID(0, ids.getAndIncrement()),
                5,
                2);
    }

    @Test
    void followUpAfterMainQuestionCreatesFollowUpOne() {
        InterviewSnapshot initial = waitingAt(1, 0);
        repository.seed(initial);
        when(evaluator.evaluate(
                any(),
                eq(initial.currentQuestion().orElseThrow()),
                eq("candidate answer")))
                .thenReturn(followUp("Why powers of two?"));

        InterviewSnapshot result = service.answer(
                USER_ID,
                INTERVIEW_ID,
                initial.session().currentQuestionId(),
                "candidate answer");

        InterviewQuestion current = result.currentQuestion().orElseThrow();
        assertEquals(InterviewQuestion.Type.FOLLOW_UP, current.type());
        assertEquals(1, current.followUpNumber());
        assertEquals(1, result.answers().size());
    }

    @Test
    void followUpAfterFollowUpOneCreatesFollowUpTwo() {
        InterviewSnapshot initial = waitingAt(1, 1);
        repository.seed(initial);
        when(evaluator.evaluate(any(), any(), eq("candidate answer")))
                .thenReturn(followUp("What changes in Java 8?"));

        InterviewSnapshot result = service.answer(
                USER_ID,
                INTERVIEW_ID,
                initial.session().currentQuestionId(),
                "candidate answer");

        assertEquals(
                2,
                result.currentQuestion().orElseThrow().followUpNumber());
    }

    @Test
    void thirdFollowUpRequestIsOverriddenAndReturnsNextMainQuestion() {
        InterviewSnapshot initial = waitingAt(1, 2);
        repository.seed(initial);
        when(evaluator.evaluate(any(), any(), eq("candidate answer")))
                .thenReturn(followUp("Illegal third follow-up"));
        when(questionGenerator.generate(any(), eq(2))).thenReturn(
                generated("Explain ConcurrentHashMap"));

        InterviewSnapshot result = service.answer(
                USER_ID,
                INTERVIEW_ID,
                initial.session().currentQuestionId(),
                "candidate answer");

        assertEquals(InterviewSession.Status.IN_PROGRESS, result.session().status());
        assertEquals(2, result.session().mainQuestionCount());
        assertEquals(
                "Explain ConcurrentHashMap",
                result.currentQuestion().orElseThrow().text());
        assertEquals(
                2,
                result.questions().stream()
                        .filter(question -> question.type()
                                == InterviewQuestion.Type.FOLLOW_UP)
                        .count());
    }

    @Test
    void nextMainDecisionGeneratesNextMainQuestionImmediately() {
        InterviewSnapshot initial = waitingAt(1, 0);
        repository.seed(initial);
        when(evaluator.evaluate(any(), any(), eq("candidate answer")))
                .thenReturn(nextMain());
        when(questionGenerator.generate(any(), eq(2))).thenReturn(
                generated("Explain the JVM memory model"));

        InterviewSnapshot result = service.answer(
                USER_ID,
                INTERVIEW_ID,
                initial.session().currentQuestionId(),
                "candidate answer");

        assertEquals(2, result.session().mainQuestionCount());
        assertEquals(
                InterviewQuestion.Type.MAIN,
                result.currentQuestion().orElseThrow().type());
    }

    @Test
    void mainQuestionFiveCanStillCreateTwoFollowUps() {
        InterviewSnapshot mainFive = waitingAt(5, 0);
        repository.seed(mainFive);
        when(evaluator.evaluate(any(), any(), eq("main answer")))
                .thenReturn(followUp("Follow-up one"));

        InterviewSnapshot followUpOne = service.answer(
                USER_ID,
                INTERVIEW_ID,
                mainFive.session().currentQuestionId(),
                "main answer");
        when(evaluator.evaluate(any(), any(), eq("follow-up answer")))
                .thenReturn(followUp("Follow-up two"));

        InterviewSnapshot followUpTwo = service.answer(
                USER_ID,
                INTERVIEW_ID,
                followUpOne.session().currentQuestionId(),
                "follow-up answer");

        assertEquals(
                2,
                followUpTwo.currentQuestion().orElseThrow().followUpNumber());
        assertEquals(InterviewSession.Status.IN_PROGRESS, followUpTwo.session().status());
    }

    @Test
    void finishingFifthQuestionThreadEntersScoringPending() {
        InterviewSnapshot initial = waitingAt(5, 2);
        repository.seed(initial);
        when(evaluator.evaluate(any(), any(), eq("candidate answer")))
                .thenReturn(nextMain());

        InterviewSnapshot result = service.answer(
                USER_ID,
                INTERVIEW_ID,
                initial.session().currentQuestionId(),
                "candidate answer");

        assertEquals(
                InterviewSession.Status.SCORING_PENDING,
                result.session().status());
        assertTrue(result.currentQuestion().isEmpty());
    }

    @Test
    void evaluationFailureLeavesQuestionUnanswered() {
        InterviewSnapshot initial = waitingAt(1, 0);
        repository.seed(initial);
        when(evaluator.evaluate(any(), any(), eq("candidate answer")))
                .thenThrow(new IllegalStateException("model down"));

        InterviewServiceException error = assertThrows(
                InterviewServiceException.class,
                () -> service.answer(
                        USER_ID,
                        INTERVIEW_ID,
                        initial.session().currentQuestionId(),
                        "candidate answer"));

        assertEquals(
                InterviewServiceException.Kind.AI_UNAVAILABLE,
                error.kind());
        InterviewSnapshot persisted = repository.findByIdAndUserId(
                INTERVIEW_ID,
                USER_ID).orElseThrow();
        assertEquals(0, persisted.answers().size());
        assertEquals(
                InterviewQuestion.Status.WAITING_FOR_ANSWER,
                persisted.currentQuestion().orElseThrow().status());
    }

    @Test
    void blankAnswerIsRejectedBeforeEvaluation() {
        InterviewSnapshot initial = waitingAt(1, 0);
        repository.seed(initial);

        InterviewServiceException error = assertThrows(
                InterviewServiceException.class,
                () -> service.answer(
                        USER_ID,
                        INTERVIEW_ID,
                        initial.session().currentQuestionId(),
                        "  "));

        assertEquals(InterviewServiceException.Kind.BAD_REQUEST, error.kind());
        verifyNoInteractions(evaluator);
    }

    @Test
    void staleQuestionIdIsRejected() {
        InterviewSnapshot initial = waitingAt(1, 1);
        repository.seed(initial);

        InterviewServiceException error = assertThrows(
                InterviewServiceException.class,
                () -> service.answer(
                        USER_ID,
                        INTERVIEW_ID,
                        UUID.randomUUID(),
                        "candidate answer"));

        assertEquals(InterviewServiceException.Kind.CONFLICT, error.kind());
        verifyNoInteractions(evaluator);
    }

    @Test
    void alreadyAnsweredQuestionReturnsLatestSnapshotWithoutEvaluation() {
        InterviewSnapshot initial = waitingAt(1, 1);
        repository.seed(initial);
        UUID answeredMainId = initial.questions().stream()
                .filter(question -> question.type() == InterviewQuestion.Type.MAIN)
                .findFirst()
                .orElseThrow()
                .id();

        InterviewSnapshot result = service.answer(
                USER_ID,
                INTERVIEW_ID,
                answeredMainId,
                "retried answer");

        assertEquals(initial, result);
        verifyNoInteractions(evaluator);
    }

    private InterviewSnapshot waitingAt(int mainNumber, int followUpNumber) {
        List<InterviewQuestion> questions = new ArrayList<>();
        List<InterviewAnswer> answers = new ArrayList<>();
        UUID mainId = new UUID(1, mainNumber * 10L);
        InterviewQuestion main = InterviewQuestion.main(
                mainId,
                INTERVIEW_ID,
                mainNumber,
                "Main question " + mainNumber,
                List.of("JAVA"),
                List.of(),
                NOW);
        if (followUpNumber == 0) {
            questions.add(main);
        } else {
            questions.add(answered(main));
            answers.add(answer(main, "main answer"));
            for (int number = 1; number <= followUpNumber; number++) {
                InterviewQuestion followUp = InterviewQuestion.followUp(
                        new UUID(2, mainNumber * 10L + number),
                        INTERVIEW_ID,
                        mainNumber,
                        number,
                        number == 1
                                ? mainId
                                : questions.get(questions.size() - 1).id(),
                        "Follow-up " + number,
                        List.of("JAVA"),
                        List.of(),
                        NOW);
                if (number < followUpNumber) {
                    questions.add(answered(followUp));
                    answers.add(answer(followUp, "follow-up answer"));
                } else {
                    questions.add(followUp);
                }
            }
        }
        UUID currentId = questions.get(questions.size() - 1).id();
        InterviewSession session = new InterviewSession(
                INTERVIEW_ID,
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.IN_PROGRESS,
                mainNumber,
                currentId,
                answers.size(),
                7,
                NOW,
                NOW,
                null);
        return new InterviewSnapshot(session, questions, answers, null);
    }

    private InterviewQuestion answered(InterviewQuestion question) {
        return new InterviewQuestion(
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
                NOW);
    }

    private InterviewAnswer answer(
            InterviewQuestion question,
            String answerText) {
        return new InterviewAnswer(
                new UUID(3, question.id().getLeastSignificantBits()),
                INTERVIEW_ID,
                question.id(),
                answerText,
                "evaluated",
                List.of("JAVA"),
                InterviewAnswer.Decision.FOLLOW_UP,
                "needs detail",
                NOW);
    }

    private InterviewAiContracts.AnswerEvaluation followUp(String question) {
        return new InterviewAiContracts.AnswerEvaluation(
                "partial",
                List.of("JAVA"),
                InterviewAnswer.Decision.FOLLOW_UP,
                question,
                "needs detail");
    }

    private InterviewAiContracts.AnswerEvaluation nextMain() {
        return new InterviewAiContracts.AnswerEvaluation(
                "complete",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                null,
                "sufficient");
    }

    private InterviewAiContracts.GeneratedQuestion generated(String question) {
        return new InterviewAiContracts.GeneratedQuestion(
                question,
                List.of("JAVA"),
                List.of());
    }
}
