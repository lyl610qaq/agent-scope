# Interview Agentic Orchestration Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a multi-agent orchestration layer behind the existing authenticated `/api/interviews` Java interview APIs without changing `/api/chat`.

**Architecture:** Keep `InterviewService` as the authoritative lifecycle state machine. Replace the production AI adapter path with `InterviewAgentOrchestrator`, which calls Router, RAG Planner, target agents, MemoryManager, RAG retrieval, and memory write boundaries before returning the existing `InterviewAiContracts` results. Auxiliary agent failures degrade; target agent failures propagate to the current retryable interview states.

**Tech Stack:** Java 17, Spring Boot 4.0.6, Spring MVC, Spring JDBC, Jackson, JUnit 5, Mockito, MockMvc, existing `ChatTextModel`, `EmbeddingClient`, `KnowledgeRetriever`, and memory ports.

---

## Reference

Approved spec:

`docs/superpowers/specs/2026-06-16-interview-agentic-orchestration-closure-design.md`

Current production entry points to preserve:

- `src/main/java/com/example/demoscope/InterviewController.java`
- `src/main/java/com/example/demoscope/InterviewService.java`
- `src/main/java/com/example/demoscope/InterviewRepository.java`
- `src/main/java/com/example/demoscope/InterviewConfig.java`

Current simple AI adapters can remain as compatibility code and focused prompt tests:

- `src/main/java/com/example/demoscope/ModelInterviewQuestionGenerator.java`
- `src/main/java/com/example/demoscope/ModelInterviewAnswerEvaluator.java`
- `src/main/java/com/example/demoscope/ModelInterviewReportGenerator.java`

## File Structure

Create these production files:

- `src/main/java/com/example/demoscope/InterviewAgentName.java` - target agent enum.
- `src/main/java/com/example/demoscope/InterviewAgentTask.java` - task type, allowed agents, defaults, and task-specific values.
- `src/main/java/com/example/demoscope/AgentPromptContext.java` - shared context passed to every agent.
- `src/main/java/com/example/demoscope/RouterDecision.java` - strict Router JSON contract.
- `src/main/java/com/example/demoscope/RagQueryPlan.java` - strict Planner JSON contract.
- `src/main/java/com/example/demoscope/InterviewAgentOutput.java` - strict target agent output wrapper.
- `src/main/java/com/example/demoscope/MemoryWriteDecision.java` - strict MemoryManager JSON contract.
- `src/main/java/com/example/demoscope/InterviewRouterAgent.java` - Router interface.
- `src/main/java/com/example/demoscope/InterviewRagPlannerAgent.java` - Planner interface.
- `src/main/java/com/example/demoscope/InterviewTargetAgent.java` - target agent interface.
- `src/main/java/com/example/demoscope/InterviewMemoryManagerAgent.java` - MemoryManager interface.
- `src/main/java/com/example/demoscope/InterviewMemoryContextProvider.java` - short-term and long-term interview memory reads.
- `src/main/java/com/example/demoscope/InterviewMemoryWriter.java` - applies MemoryManager write suggestions.
- `src/main/java/com/example/demoscope/DefaultInterviewMemoryWriter.java` - writes interview memory through existing memory stores and policy.
- `src/main/java/com/example/demoscope/InterviewTranscriptRenderer.java` - transcript rendering shared by model agents.
- `src/main/java/com/example/demoscope/ModelInterviewRouterAgent.java` - model-backed Router.
- `src/main/java/com/example/demoscope/ModelInterviewRagPlannerAgent.java` - model-backed Planner.
- `src/main/java/com/example/demoscope/ModelInterviewerAgent.java` - general interviewer target agent.
- `src/main/java/com/example/demoscope/ModelProjectAgent.java` - project-depth target agent.
- `src/main/java/com/example/demoscope/ModelJavaSkillAgent.java` - Java-skill target agent.
- `src/main/java/com/example/demoscope/ModelScoreAgent.java` - scoring target agent.
- `src/main/java/com/example/demoscope/InterviewAgentOrchestrator.java` - orchestration core.
- `src/main/java/com/example/demoscope/AgenticInterviewQuestionGenerator.java` - `InterviewQuestionGenerator` adapter.
- `src/main/java/com/example/demoscope/AgenticInterviewAnswerEvaluator.java` - `InterviewAnswerEvaluator` adapter.
- `src/main/java/com/example/demoscope/AgenticInterviewReportGenerator.java` - `InterviewReportGenerator` adapter.

Modify these production files:

- `src/main/java/com/example/demoscope/InterviewEvidenceProvider.java` - add planned-query retrieval while keeping existing string retrieval.
- `src/main/java/com/example/demoscope/InterviewConfig.java` - wire the agentic production path.

Create these test files:

- `src/test/java/com/example/demoscope/InterviewAgentContractsTest.java`
- `src/test/java/com/example/demoscope/ModelInterviewAgentTest.java`
- `src/test/java/com/example/demoscope/InterviewEvidenceProviderPlanTest.java`
- `src/test/java/com/example/demoscope/InterviewMemorySupportTest.java`
- `src/test/java/com/example/demoscope/InterviewAgentOrchestratorTest.java`
- `src/test/java/com/example/demoscope/AgenticAuthenticatedInterviewFlowTest.java`

Modify these test files:

- `src/test/java/com/example/demoscope/InterviewConfigTest.java`
- optionally `src/test/java/com/example/demoscope/AuthenticatedInterviewFlowTest.java` only if the new flow test replaces duplicated setup.

## Task 1: Add Agentic Contract Types

**Files:**

- Create: `src/main/java/com/example/demoscope/InterviewAgentName.java`
- Create: `src/main/java/com/example/demoscope/InterviewAgentTask.java`
- Create: `src/main/java/com/example/demoscope/AgentPromptContext.java`
- Create: `src/main/java/com/example/demoscope/RouterDecision.java`
- Create: `src/main/java/com/example/demoscope/RagQueryPlan.java`
- Create: `src/main/java/com/example/demoscope/InterviewAgentOutput.java`
- Create: `src/main/java/com/example/demoscope/MemoryWriteDecision.java`
- Test: `src/test/java/com/example/demoscope/InterviewAgentContractsTest.java`

- [ ] **Step 1: Write failing contract validation tests**

Create `src/test/java/com/example/demoscope/InterviewAgentContractsTest.java`:

```java
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
        InterviewAgentTask reportTask = InterviewAgentTask.generateReport(snapshot);

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
```

- [ ] **Step 2: Run the contract tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewAgentContractsTest' test
```

Expected: compilation fails because `RouterDecision`, `RagQueryPlan`,
`InterviewAgentOutput`, `MemoryWriteDecision`, `InterviewAgentTask`, and
`InterviewAgentName` do not exist.

- [ ] **Step 3: Implement the contract records**

Create `InterviewAgentName.java`:

```java
package com.example.demoscope;

public enum InterviewAgentName {
    INTERVIEWER,
    PROJECT,
    JAVA_SKILL,
    SCORE
}
```

Create `InterviewAgentTask.java`:

```java
package com.example.demoscope;

import java.util.Objects;
import java.util.Set;

public record InterviewAgentTask(
        Type type,
        Set<InterviewAgentName> allowedAgents,
        InterviewAgentName defaultAgent,
        int mainQuestionNumber,
        InterviewQuestion currentQuestion,
        String candidateAnswer) {

    public enum Type {
        GENERATE_MAIN_QUESTION,
        EVALUATE_ANSWER,
        GENERATE_REPORT
    }

    public InterviewAgentTask {
        Objects.requireNonNull(type, "type");
        allowedAgents = Set.copyOf(
                Objects.requireNonNull(allowedAgents, "allowedAgents"));
        if (allowedAgents.isEmpty()) {
            throw new IllegalArgumentException("allowedAgents must not be empty");
        }
        Objects.requireNonNull(defaultAgent, "defaultAgent");
        if (!allowedAgents.contains(defaultAgent)) {
            throw new IllegalArgumentException(
                    "defaultAgent must be in allowedAgents");
        }
        if (mainQuestionNumber < 0 || mainQuestionNumber > 5) {
            throw new IllegalArgumentException(
                    "mainQuestionNumber must be between 0 and 5");
        }
        if (type == Type.EVALUATE_ANSWER) {
            Objects.requireNonNull(currentQuestion, "currentQuestion");
            candidateAnswer = requireText(candidateAnswer, "candidateAnswer");
        }
    }

    public static InterviewAgentTask generateMainQuestion(
            InterviewSnapshot snapshot,
            int mainQuestionNumber) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new InterviewAgentTask(
                Type.GENERATE_MAIN_QUESTION,
                Set.of(
                        InterviewAgentName.INTERVIEWER,
                        InterviewAgentName.PROJECT,
                        InterviewAgentName.JAVA_SKILL),
                InterviewAgentName.JAVA_SKILL,
                mainQuestionNumber,
                null,
                null);
    }

    public static InterviewAgentTask evaluateAnswer(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new InterviewAgentTask(
                Type.EVALUATE_ANSWER,
                Set.of(
                        InterviewAgentName.INTERVIEWER,
                        InterviewAgentName.PROJECT,
                        InterviewAgentName.JAVA_SKILL),
                InterviewAgentName.INTERVIEWER,
                question.mainQuestionNumber(),
                question,
                candidateAnswer);
    }

    public static InterviewAgentTask generateReport(
            InterviewSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new InterviewAgentTask(
                Type.GENERATE_REPORT,
                Set.of(InterviewAgentName.SCORE),
                InterviewAgentName.SCORE,
                snapshot.session().mainQuestionCount(),
                null,
                null);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
```

Create `AgentPromptContext.java`:

```java
package com.example.demoscope;

import java.util.List;
import java.util.Objects;

public record AgentPromptContext(
        InterviewSnapshot snapshot,
        InterviewAgentTask task,
        InterviewQuestion currentQuestion,
        String candidateAnswer,
        List<MemoryTurn> shortTermMemory,
        List<LongTermMemory> longTermMemory,
        List<KnowledgeChunk> ragEvidence,
        RouterDecision routerDecision) {

    public AgentPromptContext {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(task, "task");
        shortTermMemory = List.copyOf(
                Objects.requireNonNull(shortTermMemory, "shortTermMemory"));
        longTermMemory = List.copyOf(
                Objects.requireNonNull(longTermMemory, "longTermMemory"));
        ragEvidence = List.copyOf(
                Objects.requireNonNull(ragEvidence, "ragEvidence"));
    }

    public AgentPromptContext withRouterDecision(RouterDecision decision) {
        return new AgentPromptContext(
                snapshot,
                task,
                currentQuestion,
                candidateAnswer,
                shortTermMemory,
                longTermMemory,
                ragEvidence,
                decision);
    }

    public AgentPromptContext withRagEvidence(List<KnowledgeChunk> evidence) {
        return new AgentPromptContext(
                snapshot,
                task,
                currentQuestion,
                candidateAnswer,
                shortTermMemory,
                longTermMemory,
                evidence,
                routerDecision);
    }
}
```

Create `RouterDecision.java`, `RagQueryPlan.java`,
`InterviewAgentOutput.java`, and `MemoryWriteDecision.java` with validation
matching the tests:

```java
package com.example.demoscope;

import java.util.List;
import java.util.Objects;

public record RouterDecision(
        InterviewAgentName nextAgent,
        String reason,
        double confidence,
        String suggestedFocus,
        List<String> usedEvidenceIds) {

    public RouterDecision {
        Objects.requireNonNull(nextAgent, "nextAgent");
        reason = requireText(reason, "reason");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be between 0.0 and 1.0");
        }
        suggestedFocus = requireText(suggestedFocus, "suggestedFocus");
        usedEvidenceIds = copyTextList(usedEvidenceIds, "usedEvidenceIds");
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static List<String> copyTextList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    name + " must contain non-blank values");
        }
        return copy;
    }
}
```

```java
package com.example.demoscope;

