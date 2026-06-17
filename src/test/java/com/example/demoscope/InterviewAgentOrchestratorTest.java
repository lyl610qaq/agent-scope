package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewAgentOrchestratorTest {

    private FakeRouter router;
    private FakePlanner planner;
    private FakeEvidenceProvider evidenceProvider;
    private FakeMemoryProvider memoryProvider;
    private FakeMemoryManager memoryManager;
    private FakeMemoryWriter memoryWriter;
    private Map<InterviewAgentName, FakeTarget> targets;
    private InterviewAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        router = new FakeRouter();
        planner = new FakePlanner();
        evidenceProvider = new FakeEvidenceProvider();
        memoryProvider = new FakeMemoryProvider();
        memoryManager = new FakeMemoryManager();
        memoryWriter = new FakeMemoryWriter();
        targets = new EnumMap<>(InterviewAgentName.class);
        for (InterviewAgentName name : InterviewAgentName.values()) {
            targets.put(name, new FakeTarget(name));
        }
        orchestrator = new InterviewAgentOrchestrator(
                router,
                planner,
                evidenceProvider,
                memoryProvider,
                memoryManager,
                memoryWriter,
                List.copyOf(targets.values()),
                6);
    }

    @Test
    void routesQuestionTaskToSelectedTargetWithPlannedEvidenceAndMemoryWrite() {
        router.nextAgent = InterviewAgentName.PROJECT;
        targets.get(InterviewAgentName.PROJECT).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.PROJECT,
                        generated("Project question"),
                        List.of("doc-1")));

        InterviewAiContracts.GeneratedQuestion question =
                orchestrator.generateQuestion(snapshot(), 1);

        assertEquals("Project question", question.question());
        assertEquals(InterviewAgentName.PROJECT, targets.get(InterviewAgentName.PROJECT).lastContext.routerDecision().nextAgent());
        assertEquals(List.of("doc-1"), targets.get(InterviewAgentName.PROJECT).lastContext.ragEvidence().stream()
                .map(KnowledgeChunk::source)
                .toList());
        assertEquals(1, memoryWriter.writes.size());
    }

    @Test
    void routerFailureFallsBackToTaskDefaultAgent() {
        router.fail = true;
        targets.get(InterviewAgentName.JAVA_SKILL).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        generated("Java question"),
                        List.of()));

        InterviewAiContracts.GeneratedQuestion question =
                orchestrator.generateQuestion(snapshot(), 1);

        assertEquals("Java question", question.question());
        assertEquals(1, targets.get(InterviewAgentName.JAVA_SKILL).calls);
    }

    @Test
    void illegalRouterTargetFallsBackToTaskDefaultAgent() {
        router.nextAgent = InterviewAgentName.SCORE;
        targets.get(InterviewAgentName.JAVA_SKILL).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        generated("Java question"),
                        List.of()));

        orchestrator.generateQuestion(snapshot(), 1);

        assertEquals(1, targets.get(InterviewAgentName.JAVA_SKILL).calls);
        assertEquals(0, targets.get(InterviewAgentName.SCORE).calls);
    }

    @Test
    void plannerFailureUsesFallbackEvidenceRetrieval() {
        planner.fail = true;
        targets.get(InterviewAgentName.JAVA_SKILL).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        generated("Fallback evidence question"),
                        List.of()));

        orchestrator.generateQuestion(snapshot(), 1);

        assertEquals(1, evidenceProvider.stringQueries.size());
        assertEquals(0, evidenceProvider.plannedCalls);
    }

    @Test
    void targetFailurePropagatesAndDoesNotWriteMemory() {
        targets.get(InterviewAgentName.JAVA_SKILL).fail = true;

        assertThrows(
                IllegalStateException.class,
                () -> orchestrator.generateQuestion(snapshot(), 1));
        assertEquals(0, memoryWriter.writes.size());
    }

    @Test
    void memoryManagerFailureDoesNotBlockSuccessfulTarget() {
        memoryManager.fail = true;
        targets.get(InterviewAgentName.JAVA_SKILL).outputs.add(
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        generated("Question"),
                        List.of()));

        InterviewAiContracts.GeneratedQuestion question =
                orchestrator.generateQuestion(snapshot(), 1);

        assertEquals("Question", question.question());
        assertEquals(0, memoryWriter.writes.size());
    }

    @Test
    void reportTaskAllowsOnlyScoreAgent() {
        router.nextAgent = InterviewAgentName.PROJECT;
        targets.get(InterviewAgentName.SCORE).outputs.add(
                InterviewAgentOutput.report(
                        InterviewAgentName.SCORE,
                        report(),
                        List.of()));

        InterviewAiContracts.ReportDraft report =
                orchestrator.generateReport(snapshot());

        assertEquals(80, report.overallScore());
        assertEquals(1, targets.get(InterviewAgentName.SCORE).calls);
        assertEquals(0, targets.get(InterviewAgentName.PROJECT).calls);
    }

    private InterviewAiContracts.GeneratedQuestion generated(String text) {
        return new InterviewAiContracts.GeneratedQuestion(
                text,
                List.of("JAVA"),
                List.of());
    }

    private InterviewAiContracts.ReportDraft report() {
        return new InterviewAiContracts.ReportDraft(
                80,
                new InterviewAiContracts.ScoreBreakdown(80, 80, 80, 80, 80, 80),
                List.of("clear"),
                List.of("depth"),
                List.of("practice"));
    }

    private InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000901");
        UUID questionId = UUID.fromString(
                "00000000-0000-0000-0000-000000000902");
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
                InterviewSession.Status.IN_PROGRESS,
                1,
                questionId,
                0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        return new InterviewSnapshot(session, List.of(question), List.of(), null);
    }

    private static final class FakeRouter implements InterviewRouterAgent {

        private InterviewAgentName nextAgent = InterviewAgentName.JAVA_SKILL;
        private boolean fail;

        @Override
        public RouterDecision route(AgentPromptContext context) {
            if (fail) {
                throw new IllegalStateException("router failed");
            }
            return new RouterDecision(
                    nextAgent,
                    "route",
                    0.8,
                    "focus",
                    List.of());
        }
    }

    private static final class FakePlanner implements InterviewRagPlannerAgent {

        private boolean fail;

        @Override
        public RagQueryPlan plan(AgentPromptContext context) {
            if (fail) {
                throw new IllegalStateException("planner failed");
            }
            return new RagQueryPlan(List.of(
                    new RagQueryPlan.Query(
                            "planned query",
                            1,
                            List.of("java"),
                            "purpose",
                            "reference")));
        }
    }

    private static final class FakeEvidenceProvider
            extends InterviewEvidenceProvider {

        private int plannedCalls;
        private final List<String> stringQueries = new ArrayList<>();

        private FakeEvidenceProvider() {
            super(input -> new float[] {0.1f}, query -> List.of());
        }

        @Override
        public List<KnowledgeChunk> retrieve(RagQueryPlan plan, int maxEvidence) {
            plannedCalls++;
            return List.of(new KnowledgeChunk("doc-1", "evidence"));
        }

        @Override
        public List<KnowledgeChunk> retrieve(String query) {
            stringQueries.add(query);
            return List.of(new KnowledgeChunk("fallback", "evidence"));
        }
    }

    private static final class FakeMemoryProvider
            extends InterviewMemoryContextProvider {

        private FakeMemoryProvider() {
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

    private static final class FakeMemoryManager
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

    private static final class FakeMemoryWriter implements InterviewMemoryWriter {

        private final List<MemoryWriteDecision> writes = new ArrayList<>();

        @Override
        public void write(InterviewSnapshot snapshot, MemoryWriteDecision decision) {
            writes.add(decision);
        }
    }

    private static final class FakeTarget implements InterviewTargetAgent {

        private final InterviewAgentName name;
        private final Queue<InterviewAgentOutput> outputs = new ArrayDeque<>();
        private boolean fail;
        private int calls;
        private AgentPromptContext lastContext;

        private FakeTarget(InterviewAgentName name) {
            this.name = name;
        }

        @Override
        public InterviewAgentName name() {
            return name;
        }

        @Override
        public InterviewAgentOutput run(AgentPromptContext context) {
            calls++;
            lastContext = context;
            if (fail) {
                throw new IllegalStateException("target failed");
            }
            return outputs.remove();
        }
    }
}
