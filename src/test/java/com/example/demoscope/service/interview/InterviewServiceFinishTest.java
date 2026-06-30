package com.example.demoscope.service.interview;

import com.example.demoscope.testsupport.interview.MutableInterviewRepository;
import com.example.demoscope.service.interview.InterviewService;
import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewAnswerEvaluator;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewQuestionGenerator;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewReportGenerator;
import com.example.demoscope.domain.interview.InterviewServiceException;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

class InterviewServiceFinishTest {

    private static final String USER_ID = "user-42";
    private static final UUID INTERVIEW_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000301");
    private static final Instant NOW = Instant.parse("2026-06-16T02:00:00Z");

    private MutableInterviewRepository repository;
    private InterviewQuestionGenerator questionGenerator;
    private InterviewAnswerEvaluator evaluator;
    private InterviewReportGenerator reportGenerator;
    private InterviewService service;

    @BeforeEach
    void setUp() {
        repository = new MutableInterviewRepository();
        questionGenerator = mock(InterviewQuestionGenerator.class);
        evaluator = mock(InterviewAnswerEvaluator.class);
        reportGenerator = mock(InterviewReportGenerator.class);
        AtomicLong ids = new AtomicLong(2000);
        service = new InterviewService(
                repository,
                questionGenerator,
                evaluator,
                reportGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> new UUID(0, ids.getAndIncrement()),
                5,
                2);
    }

    @Test
    void zeroAnswerInterviewIsCancelledWithoutScoring() {
        repository.seed(inProgressWithAnswers(0, 1));

        InterviewSnapshot result = service.finish(USER_ID, INTERVIEW_ID);

        assertEquals(InterviewSession.Status.CANCELLED, result.session().status());
        assertNull(result.report());
        verifyNoInteractions(reportGenerator);
    }

    @Test
    void voluntaryFinishAfterOneAnswerGeneratesReport() {
        repository.seed(inProgressWithAnswers(1, 2));
        when(reportGenerator.generate(any())).thenReturn(reportDraft(78));

        InterviewSnapshot result = service.finish(USER_ID, INTERVIEW_ID);

        assertEquals(InterviewSession.Status.COMPLETED, result.session().status());
        assertEquals(78, result.report().overallScore());
        assertEquals(82, result.report().javaFundamentalsScore());
    }

    @Test
    void scoringFailureKeepsRetryablePendingState() {
        repository.seed(scoringPendingSnapshot());
        when(reportGenerator.generate(any()))
                .thenThrow(new IllegalStateException("model down"));

        InterviewSnapshot result = service.finish(USER_ID, INTERVIEW_ID);

        assertEquals(
                InterviewSession.Status.SCORING_PENDING,
                result.session().status());
        assertNull(result.report());
    }

    @Test
    void automaticFifthThreadCompletionImmediatelyAttemptsScoring() {
        InterviewSnapshot initial = fifthQuestionWaiting();
        repository.seed(initial);
        when(evaluator.evaluate(any(), any(), any()))
                .thenReturn(new InterviewAiContracts.AnswerEvaluation(
                        "complete",
                        List.of("JAVA"),
                        InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                        null,
                        "sufficient"));
        when(reportGenerator.generate(any())).thenReturn(reportDraft(88));

        InterviewSnapshot result = service.answer(
                USER_ID,
                INTERVIEW_ID,
                initial.session().currentQuestionId(),
                "candidate answer");

        assertEquals(InterviewSession.Status.COMPLETED, result.session().status());
        assertEquals(88, result.report().overallScore());
    }

    @Test
    void completedFinishIsIdempotent() {
        InterviewSnapshot completed = completedSnapshot();
        repository.seed(completed);

        InterviewSnapshot result = service.finish(USER_ID, INTERVIEW_ID);

        assertEquals(completed, result);
        verifyNoInteractions(reportGenerator);
    }

    @Test
    void cancelledFinishIsIdempotent() {
        InterviewSnapshot cancelled = cancelledSnapshot();
        repository.seed(cancelled);

        InterviewSnapshot result = service.finish(USER_ID, INTERVIEW_ID);

        assertEquals(cancelled, result);
        verifyNoInteractions(reportGenerator);
    }

    @Test
    void scoringPendingInterviewRejectsAnswers() {
        InterviewSnapshot pending = scoringPendingSnapshot();
        repository.seed(pending);

        InterviewServiceException error = assertThrows(
                InterviewServiceException.class,
                () -> service.answer(
                        USER_ID,
                        INTERVIEW_ID,
                        UUID.randomUUID(),
                        "late answer"));

        assertEquals(InterviewServiceException.Kind.CONFLICT, error.kind());
        verifyNoInteractions(evaluator);
    }