import java.util.List;
import java.util.Objects;

public record RagQueryPlan(List<Query> queries) {

    public RagQueryPlan {
        queries = List.copyOf(Objects.requireNonNull(queries, "queries"));
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("queries must not be empty");
        }
    }

    public record Query(
            String query,
            int topK,
            List<String> filters,
            String purpose,
            String expectedEvidenceType) {

        public Query {
            query = RouterDecision.requireText(query, "query");
            if (topK < 1 || topK > 20) {
                throw new IllegalArgumentException(
                        "topK must be between 1 and 20");
            }
            filters = RouterDecision.copyTextList(filters, "filters");
            purpose = RouterDecision.requireText(purpose, "purpose");
            expectedEvidenceType = RouterDecision.requireText(
                    expectedEvidenceType,
                    "expectedEvidenceType");
        }
    }
}
```

```java
package com.example.demoscope;

import java.util.List;
import java.util.Objects;

public record InterviewAgentOutput(
        InterviewAgentName agentName,
        Type type,
        InterviewAiContracts.GeneratedQuestion generatedQuestion,
        InterviewAiContracts.AnswerEvaluation answerEvaluation,
        InterviewAiContracts.ReportDraft reportDraft,
        List<String> usedEvidenceIds) {

    public enum Type {
        QUESTION,
        ANSWER_EVALUATION,
        SCORE_REPORT
    }

    public InterviewAgentOutput {
        Objects.requireNonNull(agentName, "agentName");
        Objects.requireNonNull(type, "type");
        usedEvidenceIds = RouterDecision.copyTextList(
                usedEvidenceIds,
                "usedEvidenceIds");
        int payloads = (generatedQuestion == null ? 0 : 1)
                + (answerEvaluation == null ? 0 : 1)
                + (reportDraft == null ? 0 : 1);
        if (payloads != 1) {
            throw new IllegalArgumentException(
                    "exactly one agent output payload is required");
        }
        if (type == Type.QUESTION && generatedQuestion == null) {
            throw new IllegalArgumentException(
                    "QUESTION output requires generatedQuestion");
        }
        if (type == Type.ANSWER_EVALUATION && answerEvaluation == null) {
            throw new IllegalArgumentException(
                    "ANSWER_EVALUATION output requires answerEvaluation");
        }
        if (type == Type.SCORE_REPORT && reportDraft == null) {
            throw new IllegalArgumentException(
                    "SCORE_REPORT output requires reportDraft");
        }
    }

    public static InterviewAgentOutput question(
            InterviewAgentName agentName,
            InterviewAiContracts.GeneratedQuestion question,
            List<String> usedEvidenceIds) {
        return new InterviewAgentOutput(
                agentName,
                Type.QUESTION,
                Objects.requireNonNull(question, "question"),
                null,
                null,
                usedEvidenceIds);
    }

    public static InterviewAgentOutput answerEvaluation(
            InterviewAgentName agentName,
            InterviewAiContracts.AnswerEvaluation evaluation,
            List<String> usedEvidenceIds) {
        return new InterviewAgentOutput(
                agentName,
                Type.ANSWER_EVALUATION,
                null,
                Objects.requireNonNull(evaluation, "evaluation"),
                null,
                usedEvidenceIds);
    }

    public static InterviewAgentOutput report(
            InterviewAgentName agentName,
            InterviewAiContracts.ReportDraft report,
            List<String> usedEvidenceIds) {
        return new InterviewAgentOutput(
                agentName,
                Type.SCORE_REPORT,
                null,
                null,
                Objects.requireNonNull(report, "report"),
                usedEvidenceIds);
    }
}
```

```java
package com.example.demoscope;

import java.util.List;

public record MemoryWriteDecision(
        List<String> shortTermWrites,
        List<String> longTermWrites,
        String reason) {

    public MemoryWriteDecision {
        shortTermWrites = RouterDecision.copyTextList(
                shortTermWrites,
                "shortTermWrites");
        longTermWrites = RouterDecision.copyTextList(
                longTermWrites,
                "longTermWrites");
        reason = RouterDecision.requireText(reason, "reason");
    }
}
```

- [ ] **Step 4: Run contract tests and verify GREEN**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewAgentContractsTest' test
```

Expected: `InterviewAgentContractsTest` passes.

- [ ] **Step 5: Commit contracts**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewAgentName.java src/main/java/com/example/demoscope/InterviewAgentTask.java src/main/java/com/example/demoscope/AgentPromptContext.java src/main/java/com/example/demoscope/RouterDecision.java src/main/java/com/example/demoscope/RagQueryPlan.java src/main/java/com/example/demoscope/InterviewAgentOutput.java src/main/java/com/example/demoscope/MemoryWriteDecision.java src/test/java/com/example/demoscope/InterviewAgentContractsTest.java
git commit -m "feat: add interview agent contracts"
```

## Task 2: Add Model Agent Interfaces and Prompt Implementations

**Files:**

- Create: `src/main/java/com/example/demoscope/InterviewRouterAgent.java`
- Create: `src/main/java/com/example/demoscope/InterviewRagPlannerAgent.java`
- Create: `src/main/java/com/example/demoscope/InterviewTargetAgent.java`
- Create: `src/main/java/com/example/demoscope/InterviewMemoryManagerAgent.java`
- Create: `src/main/java/com/example/demoscope/InterviewTranscriptRenderer.java`
- Create: `src/main/java/com/example/demoscope/ModelInterviewRouterAgent.java`
- Create: `src/main/java/com/example/demoscope/ModelInterviewRagPlannerAgent.java`
- Create: `src/main/java/com/example/demoscope/ModelInterviewerAgent.java`
- Create: `src/main/java/com/example/demoscope/ModelProjectAgent.java`
- Create: `src/main/java/com/example/demoscope/ModelJavaSkillAgent.java`
- Create: `src/main/java/com/example/demoscope/ModelScoreAgent.java`
- Create: `src/main/java/com/example/demoscope/ModelInterviewMemoryManagerAgent.java`
- Test: `src/test/java/com/example/demoscope/ModelInterviewAgentTest.java`

- [ ] **Step 1: Write failing model agent prompt tests**

Create `src/test/java/com/example/demoscope/ModelInterviewAgentTest.java`:

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelInterviewAgentTest {

    @Test
    void routerPromptIncludesAllowedAgentsAndReturnsDecision() {
        CapturingModel model = new CapturingModel("""
                {
                  "nextAgent":"PROJECT",
                  "reason":"project depth is useful",
                  "confidence":0.77,
                  "suggestedFocus":"ask about trade-offs",
                  "usedEvidenceIds":[]
                }
                """);
        ModelInterviewRouterAgent router = new ModelInterviewRouterAgent(
                new InterviewAiJsonClient(model, new ObjectMapper()));

        RouterDecision decision = router.route(
                context(),
                Set.of(InterviewAgentName.PROJECT, InterviewAgentName.JAVA_SKILL));

        assertEquals(InterviewAgentName.PROJECT, decision.nextAgent());
        assertTrue(model.lastPrompt.contains("PROJECT"));
        assertTrue(model.lastPrompt.contains("JAVA_SKILL"));
    }

    @Test
    void plannerPromptIncludesTaskAndReturnsQueries() {
        CapturingModel model = new CapturingModel("""
                {
                  "queries":[{
                    "query":"Java HashMap internals",
                    "topK":6,
                    "filters":["java"],
                    "purpose":"support next question",
                    "expectedEvidenceType":"technical_reference"
                  }]
                }
                """);
        ModelInterviewRagPlannerAgent planner =
                new ModelInterviewRagPlannerAgent(
                        new InterviewAiJsonClient(model, new ObjectMapper()));

        RagQueryPlan plan = planner.plan(context());

        assertEquals("Java HashMap internals", plan.queries().get(0).query());
        assertTrue(model.lastPrompt.contains("GENERATE_MAIN_QUESTION"));
    }

    @Test
    void javaSkillAgentReturnsQuestionOutput() {
        CapturingModel model = new CapturingModel("""
                {
                  "agentName":"JAVA_SKILL",
                  "type":"QUESTION",
                  "generatedQuestion":{
                    "question":"Explain HashMap resizing.",
                    "skillTags":["JAVA"],
                    "evidenceIds":["doc-1"]
                  },
                  "usedEvidenceIds":["doc-1"]
                }
                """);
        ModelJavaSkillAgent agent = new ModelJavaSkillAgent(
                new InterviewAiJsonClient(model, new ObjectMapper()));

        InterviewAgentOutput output = agent.run(contextWithEvidence());

        assertEquals(InterviewAgentName.JAVA_SKILL, output.agentName());
        assertEquals("Explain HashMap resizing.", output.generatedQuestion().question());
        assertTrue(model.lastPrompt.contains("HashMap evidence"));
    }

    @Test
    void scoreAgentReturnsReportOutput() {
        CapturingModel model = new CapturingModel("""
                {
                  "agentName":"SCORE",
                  "type":"SCORE_REPORT",
                  "reportDraft":{
                    "overallScore":80,
                    "scores":{
                      "javaFundamentals":82,
                      "concurrency":76,
                      "jvm":78,
                      "spring":79,
                      "database":77,
                      "engineering":81
                    },
                    "strengths":["clear basics"],
                    "weaknesses":["needs deeper JVM detail"],
                    "improvementSuggestions":["practice diagnostics"]
                  },
                  "usedEvidenceIds":[]
                }
                """);
        ModelScoreAgent agent = new ModelScoreAgent(
                new InterviewAiJsonClient(model, new ObjectMapper()));

        InterviewAgentOutput output = agent.run(reportContext());

        assertEquals(InterviewAgentOutput.Type.SCORE_REPORT, output.type());
        assertEquals(80, output.reportDraft().overallScore());
    }

    @Test
    void memoryManagerReturnsWriteDecision() {
        CapturingModel model = new CapturingModel("""
                {
                  "shortTermWrites":["candidate missed resize threshold"],
                  "longTermWrites":["candidate prefers concrete examples"],
                  "reason":"useful for later interview turns"
                }
                """);
        ModelInterviewMemoryManagerAgent agent =
                new ModelInterviewMemoryManagerAgent(
                        new InterviewAiJsonClient(model, new ObjectMapper()));

        MemoryWriteDecision decision = agent.decide(
                context(),
                InterviewAgentOutput.question(
                        InterviewAgentName.JAVA_SKILL,
                        new InterviewAiContracts.GeneratedQuestion(
                                "Explain HashMap",
                                List.of("JAVA"),
                                List.of()),
                        List.of()));

        assertEquals(1, decision.shortTermWrites().size());
        assertTrue(model.lastPrompt.contains("Memory write suggestions"));
    }

    private AgentPromptContext context() {
        InterviewSnapshot snapshot = snapshot();
        return new AgentPromptContext(
                snapshot,
                InterviewAgentTask.generateMainQuestion(snapshot, 2),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private AgentPromptContext contextWithEvidence() {
        AgentPromptContext context = context();
        return context.withRagEvidence(List.of(
                new KnowledgeChunk("doc-1", "HashMap evidence")));
    }

    private AgentPromptContext reportContext() {
        InterviewSnapshot snapshot = snapshot();
        return new AgentPromptContext(
                snapshot,
                InterviewAgentTask.generateReport(snapshot),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                new RouterDecision(
                        InterviewAgentName.SCORE,
                        "report needed",
                        1.0,
                        "score",
                        List.of()));
    }

    private InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000701");
        InterviewQuestion question = InterviewQuestion.main(
                UUID.fromString("00000000-0000-0000-0000-000000000702"),
                interviewId,
                1,
                "Explain HashMap.",
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
                question.id(),
                0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        return new InterviewSnapshot(session, List.of(question), List.of(), null);
    }

    private static final class CapturingModel implements ChatTextModel {

        private final List<String> responses = new ArrayList<>();
        private String lastPrompt;

        private CapturingModel(String response) {
            responses.add(response);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            lastPrompt = userPrompt;
            return responses.remove(0);
        }
    }
}
```

