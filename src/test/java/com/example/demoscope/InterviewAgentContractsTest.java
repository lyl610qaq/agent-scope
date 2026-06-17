package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InterviewAgentContractsTest {

    @Test
    void routerDecisionValidatesConfidenceAndText() {
        RouterDecision decision = new RouterDecision(
                InterviewAgentName.JAVA_SKILL,
                "Java skill depth is needed",
                0.8,
                "collections internals",
                List.of("doc-1"));

        assertEquals(InterviewAgentName.JAVA_SKILL, decision.nextAgent());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RouterDecision(
                        InterviewAgentName.PROJECT,
                        " ",
                        0.8,
                        "focus",
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RouterDecision(
                        InterviewAgentName.PROJECT,
                        "reason",
                        1.1,
                        "focus",
                        List.of()));
    }

    @Test
    void ragQueryPlanRequiresAtLeastOneBoundedQuery() {
        RagQueryPlan plan = new RagQueryPlan(List.of(
                new RagQueryPlan.Query(
                        "Java HashMap resize",
                        6,
                        List.of("java"),
                        "support a question",
                        "technical_reference")));

        assertEquals(1, plan.queries().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RagQueryPlan(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RagQueryPlan(List.of(
                        new RagQueryPlan.Query(
                                "query",
                                0,
                                List.of(),
                                "purpose",
                                "technical_reference"))));
    }

    @Test
    void agentOutputAllowsExactlyOnePayloadForItsType() {
        InterviewAiContracts.GeneratedQuestion question =
                new InterviewAiContracts.GeneratedQuestion(
                        "Explain HashMap",
                        List.of("JAVA"),
                        List.of("doc-1"));

        InterviewAgentOutput output = InterviewAgentOutput.question(
                InterviewAgentName.JAVA_SKILL,
                question,
                List.of("doc-1"));

        assertEquals(InterviewAgentOutput.Type.QUESTION, output.type());
        assertThrows(
                NullPointerException.class,
                () -> InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        null,
                        List.of()));
    }

    @Test
    void memoryWriteDecisionCopiesNonBlankWrites() {
        MemoryWriteDecision decision = new MemoryWriteDecision(
                List.of("candidate needs hashmap follow-up"),
                List.of("candidate prefers concise feedback"),
                "useful interview continuity");

        assertEquals(1, decision.shortTermWrites().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemoryWriteDecision(
                        List.of(" "),
                        List.of(),
                        "reason"));
    }

    @Test
    void taskFactoriesSetAllowedAgentsAndDefaults() {
        InterviewSnapshot snapshot = snapshot();
        InterviewAgentTask questionTask =
                InterviewAgentTask.generateMainQuestion(snapshot, 2);
        InterviewAgentTask reportTask = InterviewAgentTask.generateReport(
                snapshot);

        assertEquals(
                InterviewAgentTask.Type.GENERATE_MAIN_QUESTION,
                questionTask.type());
        assertTrue(questionTask.allowedAgents().contains(
                InterviewAgentName.INTERVIEWER));
        assertEquals(InterviewAgentName.JAVA_SKILL, questionTask.defaultAgent());
        assertEquals(Set.of(InterviewAgentName.SCORE), reportTask.allowedAgents());
        assertEquals(InterviewAgentName.SCORE, reportTask.defaultAgent());
    }

    private InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000601");
        InterviewSession session = new InterviewSession(
                interviewId,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.QUESTION_GENERATION_PENDING,
                1,
                null,
                0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        return new InterviewSnapshot(session, List.of(), List.of(), null);
    }
}