    @Test
    void optimisticReportConflictReloadsWinner() {
        InterviewSnapshot pending = scoringPendingSnapshot();
        InterviewSnapshot winner = completedSnapshot();
        repository.seed(pending);
        repository.winnerOnNextMutation(winner);
        when(reportGenerator.generate(any())).thenReturn(reportDraft(78));

        InterviewSnapshot result = service.finish(USER_ID, INTERVIEW_ID);

        assertEquals(winner, result);
    }

    private InterviewSnapshot inProgressWithAnswers(
            int answerCount,
            int mainQuestionNumber) {
        List<InterviewQuestion> questions = new ArrayList<>();
        List<InterviewAnswer> answers = new ArrayList<>();
        for (int number = 1; number <= mainQuestionNumber; number++) {
            UUID questionId = new UUID(4, number);
            InterviewQuestion question = InterviewQuestion.main(
                    questionId,
                    INTERVIEW_ID,
                    number,
                    "Question " + number,
                    List.of("JAVA"),
                    List.of(),
                    NOW);
            if (number <= answerCount) {
                question = answered(question);
                answers.add(answer(question));
            }
            questions.add(question);
        }
        UUID currentQuestionId = questions.get(questions.size() - 1).id();
        InterviewSession session = new InterviewSession(
                INTERVIEW_ID,
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.IN_PROGRESS,
                mainQuestionNumber,
                currentQuestionId,
                answerCount,
                4,
                NOW,
                NOW,
                null);
        return new InterviewSnapshot(session, questions, answers, null);
    }

    private InterviewSnapshot scoringPendingSnapshot() {
        InterviewSnapshot source = inProgressWithAnswers(1, 1);
        InterviewSession session = source.session();
        return new InterviewSnapshot(
                new InterviewSession(
                        session.id(),
                        session.userId(),
                        session.direction(),
                        session.difficulty(),
                        InterviewSession.Status.SCORING_PENDING,
                        session.mainQuestionCount(),
                        null,
                        session.answeredQuestionCount(),
                        session.version(),
                        session.createdAt(),
                        session.updatedAt(),
                        null),
                source.questions(),
                source.answers(),
                null);
    }

    private InterviewSnapshot fifthQuestionWaiting() {
        InterviewSnapshot source = inProgressWithAnswers(4, 5);
        InterviewSession session = source.session();
        return new InterviewSnapshot(
                new InterviewSession(
                        session.id(),
                        session.userId(),
                        session.direction(),
                        session.difficulty(),
                        InterviewSession.Status.IN_PROGRESS,
                        5,
                        session.currentQuestionId(),
                        4,
                        session.version(),
                        session.createdAt(),
                        session.updatedAt(),
                        null),
                source.questions(),
                source.answers(),
                null);
    }

    private InterviewSnapshot completedSnapshot() {
        InterviewSnapshot source = scoringPendingSnapshot();
        InterviewReport report = report(91);
        InterviewSession session = source.session();
        return new InterviewSnapshot(
                new InterviewSession(
                        session.id(),
                        session.userId(),
                        session.direction(),
                        session.difficulty(),
                        InterviewSession.Status.COMPLETED,
                        session.mainQuestionCount(),
                        null,
                        session.answeredQuestionCount(),
                        session.version() + 1,
                        session.createdAt(),
                        NOW,
                        NOW),
                source.questions(),
                source.answers(),
                report);
    }

    private InterviewSnapshot cancelledSnapshot() {
        InterviewSnapshot source = inProgressWithAnswers(0, 1);
        InterviewSession session = source.session();
        return new InterviewSnapshot(
                new InterviewSession(
                        session.id(),
                        session.userId(),
                        session.direction(),
                        session.difficulty(),
                        InterviewSession.Status.CANCELLED,
                        session.mainQuestionCount(),
                        null,
                        0,
                        session.version() + 1,
                        session.createdAt(),
                        NOW,
                        NOW),
                source.questions(),
                List.of(),
                null);
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

    private InterviewAnswer answer(InterviewQuestion question) {
        return new InterviewAnswer(
                new UUID(5, question.id().getLeastSignificantBits()),
                INTERVIEW_ID,
                question.id(),
                "candidate answer",
                "good",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                "complete",
                NOW);
    }

    private InterviewAiContracts.ReportDraft reportDraft(int overall) {
        return new InterviewAiContracts.ReportDraft(
                overall,
                new InterviewAiContracts.ScoreBreakdown(
                        82,
                        70,
                        75,
                        80,
                        76,
                        84),
                List.of("clear fundamentals"),
                List.of("concurrency depth"),
                List.of("practice thread safety"));
    }

    private InterviewReport report(int overall) {
        InterviewAiContracts.ReportDraft draft = reportDraft(overall);
        return new InterviewReport(
                INTERVIEW_ID,
                draft.overallScore(),
                draft.scores().javaFundamentals(),
                draft.scores().concurrency(),
                draft.scores().jvm(),
                draft.scores().spring(),
                draft.scores().database(),
                draft.scores().engineering(),
                draft.strengths(),
                draft.weaknesses(),
                draft.improvementSuggestions(),
                NOW);
    }
}
