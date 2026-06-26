package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ScoreReviewBizTest {

    @Test
    void rejectedDraftIsRegeneratedWithReviewerInstructions() {
        FakeScoreDraftGenerator generator = new FakeScoreDraftGenerator(
                report(92),
                report(78));
        FakeScoreReviewAgent reviewer = new FakeScoreReviewAgent(
                new ScoreReviewDecision(
                        false,
                        "Concurrency score is too high for the transcript.",
                        "Lower concurrency and overall score."),
                new ScoreReviewDecision(
                        true,
                        "Scores now match the transcript.",
                        null));
        ScoreReviewBiz biz = new ScoreReviewBiz(generator, reviewer, 2);

        InterviewAiContracts.ReportDraft result = biz.generate(snapshot());

        assertEquals(78, result.overallScore());
        assertEquals(2, generator.calls);
        assertEquals(null, generator.reviewFeedbacks.get(0));
        assertEquals(
                "Lower concurrency and overall score.",
                generator.reviewFeedbacks.get(1));
    }

    @Test
    void exhaustedReviewAttemptsFailWithoutAcceptingRejectedDraft() {
        FakeScoreDraftGenerator generator = new FakeScoreDraftGenerator(
                report(92),
                report(88));
        FakeScoreReviewAgent reviewer = new FakeScoreReviewAgent(
                new ScoreReviewDecision(
                        false,
                        "First draft is too generous.",
                        "Reduce the scores."),
                new ScoreReviewDecision(
                        false,
                        "Second draft is still too generous.",
                        "Reduce the scores again."));
        ScoreReviewBiz biz = new ScoreReviewBiz(generator, reviewer, 2);

        assertThrows(
                ScoreReviewBiz.ScoreReviewFailedException.class,
                () -> biz.generate(snapshot()));
        assertEquals(2, generator.calls);
    }

    private static InterviewAiContracts.ReportDraft report(int overallScore) {
        return new InterviewAiContracts.ReportDraft(
                overallScore,
                new InterviewAiContracts.ScoreBreakdown(
                        overallScore,
                        70,
                        72,
                        74,
                        76,
                        78),
                List.of("clear basics"),
                List.of("needs depth"),
                List.of("practice concurrency"));
    }

    private static InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000801");
        UUID questionId = UUID.fromString(
                "00000000-0000-0000-0000-000000000802");
        InterviewQuestion question = InterviewQuestion.main(
                questionId,
                interviewId,
                1,
                "Explain HashMap",
                List.of("JAVA"),
                List.of(),
                Instant.EPOCH);
        InterviewSession session = new InterviewSession(
                interviewId,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.SCORING_PENDING,
                1,
                null,
                1,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        InterviewAnswer answer = new InterviewAnswer(
                UUID.fromString("00000000-0000-0000-0000-000000000803"),
                interviewId,
                questionId,
                "candidate answer",
                "partial answer",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                "complete",
                Instant.EPOCH);
        return new InterviewSnapshot(
                session,
                List.of(question),
                List.of(answer),
                null);
    }

    private static final class FakeScoreDraftGenerator
            implements ScoreDraftGenerator {

        private final Queue<InterviewAiContracts.ReportDraft> drafts =
                new ArrayDeque<>();
        private final List<String> reviewFeedbacks = new ArrayList<>();
        private int calls;

        private FakeScoreDraftGenerator(
                InterviewAiContracts.ReportDraft... drafts) {
            this.drafts.addAll(List.of(drafts));
        }

        @Override
        public InterviewAiContracts.ReportDraft generate(
                InterviewSnapshot snapshot,
                String reviewFeedback) {
            calls++;
            reviewFeedbacks.add(reviewFeedback);
            return drafts.remove();
        }
    }

    private static final class FakeScoreReviewAgent implements ScoreReviewAgent {

        private final Queue<ScoreReviewDecision> decisions = new ArrayDeque<>();

        private FakeScoreReviewAgent(ScoreReviewDecision... decisions) {
            this.decisions.addAll(List.of(decisions));
        }

        @Override
        public ScoreReviewDecision review(
                InterviewSnapshot snapshot,
                InterviewAiContracts.ReportDraft draft) {
            return decisions.remove();
        }
    }
}