- [ ] **Step 2: Run model agent tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ModelInterviewAgentTest' test
```

Expected: compilation fails because the model agent interfaces and
implementations do not exist.

- [ ] **Step 3: Implement the agent interfaces and transcript renderer**

Create the four interfaces:

```java
package com.example.demoscope;

import java.util.Set;

public interface InterviewRouterAgent {

    RouterDecision route(
            AgentPromptContext context,
            Set<InterviewAgentName> allowedAgents);
}
```

```java
package com.example.demoscope;

public interface InterviewRagPlannerAgent {

    RagQueryPlan plan(AgentPromptContext context);
}
```

```java
package com.example.demoscope;

public interface InterviewTargetAgent {

    InterviewAgentName name();

    InterviewAgentOutput run(AgentPromptContext context);
}
```

```java
package com.example.demoscope;

public interface InterviewMemoryManagerAgent {

    MemoryWriteDecision decide(
            AgentPromptContext context,
            InterviewAgentOutput output);
}
```

Create `InterviewTranscriptRenderer.java`:

```java
package com.example.demoscope;

public final class InterviewTranscriptRenderer {

    private InterviewTranscriptRenderer() {
    }

    public static String transcript(InterviewSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        for (InterviewQuestion question : snapshot.questions()) {
            builder.append(question.type())
                    .append(' ')
                    .append(question.mainQuestionNumber())
                    .append('.')
                    .append(question.followUpNumber())
                    .append(": ")
                    .append(question.text())
                    .append('\n');
            snapshot.answerFor(question.id()).ifPresent(answer ->
                    builder.append("Answer: ")
                            .append(answer.answerText())
                            .append('\n')
                            .append("Internal evaluation: ")
                            .append(answer.internalEvaluation())
                            .append('\n'));
        }
        return builder.toString();
    }
}
```

- [ ] **Step 4: Implement model Router and Planner**

Create `ModelInterviewRouterAgent.java`:

```java
package com.example.demoscope;

import java.util.Set;

public class ModelInterviewRouterAgent implements InterviewRouterAgent {

    private static final String SYSTEM_PROMPT = """
            You are an interview routing agent.
            Return one JSON object only. Do not use markdown.
            Choose only one nextAgent from allowedAgents.
            Do not ask interview questions, score candidates, or summarize.
            Schema:
            {"nextAgent":"INTERVIEWER|PROJECT|JAVA_SKILL|SCORE",
            "reason":"non-blank","confidence":0.0,
            "suggestedFocus":"non-blank","usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewRouterAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public RouterDecision route(
            AgentPromptContext context,
            Set<InterviewAgentName> allowedAgents) {
        String prompt = """
                Task: %s
                Allowed agents: %s
                Direction: %s
                Difficulty: %s
                Main question number: %d
                Candidate answer present: %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                allowedAgents,
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.task().mainQuestionNumber(),
                context.candidateAnswer() != null,
                InterviewTranscriptRenderer.transcript(context.snapshot()));
        return aiClient.call(SYSTEM_PROMPT, prompt, RouterDecision.class);
    }
}
```

Create `ModelInterviewRagPlannerAgent.java`:

```java
package com.example.demoscope;

public class ModelInterviewRagPlannerAgent
        implements InterviewRagPlannerAgent {

    private static final String SYSTEM_PROMPT = """
            You are a retrieval planning agent for Java backend interviews.
            Return one JSON object only. Do not use markdown.
            Produce focused evidence queries. Do not answer the candidate.
            Schema:
            {"queries":[{"query":"non-blank","topK":6,
            "filters":["tag"],"purpose":"non-blank",
            "expectedEvidenceType":"non-blank"}]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewRagPlannerAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public RagQueryPlan plan(AgentPromptContext context) {
        String prompt = """
                Task: %s
                Suggested focus: %s
                Direction: %s
                Difficulty: %s
                Current question: %s
                Candidate answer present: %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                context.routerDecision() == null
                        ? context.task().defaultAgent()
                        : context.routerDecision().suggestedFocus(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.currentQuestion() == null
                        ? ""
                        : context.currentQuestion().text(),
                context.candidateAnswer() != null,
                InterviewTranscriptRenderer.transcript(context.snapshot()));
        return aiClient.call(SYSTEM_PROMPT, prompt, RagQueryPlan.class);
    }
}
```

- [ ] **Step 5: Implement the model target agents and MemoryManager**

Create `ModelJavaSkillAgent.java` with this pattern:

```java
package com.example.demoscope;

