package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelInterviewAgentTest {

    @Test
    void routerPromptIncludesAllowedAgentsAndReturnsDecision() {
        CapturingModel model = new CapturingModel("""
                {"nextAgent":"JAVA_SKILL","reason":"needs Java depth",
                "confidence":0.82,"suggestedFocus":"collections",
                "usedEvidenceIds":["doc-1"]}
                """);
        InterviewRouterAgent router = new ModelInterviewRouterAgent(
                new InterviewAiJsonClient(model, new ObjectMapper()));

        RouterDecision decision = router.route(context());

        assertEquals(InterviewAgentName.JAVA_SKILL, decision.nextAgent());
        assertTrue(model.lastSystemPrompt.contains("Router"));
        assertTrue(model.lastPrompt.contains("JAVA_SKILL"));
        assertTrue(model.lastPrompt.contains("INTERVIEWER"));
    }

    @Test
    void plannerPromptIncludesTaskAndReturnsQueries() {
        CapturingModel model = new CapturingModel("""
                {"queries":[{"query":"HashMap resize","topK":3,
                "filters":["java"],"purpose":"support",
                "expectedEvidenceType":"technical_reference"}]}
                """);
        InterviewRagPlannerAgent planner = new ModelInterviewRagPlannerAgent(
                new InterviewAiJsonClient(model, new ObjectMapper()));

        RagQueryPlan plan = planner.plan(context());

        assertEquals("HashMap resize", plan.queries().get(0).query());
        assertTrue(model.lastPrompt.contains("GENERATE_MAIN_QUESTION"));
        assertTrue(model.lastPrompt.contains("Transcript"));
    }

    @Test
    void javaSkillAgentReturnsQuestionOutput() {
        CapturingModel model = new CapturingModel("""
                {"agentName":"JAVA_SKILL","type":"QUESTION",
                "generatedQuestion":{"question":"Explain HashMap",
                "skillTags":["JAVA"],"evidenceIds":["doc-1"]},
                "usedEvidenceIds":["doc-1"]}
                """);
        InterviewTargetAgent agent = new ModelJavaSkillAgent(
                new InterviewAiJsonClient(model, new ObjectMapper()));

        InterviewAgentOutput output = agent.run(contextWithEvidence());

        assertEquals(InterviewAgentName.JAVA_SKILL, agent.name());
        assertEquals("Explain HashMap", output.generatedQuestion().question());
        assertTrue(model.lastPrompt.contains("HashMap evidence"));
    }

    @Test
    void scoreAgentReturnsReportOutput() {
        CapturingModel model = new CapturingModel("""
                {"agentName":"SCORE","type":"SCORE_REPORT",
                "reportDraft":{"overallScore":80,
                "scores":{"javaFundamentals":80,"concurrency":75,"jvm":70,
                "spring":82,"database":78,"engineering":85},
                "strengths":["clear"],"weaknesses":["depth"],
                "improvementSuggestions":["practice"]},
                "usedEvidenceIds":["doc-1"]}
                """);
        InterviewTargetAgent agent = new ModelScoreAgent(
                new InterviewAiJsonClient(model, new ObjectMapper()));

        InterviewAgentOutput output = agent.run(reportContext());

        assertEquals(InterviewAgentName.SCORE, agent.name());
        assertEquals(80, output.reportDraft().overallScore());
        assertTrue(model.lastPrompt.contains("candidate answer"));
    }

    @Test
    void memoryManagerReturnsWriteDecision() {
        CapturingModel model = new CapturingModel("""
                {"shortTermWrites":["needs follow-up"],
                "longTermWrites":["prefers concise feedback"],
                "reason":"interview continuity"}
                """);
        InterviewMemoryManagerAgent memoryManager =
                new ModelInterviewMemoryManagerAgent(
                        new InterviewAiJsonClient(model, new ObjectMapper()));

        MemoryWriteDecision decision = memoryManager.decide(
                contextWithEvidence(),
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        new InterviewAiContracts.GeneratedQuestion(
                                "Explain HashMap",
                                List.of("JAVA"),
                                List.of("doc-1")),
                        List.of("doc-1")));

        assertEquals(1, decision.shortTermWrites().size());
        assertTrue(model.lastSystemPrompt.contains("memory"));
        assertTrue(model.lastPrompt.contains("JAVA_SKILL"));
    }

    private AgentPromptContext context() {
        return new AgentPromptContext(
                snapshot(false),
                InterviewAgentTask.generateMainQuestion(snapshot(false), 1),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private AgentPromptContext contextWithEvidence() {
        return context().withRagEvidence(List.of(
                new KnowledgeChunk("doc-1", "HashMap evidence")));
    }

    private AgentPromptContext reportContext() {
        InterviewSnapshot snapshot = snapshot(true);
        return new AgentPromptContext(
                snapshot,
                InterviewAgentTask.generateReport(snapshot),
                null,
                null,
                List.of(),
                List.of(),
                List.of(new KnowledgeChunk("doc-1", "score evidence")),
                new RouterDecision(
                        InterviewAgentName.SCORE,
                        "score now",
                        1.0,
                        "overall score",
                        List.of("doc-1")));
    }

    private InterviewSnapshot snapshot(boolean answered) {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000701");
        UUID questionId = UUID.fromString(
                "00000000-0000-0000-0000-000000000702");
        InterviewQuestion question = InterviewQuestion.main(
                questionId,
                interviewId,
                1,
                "Explain HashMap",
                List.of("JAVA"),
                List.of("doc-1"),
                Instant.EPOCH);
        InterviewSession session = new InterviewSession(
                interviewId,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                answered
                        ? InterviewSession.Status.SCORING_PENDING
                        : InterviewSession.Status.IN_PROGRESS,
                1,
                questionId,
                answered ? 1 : 0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        if (!answered) {
            return new InterviewSnapshot(session, List.of(question), List.of(), null);
        }
        InterviewAnswer answer = new InterviewAnswer(
                UUID.fromString("00000000-0000-0000-0000-000000000703"),
                interviewId,
                questionId,
                "candidate answer",
                "internal evaluation",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                "complete",
                Instant.EPOCH);
        return new InterviewSnapshot(session, List.of(question), List.of(answer), null);
    }

    private static final class CapturingModel implements ChatTextModel {

        private final List<String> responses = new ArrayList<>();
        private String lastSystemPrompt;
        private String lastPrompt;

        private CapturingModel(String response) {
            responses.add(response);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            lastSystemPrompt = systemPrompt;
            lastPrompt = userPrompt;
            return responses.remove(0);
        }
    }
}
