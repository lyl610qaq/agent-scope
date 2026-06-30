package com.example.demoscope.controller.interview;

import com.example.demoscope.biz.auth.AuthenticatedUserContext;
import com.example.demoscope.domain.auth.UnauthenticatedUserException;
import com.example.demoscope.controller.interview.InterviewController;
import com.example.demoscope.service.interview.InterviewService;
import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewServiceException;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InterviewControllerTest {

    private static final String USER_ID = "user-42";
    private static final UUID INTERVIEW_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000401");
    private static final UUID QUESTION_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000402");
    private static final Instant NOW = Instant.parse("2026-06-16T03:00:00Z");

    private InterviewService service;
    private HeaderUserContext userContext;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(InterviewService.class);
        userContext = new HeaderUserContext();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InterviewController(service, userContext))
                .build();
    }

    @Test
    void createReturnsCandidateSafeMainQuestion() throws Exception {
        when(service.createOrResume(
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE))
                .thenReturn(inProgressMain());

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction":"JAVA_BACKEND",
                                  "difficulty":"MIDDLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewId")
                        .value(INTERVIEW_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.nextAction").value("MAIN_QUESTION"))
                .andExpect(jsonPath("$.question.questionId")
                        .value(QUESTION_ID.toString()))
                .andExpect(jsonPath("$.question.text")
                        .value("Explain HashMap"))
                .andExpect(jsonPath("$.internalEvaluation").doesNotExist())
                .andExpect(jsonPath("$.decisionReason").doesNotExist())
                .andExpect(jsonPath("$.evidenceIds").doesNotExist());
    }

    @Test
    void currentAndOwnedInterviewRoutesReturnSnapshots() throws Exception {
        when(service.current(USER_ID)).thenReturn(inProgressMain());
        when(service.get(USER_ID, INTERVIEW_ID)).thenReturn(inProgressMain());

        mockMvc.perform(get("/api/interviews/current")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewId")
                        .value(INTERVIEW_ID.toString()));
        mockMvc.perform(get("/api/interviews/{id}", INTERVIEW_ID)
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.text")
                        .value("Explain HashMap"));
    }

    @Test
    void answerReturnsFollowUpWithoutPrivateEvaluationFields()
            throws Exception {
        when(service.answer(
                eq(USER_ID),
                eq(INTERVIEW_ID),
                eq(QUESTION_ID),
                eq("volatile guarantees visibility")))
                .thenReturn(inProgressFollowUp());

        mockMvc.perform(post(
                        "/api/interviews/{id}/answers",
                        INTERVIEW_ID)
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId":"%s",
                                  "answer":"volatile guarantees visibility"
                                }
                                """.formatted(QUESTION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.followUpNumber").value(1))
                .andExpect(jsonPath("$.question.questionId").exists())
                .andExpect(jsonPath("$.internalEvaluation").doesNotExist())
                .andExpect(jsonPath("$.decisionReason").doesNotExist())
                .andExpect(jsonPath("$.evidenceIds").doesNotExist());
    }

    @Test
    void finishReturnsAcceptedWhileScoringIsPending() throws Exception {
        when(service.finish(USER_ID, INTERVIEW_ID))
                .thenReturn(scoringPending());

        mockMvc.perform(post(
                        "/api/interviews/{id}/finish",
                        INTERVIEW_ID)
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SCORING_PENDING"))
                .andExpect(jsonPath("$.nextAction").value("REPORT_PENDING"));
    }

    @Test
    void completedResponseExposesOnlyCandidateReport() throws Exception {
        when(service.get(USER_ID, INTERVIEW_ID))
                .thenReturn(completed());

        mockMvc.perform(get("/api/interviews/{id}", INTERVIEW_ID)
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("REPORT"))
                .andExpect(jsonPath("$.report.overallScore").value(78))
                .andExpect(jsonPath("$.report.javaFundamentalsScore")
                        .value(82))
                .andExpect(jsonPath("$.report.strengths[0]")
                        .value("clear fundamentals"))
                .andExpect(jsonPath("$.internalEvaluation").doesNotExist())
                .andExpect(jsonPath("$.decisionReason").doesNotExist())
                .andExpect(jsonPath("$.evidenceIds").doesNotExist());
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/interviews/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsupportedEnumReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction":"PYTHON",
                                  "difficulty":"MIDDLE"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownershipFailureReturnsNotFound() throws Exception {
        when(service.get(USER_ID, INTERVIEW_ID)).thenThrow(
                serviceError(
                        InterviewServiceException.Kind.NOT_FOUND,
                        "interview not found",
                        null));

        mockMvc.perform(get("/api/interviews/{id}", INTERVIEW_ID)
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleQuestionReturnsConflict() throws Exception {
        when(service.answer(
                eq(USER_ID),
                eq(INTERVIEW_ID),
                any(UUID.class),
                any(String.class)))
                .thenThrow(serviceError(
                        InterviewServiceException.Kind.CONFLICT,
                        "interview state conflict",
                        inProgressMain()));

        mockMvc.perform(post(
                        "/api/interviews/{id}/answers",
                        INTERVIEW_ID)
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId":"%s",
                                  "answer":"answer"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void aiFailureReturnsUnavailableWithPendingSafeView()
            throws Exception {
        InterviewSnapshot pending = generationPending();
        when(service.createOrResume(
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE))
                .thenThrow(serviceError(
                        InterviewServiceException.Kind.AI_UNAVAILABLE,
                        "interview AI is temporarily unavailable",
                        pending));

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction":"JAVA_BACKEND",
                                  "difficulty":"MIDDLE"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status")
                        .value("QUESTION_GENERATION_PENDING"))
                .andExpect(jsonPath("$.nextAction")
                        .value("MAIN_QUESTION"))
                .andExpect(jsonPath("$.question").doesNotExist())
                .andExpect(jsonPath("$.internalEvaluation").doesNotExist());
    }

    private InterviewSnapshot inProgressMain() {
        InterviewQuestion question = InterviewQuestion.main(
                QUESTION_ID,
                INTERVIEW_ID,
                1,
                "Explain HashMap",
                List.of("JAVA"),
                List.of("private-evidence"),
                NOW);
        return snapshot(
                InterviewSession.Status.IN_PROGRESS,
                QUESTION_ID,
                1,
                0,
                List.of(question),
                List.of(),
                null);
    }

    private InterviewSnapshot inProgressFollowUp() {
        InterviewQuestion main = answered(
                inProgressMain().questions().get(0));
        InterviewQuestion followUp = InterviewQuestion.followUp(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000403"),
                INTERVIEW_ID,
                1,
                1,
                main.id(),
                "Why powers of two?",
                List.of("JAVA"),
                List.of("private-evidence"),
                NOW);
        InterviewAnswer answer = new InterviewAnswer(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000404"),
                INTERVIEW_ID,
                main.id(),
                "candidate answer",
                "private evaluation",
                List.of("JAVA"),
                InterviewAnswer.Decision.FOLLOW_UP,
                "private reason",
                NOW);
        return snapshot(
                InterviewSession.Status.IN_PROGRESS,
                followUp.id(),
                1,
                1,
                List.of(main, followUp),
                List.of(answer),
                null);
    }

    private InterviewSnapshot generationPending() {
        return snapshot(
                InterviewSession.Status.QUESTION_GENERATION_PENDING,
                null,
                0,
                0,
                List.of(),
                List.of(),
                null);
    }

    private InterviewSnapshot scoringPending() {
        InterviewSnapshot source = inProgressFollowUp();
        return snapshot(
                InterviewSession.Status.SCORING_PENDING,
                null,
                1,
                1,
                source.questions(),
                source.answers(),
                null);
    }

    private InterviewSnapshot completed() {
        InterviewSnapshot source = scoringPending();
        InterviewReport report = new InterviewReport(
                INTERVIEW_ID,
                78,
                82,
                70,
                75,
                80,
                76,
                84,
                List.of("clear fundamentals"),
                List.of("concurrency depth"),
                List.of("practice thread safety"),
                NOW);
        return snapshot(
                InterviewSession.Status.COMPLETED,
                null,
                1,
                1,
                source.questions(),
                source.answers(),
                report);
    }

    private InterviewSnapshot snapshot(
            InterviewSession.Status status,
            UUID currentQuestionId,
            int mainQuestionCount,
            int answeredQuestionCount,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers,
            InterviewReport report) {
        Instant completedAt = status == InterviewSession.Status.COMPLETED
                || status == InterviewSession.Status.CANCELLED
                ? NOW
                : null;
        InterviewSession session = new InterviewSession(
                INTERVIEW_ID,
                USER_ID,
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                status,
                mainQuestionCount,
                currentQuestionId,
                answeredQuestionCount,
                3,
                NOW,
                NOW,
                completedAt);
        return new InterviewSnapshot(session, questions, answers, report);
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

    private InterviewServiceException serviceError(
            InterviewServiceException.Kind kind,
            String message,
            InterviewSnapshot snapshot) {
        return new InterviewServiceException(kind, message, snapshot);
    }

    private static final class HeaderUserContext
            implements AuthenticatedUserContext {

        @Override
        public String requireUserId(HttpServletRequest request) {
            if (!"Bearer token-123".equals(
                    request.getHeader("Authorization"))) {
                throw new UnauthenticatedUserException(
                        "invalid authentication token");
            }
            return USER_ID;
        }
    }
}
