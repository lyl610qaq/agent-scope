package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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

class AgenticAuthenticatedInterviewFlowTest {

    private static final String TOKEN = "Bearer token-123";
    private static final Instant NOW = Instant.parse("2026-06-16T04:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MutableInterviewRepository repository;
    private QueuedRouter router;
    private CountingPlanner planner;
    private FailingMemoryManager memoryManager;
    private CapturingMemoryWriter memoryWriter;
    private Map<InterviewAgentName, QueuedTarget> targets;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = new MutableInterviewRepository();
        router = new QueuedRouter();
        planner = new CountingPlanner();
        memoryManager = new FailingMemoryManager();
        memoryWriter = new CapturingMemoryWriter();
        targets = new EnumMap<>(InterviewAgentName.class);
        for (InterviewAgentName name : InterviewAgentName.values()) {
            targets.put(name, new QueuedTarget(name));
        }
        InterviewAgentOrchestrator orchestrator =
                new InterviewAgentOrchestrator(
                        router,
                        planner,
                        new StaticEvidenceProvider(),
                        new EmptyMemoryProvider(),
                        memoryManager,
                        memoryWriter,
                        List.copyOf(targets.values()),
                        6);
        AtomicLong ids = new AtomicLong(20_000);
        InterviewService service = new InterviewService(
                repository,
                new AgenticInterviewQuestionGenerator(orchestrator),
                new AgenticInterviewAnswerEvaluator(orchestrator),
                new AgenticInterviewReportGenerator(orchestrator),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> new UUID(0, ids.getAndIncrement()),
                5,
                2);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InterviewController(service, new TokenUserContext()))
                .build();
    }

    @Test
    void agenticInterviewFlowUsesRouterPlannerTargetsAndMemory()
            throws Exception {
        memoryManager.fail = false;
        router.nextAgents.addAll(List.of(
                InterviewAgentName.JAVA_SKILL,
                InterviewAgentName.INTERVIEWER));
        targets.get(InterviewAgentName.JAVA_SKILL).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        generated("Main 1"),
                        List.of("doc-1")));
        targets.get(InterviewAgentName.INTERVIEWER).outputs.add(
                InterviewAgentOutput.answerEvaluation(
                        InterviewAgentName.INTERVIEWER,
                        nextMain(),
                        List.of("doc-2")));
        targets.get(InterviewAgentName.JAVA_SKILL).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        generated("Main 2"),
                        List.of("doc-3")));

        JsonNode state = create(TOKEN, 200);
        UUID interviewId = uuid(state, "interviewId");
        assertEquals("Main 1", state.path("question").path("text").asText());

        state = answerCurrent(state, interviewId, "candidate answer", 200);

        assertEquals("Main 2", state.path("question").path("text").asText());
        assertEquals(2, router.calls);
        assertEquals(2, planner.calls);
        assertEquals(2, memoryWriter.writes.size());
    }

    @Test
    void auxiliaryFailuresDegradeButTargetFailurePreservesRetryState()
            throws Exception {
        router.fail = true;
        planner.fail = true;
        memoryManager.fail = true;
        targets.get(InterviewAgentName.JAVA_SKILL).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        generated("Main 1"),
                        List.of()));

        JsonNode state = create(TOKEN, 200);
        assertEquals("Main 1", state.path("question").path("text").asText());

        targets.get(InterviewAgentName.INTERVIEWER).fail = true;
        JsonNode failed = answerCurrent(
                state,
                uuid(state, "interviewId"),
                "candidate answer",
                503);

        assertEquals("IN_PROGRESS", failed.path("status").asText());
        assertEquals(questionId(state), questionId(failed));
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

    private JsonNode body(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        assertSafe(json);
        return objectMapper.readTree(json);
    }

    private void assertSafe(String json) {
        assertFalse(json.contains("internalEvaluation"));
        assertFalse(json.contains("decisionReason"));
        assertFalse(json.contains("Bearer"));
        assertFalse(json.contains("Authorization"));
        assertFalse(json.contains("raw model"));
        assertFalse(json.contains("Redis key"));
    }

    private UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private UUID questionId(JsonNode state) {
        return uuid(state.path("question"), "questionId");
    }

    private InterviewAiContracts.GeneratedQuestion generated(String text) {
        return new InterviewAiContracts.GeneratedQuestion(
                text,
                List.of("JAVA"),
                List.of());
    }

    private InterviewAiContracts.AnswerEvaluation nextMain() {
        return new InterviewAiContracts.AnswerEvaluation(
                "private evaluation",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                null,
                "private reason");
    }

    private static final class QueuedRouter implements InterviewRouterAgent {

        private final Queue<InterviewAgentName> nextAgents = new ArrayDeque<>();
        private boolean fail;
        private int calls;

        @Override
        public RouterDecision route(AgentPromptContext context) {
            calls++;
            if (fail) {
                throw new IllegalStateException("router failed");
            }
            InterviewAgentName next = nextAgents.isEmpty()
                    ? context.task().defaultAgent()
                    : nextAgents.remove();
            return new RouterDecision(
                    next,
                    "route",
                    0.8,
                    "focus",
                    List.of());
        }
    }

    private static final class CountingPlanner
            implements InterviewRagPlannerAgent {

        private boolean fail;
        private int calls;

        @Override
        public RagQueryPlan plan(AgentPromptContext context) {
            calls++;
            if (fail) {
                throw new IllegalStateException("planner failed");
            }
            return new RagQueryPlan(List.of(
                    new RagQueryPlan.Query(
                            "query",
                            1,
                            List.of("java"),
                            "purpose",
                            "reference")));
        }
    }

    private static final class QueuedTarget implements InterviewTargetAgent {

        private final InterviewAgentName name;
        private final Queue<InterviewAgentOutput> outputs = new ArrayDeque<>();
        private boolean fail;

        private QueuedTarget(InterviewAgentName name) {
            this.name = name;
        }

        @Override
        public InterviewAgentName name() {
            return name;
        }

        @Override
        public InterviewAgentOutput run(AgentPromptContext context) {
            if (fail) {
                throw new IllegalStateException("target failed");
            }
            return outputs.remove();
        }
    }

    private static final class FailingMemoryManager
            implements InterviewMemoryManagerAgent {

        private boolean fail;

        @Override
        public MemoryWriteDecision decide(
                AgentPromptContext context,
                InterviewAgentOutput output) {
            if (fail) {
                throw new IllegalStateException("memory failed");
            }
            return new MemoryWriteDecision(
                    List.of("short"),
                    List.of(),
                    "reason");
        }
    }

    private static final class CapturingMemoryWriter
            implements InterviewMemoryWriter {

        private final List<MemoryWriteDecision> writes = new ArrayList<>();

        @Override
        public void write(InterviewSnapshot snapshot, MemoryWriteDecision decision) {
            writes.add(decision);
        }
    }

    private static final class StaticEvidenceProvider
            extends InterviewEvidenceProvider {

        private StaticEvidenceProvider() {
            super(input -> new float[] {0.1f}, query -> List.of());
        }

        @Override
        public List<KnowledgeChunk> retrieve(RagQueryPlan plan, int maxEvidence) {
            return List.of(new KnowledgeChunk("doc-1", "evidence"));
        }

        @Override
        public List<KnowledgeChunk> retrieve(String query) {
            return List.of();
        }
    }

    private static final class EmptyMemoryProvider
            extends InterviewMemoryContextProvider {

        private EmptyMemoryProvider() {
            super(
                    new InMemoryShortTermMemoryStore(3),
                    new LongTermMemoryRepository() {
                        @Override
                        public List<LongTermMemory> findRelevant(
                                String userId,
                                SemanticQuery query) {
                            return List.of();
                        }

                        @Override
                        public void save(
                                String userId,
                                String conversationId,
                                LongTermMemoryCandidate candidate) {
                        }
                    },
                    input -> new float[] {0.1f});
        }
    }

    private static final class TokenUserContext
            implements AuthenticatedUserContext {

        @Override
        public String requireUserId(HttpServletRequest request) {
            if (TOKEN.equals(request.getHeader("Authorization"))) {
                return "user-42";
            }
            throw new UnauthenticatedUserException(
                    "invalid authentication token");
        }
    }
}