public class ModelJavaSkillAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a Java skill interview agent.
            Return one JSON object only. Do not use markdown.
            Focus on Java fundamentals, JVM, concurrency, collections, and Spring.
            Return an InterviewAgentOutput whose agentName is JAVA_SKILL.
            For question tasks, type must be QUESTION.
            For answer evaluation tasks, type must be ANSWER_EVALUATION.
            Schema for QUESTION:
            {"agentName":"JAVA_SKILL","type":"QUESTION",
            "generatedQuestion":{"question":"non-blank",
            "skillTags":["tag"],"evidenceIds":["id"]},
            "usedEvidenceIds":["id"]}
            Schema for ANSWER_EVALUATION:
            {"agentName":"JAVA_SKILL","type":"ANSWER_EVALUATION",
            "answerEvaluation":{"internalEvaluation":"non-blank",
            "abilityTags":["tag"],"decision":"FOLLOW_UP|NEXT_MAIN_QUESTION",
            "followUpQuestion":"required only for FOLLOW_UP",
            "decisionReason":"non-blank"},"usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelJavaSkillAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.JAVA_SKILL;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt(context),
                InterviewAgentOutput.class);
    }

    private String prompt(AgentPromptContext context) {
        return """
                Task: %s
                Direction: %s
                Difficulty: %s
                Main question number: %d
                Current question: %s
                Candidate answer: %s
                Router focus: %s
                Evidence:
                %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.task().mainQuestionNumber(),
                context.currentQuestion() == null
                        ? ""
                        : context.currentQuestion().text(),
                context.candidateAnswer() == null
                        ? ""
                        : context.candidateAnswer(),
                context.routerDecision() == null
                        ? ""
                        : context.routerDecision().suggestedFocus(),
                context.ragEvidence(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
    }
}
```

Create `ModelInterviewerAgent.java`:

```java
package com.example.demoscope;

public class ModelInterviewerAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a general Java technical interviewer agent.
            Return one JSON object only. Do not use markdown.
            Keep the interview coherent, avoid repeating already asked questions,
            and focus on useful follow-up quality.
            Return an InterviewAgentOutput whose agentName is INTERVIEWER.
            For question tasks, type must be QUESTION.
            For answer evaluation tasks, type must be ANSWER_EVALUATION.
            Schema for QUESTION:
            {"agentName":"INTERVIEWER","type":"QUESTION",
            "generatedQuestion":{"question":"non-blank",
            "skillTags":["tag"],"evidenceIds":["id"]},
            "usedEvidenceIds":["id"]}
            Schema for ANSWER_EVALUATION:
            {"agentName":"INTERVIEWER","type":"ANSWER_EVALUATION",
            "answerEvaluation":{"internalEvaluation":"non-blank",
            "abilityTags":["tag"],"decision":"FOLLOW_UP|NEXT_MAIN_QUESTION",
            "followUpQuestion":"required only for FOLLOW_UP",
            "decisionReason":"non-blank"},"usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewerAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.INTERVIEWER;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt(context),
                InterviewAgentOutput.class);
    }

    private String prompt(AgentPromptContext context) {
        return """
                Task: %s
                Direction: %s
                Difficulty: %s
                Main question number: %d
                Current question: %s
                Candidate answer: %s
                Router focus: %s
                Evidence:
                %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.task().mainQuestionNumber(),
                context.currentQuestion() == null
                        ? ""
                        : context.currentQuestion().text(),
                context.candidateAnswer() == null
                        ? ""
                        : context.candidateAnswer(),
                context.routerDecision() == null
                        ? ""
                        : context.routerDecision().suggestedFocus(),
                context.ragEvidence(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
    }
}
```

Create `ModelProjectAgent.java`:

```java
package com.example.demoscope;

