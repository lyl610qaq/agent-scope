package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthenticatedInterviewFlowTest {

    private static final String TOKEN = "Bearer token-123";
    private static final String OTHER_TOKEN = "Bearer token-456";
    private static final Instant NOW = Instant.parse("2026-06-16T04:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MutableInterviewRepository repository;
    private QueuedQuestionGenerator questionGenerator;
    private QueuedEvaluator evaluator;
    private RetryableReportGenerator reportGenerator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = new MutableInterviewRepository();
        questionGenerator = new QueuedQuestionGenerator();
        evaluator = new QueuedEvaluator();
        reportGenerator = new RetryableReportGenerator();
        AtomicLong ids = new AtomicLong(10_000);
        InterviewService service = new InterviewService(
                repository,
                questionGenerator,
                evaluator,
                reportGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> new UUID(0, ids.getAndIncrement()),
                5,
                2);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InterviewController(
                                service,
                                new TokenUserContext()))
                .build();
    }

    @Test
    void authenticatedFiveQuestionFlowEnforcesFollowUpsAndReturnsReport()
            throws Exception {
        questionGenerator.add(
                "Main 1",
                "Main 2",
                "Main 3",
                "Main 4",
                "Main 5");
        evaluator.add(
                followUp("Follow-up 1.1"),
                followUp("Follow-up 1.2"),
                followUp("Illegal follow-up 1.3"),
                nextMain(),
                nextMain(),
                nextMain(),
                followUp("Follow-up 5.1"),
                followUp("Follow-up 5.2"),
                nextMain());
        reportGenerator.failNext = true;

        JsonNode state = create(TOKEN, 200);
        UUID interviewId = uuid(state, "interviewId");
        assertQuestion(state, "Main 1", "MAIN_QUESTION", 1, 0);

        UUID mainOneId = questionId(state);
        state = answer(TOKEN, interviewId, mainOneId, "answer 1", 200);
        assertQuestion(state, "Follow-up 1.1", "FOLLOW_UP", 1, 1);

        JsonNode repeated = answer(
                TOKEN,
                interviewId,
                mainOneId,
                "duplicate answer",
                200);
        assertEquals(questionId(state), questionId(repeated));
        assertEquals(
                1,
                repository.findByIdAndUserId(interviewId, "user-42")
                        .orElseThrow()
                        .answers()
                        .size());

        state = answerCurrent(state, interviewId, "follow-up answer 1", 200);
        assertQuestion(state, "Follow-up 1.2", "FOLLOW_UP", 1, 2);
        state = answerCurrent(state, interviewId, "follow-up answer 2", 200);
        assertQuestion(state, "Main 2", "MAIN_QUESTION", 2, 0);
        assertEquals(
                2,
                repository.findByIdAndUserId(interviewId, "user-42")
                        .orElseThrow()
                        .questions()
                        .stream()
                        .filter(question -> question.type()
                                == InterviewQuestion.Type.FOLLOW_UP)
                        .count());

        state = answerCurrent(state, interviewId, "answer 2", 200);
        assertQuestion(state, "Main 3", "MAIN_QUESTION", 3, 0);
        state = answerCurrent(state, interviewId, "answer 3", 200);
        assertQuestion(state, "Main 4", "MAIN_QUESTION", 4, 0);
        state = answerCurrent(state, interviewId, "answer 4", 200);
        assertQuestion(state, "Main 5", "MAIN_QUESTION", 5, 0);
        state = answerCurrent(state, interviewId, "answer 5", 200);
        assertQuestion(state, "Follow-up 5.1", "FOLLOW_UP", 5, 1);
        state = answerCurrent(state, interviewId, "answer 5.1", 200);
        assertQuestion(state, "Follow-up 5.2", "FOLLOW_UP", 5, 2);
        state = answerCurrent(state, interviewId, "answer 5.2", 202);
        assertEquals("SCORING_PENDING", state.path("status").asText());
        assertEquals("REPORT_PENDING", state.path("nextAction").asText());

        state = finish(TOKEN, interviewId, 200);
        assertEquals("COMPLETED", state.path("status").asText());
        assertEquals("REPORT", state.path("nextAction").asText());
        assertEquals(86, state.path("report").path("overallScore").asInt());

        JsonNode fetched = getInterview(TOKEN, interviewId, 200);
        assertEquals(state, fetched);
        getInterview(OTHER_TOKEN, interviewId, 404);
    }

    @Test
    void evaluationFailureReturns503AndSameQuestionCanBeRetried()
            throws Exception {
        questionGenerator.add("Main 1", "Main 2");
        evaluator.add(nextMain());
        JsonNode state = create(TOKEN, 200);
        UUID interviewId = uuid(state, "interviewId");
        UUID questionId = questionId(state);
        evaluator.failNext = true;

        JsonNode failed = answer(
                TOKEN,
                interviewId,
                questionId,
                "first attempt",
                503);
        assertEquals(questionId, questionId(failed));
        assertEquals("IN_PROGRESS", failed.path("status").asText());

        JsonNode retried = answer(
                TOKEN,
                interviewId,
                questionId,
                "second attempt",
                200);
        assertQuestion(retried, "Main 2", "MAIN_QUESTION", 2, 0);
    }

    @Test
    void pendingMainQuestionGenerationRetriesThroughCreateEndpoint()
            throws Exception {
        questionGenerator.add("Main 1", "Main 2");
        evaluator.add(nextMain());
        JsonNode state = create(TOKEN, 200);
        UUID interviewId = uuid(state, "interviewId");
        questionGenerator.failNext = true;

        JsonNode pending = answerCurrent(
                state,
                interviewId,
                "candidate answer",
                503);
        assertEquals(
                "QUESTION_GENERATION_PENDING",
                pending.path("status").asText());
        assertFalse(pending.hasNonNull("question"));

        JsonNode resumed = create(TOKEN, 200);
        assertEquals(interviewId, uuid(resumed, "interviewId"));
        assertQuestion(resumed, "Main 2", "MAIN_QUESTION", 2, 0);
    }

    @Test
    void voluntaryFinishAfterOneAnswerReturnsReport() throws Exception {
        questionGenerator.add("Main 1");
        evaluator.add(followUp("Follow-up 1.1"));
        JsonNode state = create(TOKEN, 200);
        UUID interviewId = uuid(state, "interviewId");
        state = answerCurrent(state, interviewId, "candidate answer", 200);
        assertEquals("FOLLOW_UP", state.path("nextAction").asText());

        JsonNode finished = finish(TOKEN, interviewId, 200);

        assertEquals("COMPLETED", finished.path("status").asText());
        assertEquals(86, finished.path("report").path("overallScore").asInt());
    }

    @Test
    void finishBeforeAnyAnswerCancelsWithoutReport() throws Exception {
        questionGenerator.add("Main 1");
        JsonNode state = create(TOKEN, 200);
        UUID interviewId = uuid(state, "interviewId");

        JsonNode cancelled = finish(TOKEN, interviewId, 200);

        assertEquals("CANCELLED", cancelled.path("status").asText());
        assertEquals("CANCELLED", cancelled.path("nextAction").asText());
        assertFalse(cancelled.hasNonNull("report"));
    }

    private JsonNode create(String token, int expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "direction":"JAVA_BACKEND",
                                  "difficulty":"MIDDLE"
                                }
                                """))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return body(result);
    }

    private JsonNode answerCurrent(
            JsonNode state,
            UUID interviewId,
            String answer,
            int expectedStatus) throws Exception {
        return answer(
                TOKEN,
                interviewId,
                questionId(state),
                answer,
                expectedStatus);
    }

    private JsonNode answer(
            String token,
            UUID interviewId,
            UUID questionId,
            String answer,
            int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/interviews/{id}/answers",
                        interviewId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId":"%s",
                                  "answer":"%s"
                                }
                                """.formatted(questionId, answer)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return body(result);
    }

    private JsonNode finish(
            String token,
            UUID interviewId,
            int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/interviews/{id}/finish",
                        interviewId)
                        .header("Authorization", token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return body(result);
    }

    private JsonNode getInterview(
            String token,
            UUID interviewId,
            int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(
                        "/api/interviews/{id}",
                        interviewId)
                        .header("Authorization", token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        if (expectedStatus != 200) {
            return null;
        }
        return body(result);
    }

    private JsonNode body(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        assertSafe(json);
        return objectMapper.readTree(json);
    }

    private void assertSafe(String json) {
        assertFalse(json.contains("internalEvaluation"));
        assertFalse(json.contains("decisionReason"));
        assertFalse(json.contains("evidenceIds"));
        assertFalse(json.contains("private evaluation"));
        assertFalse(json.contains("private reason"));
    }

    private void assertQuestion(
            JsonNode state,
            String text,
            String nextAction,
            int mainQuestionNumber,
            int followUpNumber) {
        assertEquals(text, state.path("question").path("text").asText());
        assertEquals(nextAction, state.path("nextAction").asText());
        assertEquals(
                mainQuestionNumber,
                state.path("mainQuestionNumber").asInt());
        assertEquals(followUpNumber, state.path("followUpNumber").asInt());
    }

    private UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private UUID questionId(JsonNode state) {
        return uuid(state.path("question"), "questionId");
    }

    private InterviewAiContracts.AnswerEvaluation followUp(String question) {
        return new InterviewAiContracts.AnswerEvaluation(
                "private evaluation",
                List.of("JAVA"),
                InterviewAnswer.Decision.FOLLOW_UP,
                question,
                "private reason");
    }

    private InterviewAiContracts.AnswerEvaluation nextMain() {
        return new InterviewAiContracts.AnswerEvaluation(
                "private evaluation",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                null,
                "private reason");
    }

    private static final class QueuedQuestionGenerator
            implements InterviewQuestionGenerator {

        private final Deque<String> questions = new ArrayDeque<>();
        private boolean failNext;

        void add(String... values) {
            questions.addAll(List.of(values));
        }

        @Override
        public InterviewAiContracts.GeneratedQuestion generate(
                InterviewSnapshot snapshot,
                int mainQuestionNumber) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("question model unavailable");
            }
            return new InterviewAiContracts.GeneratedQuestion(
                    questions.removeFirst(),
                    List.of("JAVA"),
                    List.of("private-evidence"));
        }
    }

    private static final class QueuedEvaluator
            implements InterviewAnswerEvaluator {

        private final Deque<InterviewAiContracts.AnswerEvaluation>
                evaluations = new ArrayDeque<>();
        private boolean failNext;

        void add(InterviewAiContracts.AnswerEvaluation... values) {
            evaluations.addAll(List.of(values));
        }

        @Override
        public InterviewAiContracts.AnswerEvaluation evaluate(
                InterviewSnapshot snapshot,
                InterviewQuestion question,
                String candidateAnswer) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("evaluation model unavailable");
            }
            return evaluations.removeFirst();
        }
    }

    private static final class RetryableReportGenerator
            implements InterviewReportGenerator {

        private boolean failNext;

        @Override
        public InterviewAiContracts.ReportDraft generate(
                InterviewSnapshot snapshot) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("report model unavailable");
            }
            return new InterviewAiContracts.ReportDraft(
                    86,
                    new InterviewAiContracts.ScoreBreakdown(
                            88,
                            80,
                            84,
                            89,
                            82,
                            90),
                    List.of("clear Java fundamentals"),
                    List.of("deeper concurrency examples needed"),
                    List.of("practice JVM diagnostics"));
        }
    }

    private static final class TokenUserContext
            implements AuthenticatedUserContext {

        @Override
        public String requireUserId(HttpServletRequest request) {
            return switch (request.getHeader("Authorization")) {
                case TOKEN -> "user-42";
                case OTHER_TOKEN -> "user-99";
                default -> throw new UnauthenticatedUserException(
                        "invalid authentication token");
            };
        }
    }
}
