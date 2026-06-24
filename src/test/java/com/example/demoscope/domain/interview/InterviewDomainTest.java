package com.example.demoscope.domain.interview;

import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewAiContracts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InterviewDomainTest {

    @Test
    void acceptsMainAndFollowUpQuestionPositions() {
        UUID interviewId = UUID.randomUUID();

        InterviewQuestion main = InterviewQuestion.main(
                UUID.randomUUID(),
                interviewId,
                1,
                "Explain HashMap.",
                List.of("JAVA"),
                List.of("k-1"),
                Instant.EPOCH);
        InterviewQuestion followUp = InterviewQuestion.followUp(
                UUID.randomUUID(),
                interviewId,
                1,
                2,
                main.id(),
                "Why powers of two?",
                List.of("JAVA"),
                List.of(),
                Instant.EPOCH);

        assertEquals(0, main.followUpNumber());
        assertEquals(2, followUp.followUpNumber());
    }

    @Test
    void rejectsMoreThanFiveMainQuestionsOrTwoFollowUps() {
        UUID interviewId = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> InterviewQuestion.main(
                        UUID.randomUUID(),
                        interviewId,
                        6,
                        "bad",
                        List.of(),
                        List.of(),
                        Instant.EPOCH));
        assertThrows(
                IllegalArgumentException.class,
                () -> InterviewQuestion.followUp(
                        UUID.randomUUID(),
                        interviewId,
                        1,
                        3,
                        UUID.randomUUID(),
                        "bad",
                        List.of(),
                        List.of(),
                        Instant.EPOCH));
    }

    @Test
    void rejectsInvalidReportScoresAndBlankFeedback() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewReport(
                        UUID.randomUUID(),
                        101,
                        80,
                        80,
                        80,
                        80,
                        80,
                        80,
                        List.of("strength"),
                        List.of("weakness"),
                        List.of("improve"),
                        Instant.EPOCH));
        assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewReport(
                        UUID.randomUUID(),
                        80,
                        80,
                        80,
                        80,
                        80,
                        80,
                        80,
                        List.of(" "),
                        List.of("weakness"),
                        List.of("improve"),
                        Instant.EPOCH));
    }

    @Test
    void answerEvaluationRequiresFollowUpTextOnlyForFollowUpDecision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewAiContracts.AnswerEvaluation(
                        "partial",
                        List.of("JVM"),
                        InterviewAnswer.Decision.FOLLOW_UP,
                        "",
                        "needs detail"));

        InterviewAiContracts.AnswerEvaluation next =
                new InterviewAiContracts.AnswerEvaluation(
                        "good",
                        List.of("JVM"),
                        InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                        null,
                        "complete");

        assertEquals(
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                next.decision());
    }
}