public class ModelProjectAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a project-depth Java backend interview agent.
            Return one JSON object only. Do not use markdown.
            Focus on project experience, trade-offs, production incidents,
            architecture decisions, and engineering judgment.
            Return an InterviewAgentOutput whose agentName is PROJECT.
            For question tasks, type must be QUESTION.
            For answer evaluation tasks, type must be ANSWER_EVALUATION.
            Schema for QUESTION:
            {"agentName":"PROJECT","type":"QUESTION",
            "generatedQuestion":{"question":"non-blank",
            "skillTags":["tag"],"evidenceIds":["id"]},
            "usedEvidenceIds":["id"]}
            Schema for ANSWER_EVALUATION:
            {"agentName":"PROJECT","type":"ANSWER_EVALUATION",
            "answerEvaluation":{"internalEvaluation":"non-blank",
            "abilityTags":["tag"],"decision":"FOLLOW_UP|NEXT_MAIN_QUESTION",
            "followUpQuestion":"required only for FOLLOW_UP",
            "decisionReason":"non-blank"},"usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelProjectAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.PROJECT;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt(context),
                InterviewAgentOutput.class);
    }

    private String prompt(AgentPromptContext context) {
        return """
                Task: %s
                Direction: %s
                Difficulty: %s
                Main question number: %d
                Current question: %s
                Candidate answer: %s
                Router focus: %s
                Evidence:
                %s
                Transcript:
                %s
                """.formatted(
                context.task().type(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.task().mainQuestionNumber(),
                context.currentQuestion() == null
                        ? ""
                        : context.currentQuestion().text(),
                context.candidateAnswer() == null
                        ? ""
                        : context.candidateAnswer(),
                context.routerDecision() == null
                        ? ""
                        : context.routerDecision().suggestedFocus(),
                context.ragEvidence(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
    }
}
```

Create `ModelScoreAgent.java`:

```java
package com.example.demoscope;

public class ModelScoreAgent implements InterviewTargetAgent {

    private static final String SYSTEM_PROMPT = """
            You are a Java backend interview scoring agent.
            Return one JSON object only. Do not use markdown.
            Return an InterviewAgentOutput whose agentName is SCORE and type is SCORE_REPORT.
            Scores are integers from 0 to 100.
            Schema:
            {"agentName":"SCORE","type":"SCORE_REPORT",
            "reportDraft":{"overallScore":0,
            "scores":{"javaFundamentals":0,"concurrency":0,"jvm":0,
            "spring":0,"database":0,"engineering":0},
            "strengths":["non-blank"],"weaknesses":["non-blank"],
            "improvementSuggestions":["non-blank"]},
            "usedEvidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelScoreAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAgentName name() {
        return InterviewAgentName.SCORE;
    }

    @Override
    public InterviewAgentOutput run(AgentPromptContext context) {
        String prompt = """
                Direction: %s
                Difficulty: %s
                Router focus: %s
                Evidence:
                %s
                Transcript:
                %s
                """.formatted(
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty(),
                context.routerDecision() == null
                        ? ""
                        : context.routerDecision().suggestedFocus(),
                context.ragEvidence(),
                InterviewTranscriptRenderer.transcript(context.snapshot()));
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt,
                InterviewAgentOutput.class);
    }
}
```

Create `ModelInterviewMemoryManagerAgent.java`:

```java
package com.example.demoscope;

public class ModelInterviewMemoryManagerAgent
        implements InterviewMemoryManagerAgent {

    private static final String SYSTEM_PROMPT = """
            You are an interview memory management agent.
            Return one JSON object only. Do not use markdown.
            Suggest concise memory writes. Do not include secrets, tokens, or raw answers.
            Memory write suggestions must be safe and non-sensitive.
            Schema:
            {"shortTermWrites":["non-blank"],"longTermWrites":["non-blank"],
            "reason":"non-blank"}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewMemoryManagerAgent(InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public MemoryWriteDecision decide(
            AgentPromptContext context,
            InterviewAgentOutput output) {
        String prompt = """
                Memory write suggestions for interview step.
                Task: %s
                Agent: %s
                Output type: %s
                Direction: %s
                Difficulty: %s
                Do not include candidate secrets or raw model JSON.
                """.formatted(
                context.task().type(),
                output.agentName(),
                output.type(),
                context.snapshot().session().direction(),
                context.snapshot().session().difficulty());
        return aiClient.call(SYSTEM_PROMPT, prompt, MemoryWriteDecision.class);
    }
}
```

- [ ] **Step 6: Run model agent tests and verify GREEN**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=ModelInterviewAgentTest' test
```

Expected: `ModelInterviewAgentTest` passes.

- [ ] **Step 7: Commit model agents**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewRouterAgent.java src/main/java/com/example/demoscope/InterviewRagPlannerAgent.java src/main/java/com/example/demoscope/InterviewTargetAgent.java src/main/java/com/example/demoscope/InterviewMemoryManagerAgent.java src/main/java/com/example/demoscope/InterviewTranscriptRenderer.java src/main/java/com/example/demoscope/ModelInterviewRouterAgent.java src/main/java/com/example/demoscope/ModelInterviewRagPlannerAgent.java src/main/java/com/example/demoscope/ModelInterviewerAgent.java src/main/java/com/example/demoscope/ModelProjectAgent.java src/main/java/com/example/demoscope/ModelJavaSkillAgent.java src/main/java/com/example/demoscope/ModelScoreAgent.java src/main/java/com/example/demoscope/ModelInterviewMemoryManagerAgent.java src/test/java/com/example/demoscope/ModelInterviewAgentTest.java
git commit -m "feat: add model interview agents"
```

## Task 3: Add Planned Evidence Retrieval and Interview Memory Support

**Files:**

- Modify: `src/main/java/com/example/demoscope/InterviewEvidenceProvider.java`
- Create: `src/main/java/com/example/demoscope/InterviewMemoryContextProvider.java`
- Create: `src/main/java/com/example/demoscope/InterviewMemoryWriter.java`
- Create: `src/main/java/com/example/demoscope/DefaultInterviewMemoryWriter.java`
- Test: `src/test/java/com/example/demoscope/InterviewEvidenceProviderPlanTest.java`
- Test: `src/test/java/com/example/demoscope/InterviewMemorySupportTest.java`

- [ ] **Step 1: Write failing evidence plan tests**

Create `src/test/java/com/example/demoscope/InterviewEvidenceProviderPlanTest.java`:

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class InterviewEvidenceProviderPlanTest {

    @Test
    void executesPlannedQueriesAndDeduplicatesEvidenceBySource() {
        List<String> embedded = new ArrayList<>();
        InterviewEvidenceProvider provider = new InterviewEvidenceProvider(
                query -> {
                    embedded.add(query);
                    return new float[] {0.1f};
                },
                query -> List.of(
                        new KnowledgeChunk("doc-1", "first"),
                        new KnowledgeChunk("doc-1", "duplicate"),
                        new KnowledgeChunk("doc-2", "second")));
        RagQueryPlan plan = new RagQueryPlan(List.of(
                new RagQueryPlan.Query(
                        "HashMap",
                        3,
                        List.of("java"),
                        "question",
                        "reference"),
                new RagQueryPlan.Query(
                        "ConcurrentHashMap",
                        3,
                        List.of("java"),
                        "question",
                        "reference")));

        List<KnowledgeChunk> evidence = provider.retrieve(plan, 3);

        assertEquals(List.of("HashMap", "ConcurrentHashMap"), embedded);
        assertEquals(List.of("doc-1", "doc-2"), evidence.stream()
                .map(KnowledgeChunk::source)
                .toList());
    }

    @Test
    void retrievalFailureReturnsEmptyEvidence() {
        InterviewEvidenceProvider provider = new InterviewEvidenceProvider(
                query -> {
                    throw new IllegalStateException("embedding down");
                },
                query -> List.of(new KnowledgeChunk("doc-1", "content")));
        RagQueryPlan plan = new RagQueryPlan(List.of(
                new RagQueryPlan.Query(
                        "HashMap",
                        3,
                        List.of("java"),
                        "question",
                        "reference")));

        assertEquals(List.of(), provider.retrieve(plan, 5));
    }
}
```

- [ ] **Step 2: Write failing memory support tests**

Create `src/test/java/com/example/demoscope/InterviewMemorySupportTest.java`:

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InterviewMemorySupportTest {

    @Test
    void memoryContextUsesDerivedInterviewConversationKey() {
        CapturingShortTermStore shortTerm = new CapturingShortTermStore();
        CapturingLongTermRepository longTerm = new CapturingLongTermRepository();
        InterviewMemoryContextProvider provider =
                new InterviewMemoryContextProvider(
                        shortTerm,
                        longTerm,
                        input -> new float[] {0.2f});
        InterviewSnapshot snapshot = snapshot();

        provider.load(snapshot, "HashMap question");

        assertEquals("user-42", shortTerm.lastUserId);
        assertEquals("interview:" + snapshot.session().id(),
                shortTerm.lastConversationId);
        assertEquals("user-42", longTerm.lastUserId);
    }

    @Test
    void memoryWriterPersistsSafeSuggestionsAndFiltersSecrets() {
        CapturingShortTermStore shortTerm = new CapturingShortTermStore();
        CapturingLongTermRepository longTerm = new CapturingLongTermRepository();
        DefaultInterviewMemoryWriter writer = new DefaultInterviewMemoryWriter(
                shortTerm,
                longTerm,
                new LongTermMemoryPolicy(),
                Clock.fixed(Instant.parse("2026-06-16T06:00:00Z"),
                        ZoneOffset.UTC));
        InterviewSnapshot snapshot = snapshot();

        writer.write(
                snapshot,
                new MemoryWriteDecision(
                        List.of("candidate needs hashmap follow-up"),
                        List.of(
                                "candidate prefers concrete examples",
                                "api_key=secret"),
                        "safe writes"));

        assertEquals(1, shortTerm.turns.size());
        assertEquals("interview:" + snapshot.session().id(),
                shortTerm.lastConversationId);
        assertEquals(1, longTerm.saved.size());
        assertEquals("candidate prefers concrete examples",
                longTerm.saved.get(0).text());
    }

    private InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000801");
        InterviewSession session = new InterviewSession(
                interviewId,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.IN_PROGRESS,
                1,
                null,
                0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        return new InterviewSnapshot(session, List.of(), List.of(), null);
    }

    private static final class CapturingShortTermStore
            implements ShortTermMemoryStore {
        private final List<MemoryTurn> turns = new ArrayList<>();
        private String lastUserId;
        private String lastConversationId;

        @Override
        public void append(String userId, String conversationId, MemoryTurn turn) {
            lastUserId = userId;
            lastConversationId = conversationId;
            turns.add(turn);
        }

        @Override
        public List<MemoryTurn> recent(String userId, String conversationId) {
            lastUserId = userId;
            lastConversationId = conversationId;
            return List.copyOf(turns);
        }
    }

    private static final class CapturingLongTermRepository
            implements LongTermMemoryRepository {
        private final List<LongTermMemoryCandidate> saved = new ArrayList<>();
        private String lastUserId;

        @Override
        public List<LongTermMemory> findRelevant(
                String userId,
                SemanticQuery query) {
            lastUserId = userId;
            return List.of();
        }

        @Override
        public void save(
                String userId,
                String conversationId,
                LongTermMemoryCandidate candidate) {
            saved.add(candidate);
        }
    }
}
```

- [ ] **Step 3: Run evidence and memory tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewEvidenceProviderPlanTest,InterviewMemorySupportTest' test
```

Expected: compilation fails because planned retrieval and memory support types
do not exist.

- [ ] **Step 4: Implement planned evidence retrieval**

Modify `InterviewEvidenceProvider` to add:

```java
public List<KnowledgeChunk> retrieve(RagQueryPlan plan, int maxEvidence) {
    try {
        Map<String, KnowledgeChunk> chunksBySource = new LinkedHashMap<>();
        for (RagQueryPlan.Query query : plan.queries()) {
            float[] embedding = embeddingClient.embed(query.query());
            for (KnowledgeChunk chunk : knowledgeRetriever.retrieve(
                    new SemanticQuery(query.query(), embedding))) {
                chunksBySource.putIfAbsent(chunk.source(), chunk);
                if (chunksBySource.size() >= maxEvidence) {
                    return List.copyOf(chunksBySource.values());
                }
            }
        }
        return List.copyOf(chunksBySource.values());
    } catch (RuntimeException exception) {
        log.warn("Interview evidence retrieval failed");
        return List.of();
    }
}
```

Keep the existing `retrieve(String query)` method unchanged so current tests and
compatibility adapters still work.

- [ ] **Step 5: Implement memory context provider and writer**

Create `InterviewMemoryContextProvider.java`:

```java
package com.example.demoscope;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterviewMemoryContextProvider {

    private static final Logger log =
            LoggerFactory.getLogger(InterviewMemoryContextProvider.class);

    private final ShortTermMemoryStore shortTermMemoryStore;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final EmbeddingClient embeddingClient;

    public InterviewMemoryContextProvider(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            EmbeddingClient embeddingClient) {
        this.shortTermMemoryStore = shortTermMemoryStore;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.embeddingClient = embeddingClient;
    }

    public MemoryContext load(InterviewSnapshot snapshot, String queryText) {
        String userId = snapshot.session().userId();
        String conversationId = conversationId(snapshot);
        List<MemoryTurn> shortTerm = readShortTerm(userId, conversationId);
        List<LongTermMemory> longTerm = readLongTerm(userId, queryText);
        return new MemoryContext(shortTerm, longTerm, List.of());
    }

    public static String conversationId(InterviewSnapshot snapshot) {
        return "interview:" + snapshot.session().id();
    }

    private List<MemoryTurn> readShortTerm(
            String userId,
            String conversationId) {
        try {
            return shortTermMemoryStore.recent(userId, conversationId);
        } catch (RuntimeException exception) {
            log.warn("Failed to read interview short-term memory");
            return List.of();
        }
    }

    private List<LongTermMemory> readLongTerm(
            String userId,
            String queryText) {
        try {
            return longTermMemoryRepository.findRelevant(
                    userId,
                    new SemanticQuery(queryText, embeddingClient.embed(queryText)));
        } catch (RuntimeException exception) {
            log.warn("Failed to read interview long-term memory");
            return List.of();
        }
    }
}
```

Create `InterviewMemoryWriter.java`:

```java
package com.example.demoscope;

public interface InterviewMemoryWriter {

    void write(InterviewSnapshot snapshot, MemoryWriteDecision decision);
}
```

Create `DefaultInterviewMemoryWriter.java`:

```java
package com.example.demoscope;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInterviewMemoryWriter implements InterviewMemoryWriter {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultInterviewMemoryWriter.class);

    private final ShortTermMemoryStore shortTermMemoryStore;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final LongTermMemoryPolicy longTermMemoryPolicy;
    private final Clock clock;

    public DefaultInterviewMemoryWriter(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            LongTermMemoryPolicy longTermMemoryPolicy,
            Clock clock) {
        this.shortTermMemoryStore = shortTermMemoryStore;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.longTermMemoryPolicy = longTermMemoryPolicy;
        this.clock = clock;
    }

    @Override
    public void write(InterviewSnapshot snapshot, MemoryWriteDecision decision) {
        String userId = snapshot.session().userId();
        String conversationId = InterviewMemoryContextProvider.conversationId(
                snapshot);
        for (String write : decision.shortTermWrites()) {
            try {
                shortTermMemoryStore.append(
                        userId,
                        conversationId,
                        new MemoryTurn(
                                "interview-memory",
                                write,
                                clock.instant()));
            } catch (RuntimeException exception) {
                log.warn("Failed to write interview short-term memory");
            }
        }
        for (String write : decision.longTermWrites()) {
            LongTermMemoryCandidate candidate = new LongTermMemoryCandidate(
                    LongTermMemoryCategory.STABLE_FACT,
                    write,
                    0.7);
            if (!longTermMemoryPolicy.isAllowed(candidate)) {
                continue;
            }
            try {
                longTermMemoryRepository.save(
                        userId,
                        conversationId,
                        candidate);
            } catch (RuntimeException exception) {
                log.warn("Failed to write interview long-term memory");
            }
        }
    }
}
```

- [ ] **Step 6: Run evidence and memory tests and verify GREEN**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewEvidenceProviderPlanTest,InterviewMemorySupportTest' test
```

Expected: both tests pass.

- [ ] **Step 7: Commit retrieval and memory support**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewEvidenceProvider.java src/main/java/com/example/demoscope/InterviewMemoryContextProvider.java src/main/java/com/example/demoscope/InterviewMemoryWriter.java src/main/java/com/example/demoscope/DefaultInterviewMemoryWriter.java src/test/java/com/example/demoscope/InterviewEvidenceProviderPlanTest.java src/test/java/com/example/demoscope/InterviewMemorySupportTest.java
git commit -m "feat: add interview agent evidence and memory support"
```

## Task 4: Implement InterviewAgentOrchestrator

**Files:**

- Create: `src/main/java/com/example/demoscope/InterviewAgentOrchestrator.java`
- Create: `src/main/java/com/example/demoscope/AgenticInterviewQuestionGenerator.java`
- Create: `src/main/java/com/example/demoscope/AgenticInterviewAnswerEvaluator.java`
- Create: `src/main/java/com/example/demoscope/AgenticInterviewReportGenerator.java`
- Test: `src/test/java/com/example/demoscope/InterviewAgentOrchestratorTest.java`

- [ ] **Step 1: Write failing orchestrator tests**

Create `src/test/java/com/example/demoscope/InterviewAgentOrchestratorTest.java`:

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private Map<InterviewAgentName, InterviewTargetAgent> targets;
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
        targets.put(InterviewAgentName.INTERVIEWER,
                new FakeTarget(InterviewAgentName.INTERVIEWER));
        targets.put(InterviewAgentName.PROJECT,
                new FakeTarget(InterviewAgentName.PROJECT));
        targets.put(InterviewAgentName.JAVA_SKILL,
                new FakeTarget(InterviewAgentName.JAVA_SKILL));
        targets.put(InterviewAgentName.SCORE,
                new FakeTarget(InterviewAgentName.SCORE));
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
    void routerSelectionCallsMatchingTargetAgent() {
        router.nextAgent = InterviewAgentName.PROJECT;

        InterviewAiContracts.GeneratedQuestion result =
                orchestrator.generateQuestion(snapshot(), 1);

        assertEquals("Question from PROJECT", result.question());
        assertEquals(InterviewAgentName.PROJECT,
                ((FakeTarget) targets.get(InterviewAgentName.PROJECT)).lastContext
                        .routerDecision()
                        .nextAgent());
    }

    @Test
    void routerFailureFallsBackToTaskDefaultAgent() {
        router.fail = true;

        InterviewAiContracts.GeneratedQuestion result =
                orchestrator.generateQuestion(snapshot(), 1);

        assertEquals("Question from JAVA_SKILL", result.question());
    }

    @Test
    void illegalRouterTargetFallsBackToTaskDefaultAgent() {
        router.nextAgent = InterviewAgentName.SCORE;

        InterviewAiContracts.GeneratedQuestion result =
                orchestrator.generateQuestion(snapshot(), 1);

        assertEquals("Question from JAVA_SKILL", result.question());
    }

    @Test
    void plannerFailureUsesFallbackRetrieval() {
        planner.fail = true;

        orchestrator.generateQuestion(snapshot(), 2);

        assertEquals(
                "Java backend MIDDLE interview question 2",
                evidenceProvider.lastFallbackQuery);
    }

    @Test
    void ragFailureStillCallsTargetWithEmptyEvidence() {
        evidenceProvider.failPlan = true;

        orchestrator.generateQuestion(snapshot(), 1);

        AgentPromptContext context =
                ((FakeTarget) targets.get(InterviewAgentName.JAVA_SKILL))
                        .lastContext;
        assertEquals(List.of(), context.ragEvidence());
    }

    @Test
    void targetFailurePropagatesToServiceBoundary() {
        ((FakeTarget) targets.get(InterviewAgentName.JAVA_SKILL)).fail = true;

        assertThrows(
                IllegalStateException.class,
                () -> orchestrator.generateQuestion(snapshot(), 1));
    }

    @Test
    void memoryManagerFailureDoesNotBlockTargetOutput() {
        memoryManager.fail = true;

        InterviewAiContracts.GeneratedQuestion result =
                orchestrator.generateQuestion(snapshot(), 1);

        assertEquals("Question from JAVA_SKILL", result.question());
        assertEquals(0, memoryWriter.writes.size());
    }

    @Test
    void memoryManagerSuccessWritesSuggestions() {
        orchestrator.generateQuestion(snapshot(), 1);

        assertEquals(1, memoryWriter.writes.size());
        assertEquals("remember java weakness",
                memoryWriter.writes.get(0).shortTermWrites().get(0));
    }

    @Test
    void scoreTaskOnlyAllowsScoreAgent() {
        router.nextAgent = InterviewAgentName.JAVA_SKILL;

        InterviewAiContracts.ReportDraft report =
                orchestrator.generateReport(answeredSnapshot());

        assertEquals(80, report.overallScore());
    }

    private InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000901");
        InterviewQuestion question = InterviewQuestion.main(
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                interviewId,
                1,
                "Explain HashMap.",
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
                question.id(),
                0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        return new InterviewSnapshot(session, List.of(question), List.of(), null);
    }

    private InterviewSnapshot answeredSnapshot() {
        InterviewSnapshot snapshot = snapshot();
        InterviewQuestion question = snapshot.questions().get(0);
        InterviewAnswer answer = new InterviewAnswer(
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                snapshot.session().id(),
                question.id(),
                "candidate answer",
                "internal evaluation",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                "complete",
                Instant.EPOCH);
        return new InterviewSnapshot(
                snapshot.session(),
                snapshot.questions(),
                List.of(answer),
                null);
    }

    private static final class FakeRouter implements InterviewRouterAgent {
        private InterviewAgentName nextAgent = InterviewAgentName.JAVA_SKILL;
        private boolean fail;

        @Override
        public RouterDecision route(
                AgentPromptContext context,
                java.util.Set<InterviewAgentName> allowedAgents) {
            if (fail) {
                throw new InterviewAiJsonClient.InvalidOutputException("bad router");
            }
            return new RouterDecision(
                    nextAgent,
                    "route",
                    0.9,
                    "focus",
                    List.of());
        }
    }

    private static final class FakePlanner implements InterviewRagPlannerAgent {
        private boolean fail;

        @Override
        public RagQueryPlan plan(AgentPromptContext context) {
            if (fail) {
                throw new InterviewAiJsonClient.InvalidOutputException("bad planner");
            }
            return new RagQueryPlan(List.of(
                    new RagQueryPlan.Query(
                            "planned query",
                            3,
                            List.of("java"),
                            "purpose",
                            "reference")));
        }
    }

    private static final class FakeEvidenceProvider
            extends InterviewEvidenceProvider {
        private boolean failPlan;
        private String lastFallbackQuery;

        private FakeEvidenceProvider() {
            super(query -> new float[] {0.1f}, query -> List.of());
        }

        @Override
        public List<KnowledgeChunk> retrieve(RagQueryPlan plan, int maxEvidence) {
            if (failPlan) {
                return List.of();
            }
            return List.of(new KnowledgeChunk("doc-1", "evidence"));
        }

        @Override
        public List<KnowledgeChunk> retrieve(String query) {
            lastFallbackQuery = query;
            return List.of(new KnowledgeChunk("fallback", "fallback evidence"));
        }
    }

    private static final class FakeMemoryProvider
            extends InterviewMemoryContextProvider {
        private FakeMemoryProvider() {
            super(new InMemoryShortTermMemoryStore(5),
                    new EmptyLongTermMemoryRepository(),
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
                throw new InterviewAiJsonClient.InvalidOutputException("bad memory");
            }
            return new MemoryWriteDecision(
                    List.of("remember java weakness"),
                    List.of(),
                    "safe");
        }
    }

    private static final class FakeMemoryWriter
            implements InterviewMemoryWriter {
        private final List<MemoryWriteDecision> writes = new ArrayList<>();

        @Override
        public void write(
                InterviewSnapshot snapshot,
                MemoryWriteDecision decision) {
            writes.add(decision);
        }
    }

    private static final class FakeTarget implements InterviewTargetAgent {
        private final InterviewAgentName name;
        private boolean fail;
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
            lastContext = context;
            if (fail) {
                throw new IllegalStateException("target failed");
            }
            if (name == InterviewAgentName.SCORE) {
                return InterviewAgentOutput.report(
                        name,
                        new InterviewAiContracts.ReportDraft(
                                80,
                                new InterviewAiContracts.ScoreBreakdown(
                                        82, 76, 78, 79, 77, 81),
                                List.of("clear basics"),
                                List.of("needs JVM depth"),
                                List.of("practice diagnostics")),
                        List.of());
            }
            return InterviewAgentOutput.question(
                    name,
                    new InterviewAiContracts.GeneratedQuestion(
                            "Question from " + name,
                            List.of("JAVA"),
                            List.of()),
                    List.of());
        }
    }
}
```

- [ ] **Step 2: Run orchestrator tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewAgentOrchestratorTest' test
```

Expected: compilation fails because `InterviewAgentOrchestrator` and agentic
adapter classes do not exist.

- [ ] **Step 3: Implement the orchestrator**

Create `InterviewAgentOrchestrator.java`:

```java
package com.example.demoscope;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterviewAgentOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(InterviewAgentOrchestrator.class);

    private final InterviewRouterAgent routerAgent;
    private final InterviewRagPlannerAgent plannerAgent;
    private final InterviewEvidenceProvider evidenceProvider;
    private final InterviewMemoryContextProvider memoryContextProvider;
    private final InterviewMemoryManagerAgent memoryManagerAgent;
    private final InterviewMemoryWriter memoryWriter;
    private final Map<InterviewAgentName, InterviewTargetAgent> targetAgents;
    private final int maxEvidence;

    public InterviewAgentOrchestrator(
            InterviewRouterAgent routerAgent,
            InterviewRagPlannerAgent plannerAgent,
            InterviewEvidenceProvider evidenceProvider,
            InterviewMemoryContextProvider memoryContextProvider,
            InterviewMemoryManagerAgent memoryManagerAgent,
            InterviewMemoryWriter memoryWriter,
            List<InterviewTargetAgent> targetAgents,
            int maxEvidence) {
        this.routerAgent = routerAgent;
        this.plannerAgent = plannerAgent;
        this.evidenceProvider = evidenceProvider;
        this.memoryContextProvider = memoryContextProvider;
        this.memoryManagerAgent = memoryManagerAgent;
        this.memoryWriter = memoryWriter;
        this.targetAgents = new EnumMap<>(InterviewAgentName.class);
        for (InterviewTargetAgent targetAgent : targetAgents) {
            this.targetAgents.put(targetAgent.name(), targetAgent);
        }
        this.maxEvidence = Math.max(1, maxEvidence);
    }

    public InterviewAiContracts.GeneratedQuestion generateQuestion(
            InterviewSnapshot snapshot,
            int mainQuestionNumber) {
        InterviewAgentOutput output = run(
                snapshot,
                InterviewAgentTask.generateMainQuestion(
                        snapshot,
                        mainQuestionNumber));
        if (output.generatedQuestion() == null) {
            throw new InterviewAiJsonClient.InvalidOutputException(
                    "target agent returned non-question output");
        }
        return output.generatedQuestion();
    }

    public InterviewAiContracts.AnswerEvaluation evaluateAnswer(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer) {
        InterviewAgentOutput output = run(
                snapshot,
                InterviewAgentTask.evaluateAnswer(
                        snapshot,
                        question,
                        candidateAnswer));
        if (output.answerEvaluation() == null) {
            throw new InterviewAiJsonClient.InvalidOutputException(
                    "target agent returned non-evaluation output");
        }
        return output.answerEvaluation();
    }

    public InterviewAiContracts.ReportDraft generateReport(
            InterviewSnapshot snapshot) {
        InterviewAgentOutput output = run(
                snapshot,
                InterviewAgentTask.generateReport(snapshot));
        if (output.reportDraft() == null) {
            throw new InterviewAiJsonClient.InvalidOutputException(
                    "target agent returned non-report output");
        }
        return output.reportDraft();
    }

    private InterviewAgentOutput run(
            InterviewSnapshot snapshot,
            InterviewAgentTask task) {
        MemoryContext memoryContext = memoryContextProvider.load(
                snapshot,
                fallbackQuery(snapshot, task));
        AgentPromptContext baseContext = new AgentPromptContext(
                snapshot,
                task,
                task.currentQuestion(),
                task.candidateAnswer(),
                memoryContext.shortTermTurns(),
                memoryContext.longTermMemories(),
                List.of(),
                null);
        RouterDecision decision = route(baseContext, task);
        AgentPromptContext routedContext = baseContext.withRouterDecision(decision);
        List<KnowledgeChunk> evidence = retrieveEvidence(routedContext, task);
        AgentPromptContext targetContext = routedContext.withRagEvidence(evidence);
        InterviewTargetAgent target = targetAgents.get(decision.nextAgent());
        if (target == null) {
            throw new IllegalStateException("missing interview target agent");
        }
        InterviewAgentOutput output = target.run(targetContext);
        writeMemory(targetContext, output);
        return output;
    }

    private RouterDecision route(
            AgentPromptContext context,
            InterviewAgentTask task) {
        try {
            RouterDecision decision = routerAgent.route(
                    context,
                    task.allowedAgents());
            if (task.allowedAgents().contains(decision.nextAgent())) {
                return decision;
            }
            log.warn("Interview router returned illegal target agent");
        } catch (RuntimeException exception) {
            log.warn("Interview router failed");
        }
        return new RouterDecision(
                task.defaultAgent(),
                "fallback route",
                0.0,
                fallbackQuery(context.snapshot(), task),
                List.of());
    }

    private List<KnowledgeChunk> retrieveEvidence(
            AgentPromptContext context,
            InterviewAgentTask task) {
        try {
            return evidenceProvider.retrieve(
                    plannerAgent.plan(context),
                    maxEvidence);
        } catch (RuntimeException exception) {
            log.warn("Interview planner failed");
            return evidenceProvider.retrieve(fallbackQuery(
                    context.snapshot(),
                    task));
        }
    }

    private void writeMemory(
            AgentPromptContext context,
            InterviewAgentOutput output) {
        try {
            memoryWriter.write(
                    context.snapshot(),
                    memoryManagerAgent.decide(context, output));
        } catch (RuntimeException exception) {
            log.warn("Interview memory manager failed");
        }
    }

    private String fallbackQuery(
            InterviewSnapshot snapshot,
            InterviewAgentTask task) {
        return switch (task.type()) {
            case GENERATE_MAIN_QUESTION -> "Java backend "
                    + snapshot.session().difficulty()
                    + " interview question "
                    + task.mainQuestionNumber();
            case EVALUATE_ANSWER -> task.currentQuestion().text()
                    + " "
                    + task.candidateAnswer();
            case GENERATE_REPORT -> "Java backend interview scoring transcript";
        };
    }
}
```

- [ ] **Step 4: Implement agentic adapter classes**

Create these three adapters:

```java
package com.example.demoscope;

public class AgenticInterviewQuestionGenerator
        implements InterviewQuestionGenerator {

    private final InterviewAgentOrchestrator orchestrator;

    public AgenticInterviewQuestionGenerator(
            InterviewAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public InterviewAiContracts.GeneratedQuestion generate(
            InterviewSnapshot snapshot,
            int mainQuestionNumber) {
        return orchestrator.generateQuestion(snapshot, mainQuestionNumber);
    }
}
```

```java
package com.example.demoscope;

public class AgenticInterviewAnswerEvaluator
        implements InterviewAnswerEvaluator {

    private final InterviewAgentOrchestrator orchestrator;

    public AgenticInterviewAnswerEvaluator(
            InterviewAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public InterviewAiContracts.AnswerEvaluation evaluate(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer) {
        return orchestrator.evaluateAnswer(
                snapshot,
                question,
                candidateAnswer);
    }
}
```

```java
package com.example.demoscope;

public class AgenticInterviewReportGenerator
        implements InterviewReportGenerator {

    private final InterviewAgentOrchestrator orchestrator;

    public AgenticInterviewReportGenerator(
            InterviewAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public InterviewAiContracts.ReportDraft generate(
            InterviewSnapshot snapshot) {
        return orchestrator.generateReport(snapshot);
    }
}
```

- [ ] **Step 5: Run orchestrator tests and verify GREEN**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewAgentOrchestratorTest' test
```

Expected: `InterviewAgentOrchestratorTest` passes.

- [ ] **Step 6: Commit orchestrator**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewAgentOrchestrator.java src/main/java/com/example/demoscope/AgenticInterviewQuestionGenerator.java src/main/java/com/example/demoscope/AgenticInterviewAnswerEvaluator.java src/main/java/com/example/demoscope/AgenticInterviewReportGenerator.java src/test/java/com/example/demoscope/InterviewAgentOrchestratorTest.java
git commit -m "feat: orchestrate interview agents"
```

## Task 5: Wire Agentic Production Beans

**Files:**

- Modify: `src/main/java/com/example/demoscope/InterviewConfig.java`
- Modify: `src/test/java/com/example/demoscope/InterviewConfigTest.java`

- [ ] **Step 1: Write failing configuration assertions**

Modify `InterviewConfigTest.enabledInterviewWiresAllProductionComponents()` to
add:

```java
assertThat(context).hasSingleBean(InterviewAgentOrchestrator.class);
assertThat(context).hasSingleBean(InterviewRouterAgent.class);
assertThat(context).hasSingleBean(InterviewRagPlannerAgent.class);
assertThat(context).hasSingleBean(InterviewMemoryManagerAgent.class);
assertThat(context).hasSingleBean(InterviewMemoryContextProvider.class);
assertThat(context).hasSingleBean(InterviewMemoryWriter.class);
assertThat(context).hasBean("interviewerAgent");
assertThat(context).hasBean("projectAgent");
assertThat(context).hasBean("javaSkillAgent");
assertThat(context).hasBean("scoreAgent");
assertThat(context.getBean(InterviewQuestionGenerator.class))
        .isInstanceOf(AgenticInterviewQuestionGenerator.class);
assertThat(context.getBean(InterviewAnswerEvaluator.class))
        .isInstanceOf(AgenticInterviewAnswerEvaluator.class);
assertThat(context.getBean(InterviewReportGenerator.class))
        .isInstanceOf(AgenticInterviewReportGenerator.class);
```

Modify `disabledInterviewCreatesNoInterviewComponents()` to add:

```java
assertThat(context).doesNotHaveBean(InterviewAgentOrchestrator.class);
assertThat(context).doesNotHaveBean(InterviewRouterAgent.class);
assertThat(context).doesNotHaveBean(InterviewRagPlannerAgent.class);
assertThat(context).doesNotHaveBean(InterviewMemoryManagerAgent.class);
```

Add `ShortTermMemoryStore`, `LongTermMemoryRepository`, and
`LongTermMemoryPolicy` beans to `InterviewConfigTest.Infrastructure`:

```java
@Bean
ShortTermMemoryStore shortTermMemoryStore() {
    return mock(ShortTermMemoryStore.class);
}

@Bean
LongTermMemoryRepository longTermMemoryRepository() {
    return mock(LongTermMemoryRepository.class);
}

@Bean
LongTermMemoryPolicy longTermMemoryPolicy() {
    return new LongTermMemoryPolicy();
}
```

- [ ] **Step 2: Run config test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewConfigTest' test
```

Expected: the test fails because `InterviewConfig` still wires the simple
`ModelInterviewQuestionGenerator`, `ModelInterviewAnswerEvaluator`, and
`ModelInterviewReportGenerator`.

- [ ] **Step 3: Wire agentic beans in InterviewConfig**

Modify `InterviewConfig`:

```java
@Bean
InterviewRouterAgent interviewRouterAgent(InterviewAiJsonClient aiClient) {
    return new ModelInterviewRouterAgent(aiClient);
}

@Bean
InterviewRagPlannerAgent interviewRagPlannerAgent(
        InterviewAiJsonClient aiClient) {
    return new ModelInterviewRagPlannerAgent(aiClient);
}

@Bean("interviewerAgent")
InterviewTargetAgent interviewerAgent(InterviewAiJsonClient aiClient) {
    return new ModelInterviewerAgent(aiClient);
}

@Bean("projectAgent")
InterviewTargetAgent projectAgent(InterviewAiJsonClient aiClient) {
    return new ModelProjectAgent(aiClient);
}

@Bean("javaSkillAgent")
InterviewTargetAgent javaSkillAgent(InterviewAiJsonClient aiClient) {
    return new ModelJavaSkillAgent(aiClient);
}

@Bean("scoreAgent")
InterviewTargetAgent scoreAgent(InterviewAiJsonClient aiClient) {
    return new ModelScoreAgent(aiClient);
}

@Bean
InterviewMemoryManagerAgent interviewMemoryManagerAgent(
        InterviewAiJsonClient aiClient) {
    return new ModelInterviewMemoryManagerAgent(aiClient);
}

@Bean
InterviewMemoryContextProvider interviewMemoryContextProvider(
        ShortTermMemoryStore shortTermMemoryStore,
        LongTermMemoryRepository longTermMemoryRepository,
        EmbeddingClient embeddingClient) {
    return new InterviewMemoryContextProvider(
            shortTermMemoryStore,
            longTermMemoryRepository,
            embeddingClient);
}

@Bean
InterviewMemoryWriter interviewMemoryWriter(
        ShortTermMemoryStore shortTermMemoryStore,
        LongTermMemoryRepository longTermMemoryRepository,
        LongTermMemoryPolicy longTermMemoryPolicy,
        Clock clock) {
    return new DefaultInterviewMemoryWriter(
            shortTermMemoryStore,
            longTermMemoryRepository,
            longTermMemoryPolicy,
            clock);
}

@Bean
InterviewAgentOrchestrator interviewAgentOrchestrator(
        InterviewRouterAgent routerAgent,
        InterviewRagPlannerAgent plannerAgent,
        InterviewEvidenceProvider evidenceProvider,
        InterviewMemoryContextProvider memoryContextProvider,
        InterviewMemoryManagerAgent memoryManagerAgent,
        InterviewMemoryWriter memoryWriter,
        List<InterviewTargetAgent> targetAgents,
        @Value("${agentscope.interview.agent.max-evidence:6}")
        int maxEvidence) {
    return new InterviewAgentOrchestrator(
            routerAgent,
            plannerAgent,
            evidenceProvider,
            memoryContextProvider,
            memoryManagerAgent,
            memoryWriter,
            targetAgents,
            maxEvidence);
}
```

Replace the three generator beans:

```java
@Bean
InterviewQuestionGenerator interviewQuestionGenerator(
        InterviewAgentOrchestrator orchestrator) {
    return new AgenticInterviewQuestionGenerator(orchestrator);
}

@Bean
InterviewAnswerEvaluator interviewAnswerEvaluator(
        InterviewAgentOrchestrator orchestrator) {
    return new AgenticInterviewAnswerEvaluator(orchestrator);
}

@Bean
InterviewReportGenerator interviewReportGenerator(
        InterviewAgentOrchestrator orchestrator) {
    return new AgenticInterviewReportGenerator(orchestrator);
}
```

Keep `InterviewAiJsonClient`, `InterviewEvidenceProvider`, and
`InterviewService` beans.

- [ ] **Step 4: Run configuration test and verify GREEN**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewConfigTest' test
```

Expected: `InterviewConfigTest` passes.

- [ ] **Step 5: Commit wiring**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewConfig.java src/test/java/com/example/demoscope/InterviewConfigTest.java
git commit -m "feat: wire agentic interview adapters"
```

## Task 6: Prove Agentic HTTP Flow and Degradation

**Files:**

- Create: `src/test/java/com/example/demoscope/AgenticAuthenticatedInterviewFlowTest.java`

- [ ] **Step 1: Write failing agentic flow test**

Create `src/test/java/com/example/demoscope/AgenticAuthenticatedInterviewFlowTest.java`.
Use the actual `InterviewController`, actual `InterviewService`, actual
`InterviewAgentOrchestrator`, and fake Router, Planner, targets, MemoryManager,
memory provider, and memory writer. The test should be structurally close to
`AuthenticatedInterviewFlowTest`, but fakes attach below the orchestrator instead
of replacing the generator/evaluator/report ports.

Use this minimal test body:

```java
@Test
void agenticInterviewFlowCompletesAndUsesRouterPlannerAndMemory()
        throws Exception {
    router.nextAgents.addAll(List.of(
            InterviewAgentName.JAVA_SKILL,
            InterviewAgentName.INTERVIEWER,
            InterviewAgentName.JAVA_SKILL,
            InterviewAgentName.SCORE));
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
```

Add a second test:

```java
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
```

Add these self-contained helper methods to the new test class:

- `create(String token, int expectedStatus)` posts
  `POST /api/interviews` with the bearer token, sends the default Java interview
  request body, asserts the response status, parses the JSON response, and calls
  `assertSafe`.
- `answer(String token, UUID interviewId, UUID questionId, String answer,
  int expectedStatus)` posts `POST /api/interviews/{interviewId}/answers` with
  the bearer token and JSON fields `questionId` and `answer`, asserts the
  response status, parses the JSON response, and calls `assertSafe`.
- `answerCurrent(JsonNode state, UUID interviewId, String answer,
  int expectedStatus)` reads the current question id from `state` and delegates
  to `answer`.
- `body(String json)` returns a `MockHttpServletRequestBuilder` content body with
  `MediaType.APPLICATION_JSON`.
- `assertSafe(JsonNode response)` asserts the response does not expose
  `internalEvaluation`, `decisionReason`, raw model JSON, bearer tokens, Redis
  keys, or Authorization headers.
- `uuid(JsonNode node, String field)` parses the named field as `UUID`.
- `questionId(JsonNode state)` parses `state.path("question").path("id")` as
  `UUID`.

Add these fake classes:

- `QueuedRouter implements InterviewRouterAgent`
- `CountingPlanner implements InterviewRagPlannerAgent`
- `QueuedTarget implements InterviewTargetAgent`
- `FailingMemoryManager implements InterviewMemoryManagerAgent`
- `CapturingMemoryWriter implements InterviewMemoryWriter`

- [ ] **Step 2: Run agentic flow test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgenticAuthenticatedInterviewFlowTest' test
```

Expected before Task 4 and Task 5 are complete: compilation failure. Expected
after Task 5: the first draft may fail due to helper wiring mistakes. Fix only
test helper wiring until failures represent product behavior.

- [ ] **Step 3: Complete the fake wiring and verify GREEN**

Ensure the test creates the service like this:

```java
InterviewAgentOrchestrator orchestrator = new InterviewAgentOrchestrator(
        router,
        planner,
        evidenceProvider,
        memoryProvider,
        memoryManager,
        memoryWriter,
        List.copyOf(targets.values()),
        6);
InterviewService service = new InterviewService(
        repository,
        new AgenticInterviewQuestionGenerator(orchestrator),
        new AgenticInterviewAnswerEvaluator(orchestrator),
        new AgenticInterviewReportGenerator(orchestrator),
        Clock.fixed(NOW, ZoneOffset.UTC),
        () -> new UUID(0, ids.getAndIncrement()),
        5,
        2);
```

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgenticAuthenticatedInterviewFlowTest' test
```

Expected: `AgenticAuthenticatedInterviewFlowTest` passes.

- [ ] **Step 4: Commit agentic flow test**

```powershell
git add -- src/test/java/com/example/demoscope/AgenticAuthenticatedInterviewFlowTest.java
git commit -m "test: prove agentic interview flow"
```

## Task 7: Full Interview Regression and Safety Verification

**Files:**

- No new source files expected.

- [ ] **Step 1: Run focused agentic and interview tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewAgentContractsTest,ModelInterviewAgentTest,InterviewEvidenceProviderPlanTest,InterviewMemorySupportTest,InterviewAgentOrchestratorTest,InterviewConfigTest,InterviewDomainTest,InterviewDatabaseConfigTest,JdbcInterviewRepositoryTest,InterviewAiJsonClientTest,ModelInterviewAiTest,InterviewServiceCreationTest,InterviewServiceAnswerTest,InterviewServiceFinishTest,InterviewControllerTest,AuthenticatedInterviewFlowTest,AgenticAuthenticatedInterviewFlowTest' test
```

Expected: all focused tests pass with zero failures and zero errors.

- [ ] **Step 2: Run full test suite**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B test
```

Expected: full suite passes with zero failures and zero errors.

- [ ] **Step 3: Build package**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run static safety checks**

Run:

```powershell
rg -n "internalEvaluation|decisionReason|evidenceIds|rawJson|token" src/main/java/com/example/demoscope/InterviewController.java
rg -n "request\\.getHeader|Cookie|X-Forwarded|baseUrl" src/main/java/com/example/demoscope/Interview*.java
rg -n "candidateAnswer|answerText|Authorization|Bearer|Redis key|raw model" src/main/java/com/example/demoscope/*Agent*.java src/main/java/com/example/demoscope/InterviewAgentOrchestrator.java src/main/java/com/example/demoscope/DefaultInterviewMemoryWriter.java
git diff --check
```

Expected:

- First command returns no matches.
- Second command returns no matches.
- Third command may show prompt construction uses `candidateAnswer`, but must not
  show logging statements that print candidate answers, Authorization, Bearer
  tokens, Redis keys, or raw model output.
- `git diff --check` exits `0`.

- [ ] **Step 5: Record real environment status**

Check production smoke prerequisites:

```powershell
$names = @(
  'AGENTSCOPE_RUOYI_BASE_URL',
  'AGENTSCOPE_SMOKE_BASE_URL',
  'RUOYI_LOGIN_BODY_FILE',
  'PGVECTOR_URL',
  'PGVECTOR_USERNAME',
  'PGVECTOR_PASSWORD',
  'OPENAI_API_KEY',
  'SILICONFLOW_API_KEY'
)
$names | ForEach-Object {
  $value = [Environment]::GetEnvironmentVariable($_)
  [pscustomobject]@{
    Name = $_
    Configured = -not [string]::IsNullOrWhiteSpace($value)
  }
} | Format-Table -AutoSize
```

Expected: report which variables are configured. If any are missing, state that
automated closure passed and real Redis/model/pgvector production closure remains
blocked by missing environment.

- [ ] **Step 6: Commit verification-only adjustments**

If Task 7 required only code or test fixes from failed verification, commit those
fixes:

```powershell
git status --short
git add -- <only files created or modified by this plan>
git commit -m "fix: stabilize agentic interview verification"
```

Do not stage whole source directories in this step; the worktree may contain
unrelated user or generated changes.

If Task 7 required no changes, do not create an empty commit.

## Implementation Notes

- Keep `/api/chat` unchanged.
- Keep the existing `/api/interviews` response shape unchanged.
- Do not expose `InterviewAgentOutput`, `RouterDecision`, `RagQueryPlan`, or
  `MemoryWriteDecision` through public HTTP responses.
- Do not delete the older `ModelInterviewQuestionGenerator`,
  `ModelInterviewAnswerEvaluator`, or `ModelInterviewReportGenerator` in this
  plan; they remain useful compatibility and prompt regression tests.
- Do not log raw candidate answers, raw model JSON, tokens, Redis keys, or
  Authorization headers.
- Auxiliary agent failures are caught in `InterviewAgentOrchestrator`; target
  agent failures are not caught as success.
