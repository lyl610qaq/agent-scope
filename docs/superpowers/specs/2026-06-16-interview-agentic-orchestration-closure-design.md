# Interview Agentic Orchestration Closure Design

Date: 2026-06-16

## Goal

Close the interview multi-agent orchestration gap by adding an agentic AI
orchestration layer behind the existing authenticated Java interview APIs.

The official interview entry point remains `/api/interviews`. The generic
`/api/chat` route remains unchanged and continues to serve normal chat. This
design intentionally supersedes the `/api/chat` integration portion of the
2026-06-07 multi-agent draft for the current product direction.

## Current State

The codebase already has a persistent authenticated interview state machine:

- `InterviewController` exposes `/api/interviews`.
- `InterviewService` owns lifecycle rules, limits, retries, finish, and scoring.
- `InterviewRepository` and `JdbcInterviewRepository` persist session state.
- `InterviewQuestionGenerator`, `InterviewAnswerEvaluator`, and
  `InterviewReportGenerator` isolate model-dependent behavior.

The missing closure is that model behavior is still implemented as simple
single-purpose adapters. The 2026-06-07 design called for Router, RAG Planner,
target Agents, and MemoryManager responsibilities, but those boundaries do not
exist in code yet.

## Chosen Approach

Use an agentic orchestration layer inside the existing AI adapter boundary.

`InterviewService` continues to call the three existing ports:

- `InterviewQuestionGenerator.generate(...)`
- `InterviewAnswerEvaluator.evaluate(...)`
- `InterviewReportGenerator.generate(...)`

New agentic implementations of those ports delegate to
`InterviewAgentOrchestrator`. The service remains unaware of whether the AI path
is single-model or multi-agent.

This preserves the already-tested interview lifecycle while closing the
multi-agent design gap in the model orchestration layer.

## Architecture

Per AI action, the flow is:

```text
InterviewService
 -> InterviewQuestionGenerator / InterviewAnswerEvaluator / InterviewReportGenerator
 -> InterviewAgentOrchestrator
 -> build AgentPromptContext
 -> RouterAgent
 -> RagQueryPlannerAgent
 -> InterviewEvidenceProvider / KnowledgeRetriever / pgvector
 -> target Agent
 -> MemoryManagerAgent
 -> return existing InterviewAiContracts result
```

Router participates in every action, but each task supplies an allow-list of
valid target agents:

- Main question generation: `INTERVIEWER`, `PROJECT`, `JAVA_SKILL`
- Answer evaluation and follow-up choice: `INTERVIEWER`, `PROJECT`,
  `JAVA_SKILL`
- Final report generation: `SCORE`

If Router returns an illegal target, the orchestrator falls back to the task's
default agent.

## Components

### InterviewAgentOrchestrator

Central single-action orchestration entry point.

Public operations:

- `generateQuestion(InterviewSnapshot snapshot, int mainQuestionNumber)`
- `evaluateAnswer(InterviewSnapshot snapshot, InterviewQuestion question,
  String candidateAnswer)`
- `generateReport(InterviewSnapshot snapshot)`

Responsibilities:

- Build `InterviewAgentTask`.
- Build `AgentPromptContext`.
- Call Router and validate the selected target agent.
- Call Planner and retrieve evidence.
- Call the target agent.
- Call MemoryManager.
- Convert the target output to the existing `InterviewAiContracts` types.

It does not mutate interview session state or write interview repository rows.
Those remain `InterviewService` responsibilities.

### InterviewAgentTask

Represents one AI action.

Fields:

- `type`: `GENERATE_MAIN_QUESTION`, `EVALUATE_ANSWER`, `GENERATE_REPORT`
- `allowedAgents`
- `defaultAgent`
- `mainQuestionNumber`
- `currentQuestion`
- `candidateAnswer`

### InterviewAgentName

Enum:

- `INTERVIEWER`
- `PROJECT`
- `JAVA_SKILL`
- `SCORE`

### AgentPromptContext

Shared context passed to all agents.

Fields:

- `snapshot`
- `task`
- `currentQuestion`
- `candidateAnswer`
- `shortTermMemory`
- `longTermMemory`
- `ragEvidence`
- `routerDecision`

The context is built once per step. Individual agents should not each invent
their own unrelated context shape.

### RouterAgent

Interface:

```java
RouterDecision route(AgentPromptContext context, Set<InterviewAgentName> allowedAgents);
```

Rules:

- Returns only routing metadata.
- Does not ask interview questions.
- Does not score.
- Does not summarize the candidate.

### RagQueryPlannerAgent

Interface:

```java
RagQueryPlan plan(AgentPromptContext context);
```

Rules:

- Produces retrieval intent only.
- Does not call embeddings or pgvector itself.

### Target Agents

Shared interface:

```java
InterviewAgentOutput run(AgentPromptContext context);
```

Implementations:

- `ModelInterviewerAgent`
- `ModelProjectAgent`
- `ModelJavaSkillAgent`
- `ModelScoreAgent`

Target agents produce candidate-visible questions or final report content. They
must return strict JSON that validates into existing interview contracts.

### MemoryManagerAgent

Interface:

```java
MemoryWriteDecision decide(AgentPromptContext context, InterviewAgentOutput output);
```

Rules:

- Produces write suggestions only.
- Does not directly write short-term or long-term memory.
- Orchestrator applies allowed writes through existing memory infrastructure.

## Data Contracts

### RouterDecision

Strict JSON:

```json
{
  "nextAgent": "JAVA_SKILL",
  "reason": "The current task needs core Java depth.",
  "confidence": 0.82,
  "suggestedFocus": "HashMap concurrency and memory model",
  "usedEvidenceIds": ["doc-1"]
}
```

Validation:

- `nextAgent` must be a valid `InterviewAgentName`.
- `reason` and `suggestedFocus` must be non-blank.
- `confidence` must be between `0.0` and `1.0`.
- `usedEvidenceIds` must be present and contain only non-blank strings.

### RagQueryPlan

Strict JSON:

```json
{
  "queries": [
    {
      "query": "Java HashMap resize internals",
      "topK": 6,
      "filters": ["java", "collections"],
      "purpose": "Support a middle-level Java backend question",
      "expectedEvidenceType": "technical_reference"
    }
  ]
}
```

Validation:

- At least one query.
- `query`, `purpose`, and `expectedEvidenceType` are non-blank.
- `topK` is positive and capped by a configured maximum.
- `filters` is present and contains non-blank strings.

### InterviewAgentOutput

Internal wrapper:

```json
{
  "agentName": "JAVA_SKILL",
  "type": "QUESTION",
  "generatedQuestion": {
    "question": "Explain how HashMap resizing works in Java 8.",
    "skillTags": ["JAVA", "COLLECTIONS"],
    "evidenceIds": ["doc-1"]
  },
  "usedEvidenceIds": ["doc-1"]
}
```

Allowed types:

- `QUESTION`
- `ANSWER_EVALUATION`
- `SCORE_REPORT`

Exactly one payload matching the type must be present.

### MemoryWriteDecision

Strict JSON:

```json
{
  "shortTermWrites": ["Candidate struggled with HashMap resize reasoning."],
  "longTermWrites": [],
  "reason": "Useful for follow-up continuity, not durable enough yet."
}
```

Validation:

- Both write arrays must be present.
- Entries must be non-blank.
- `reason` must be non-blank.

## RAG and Evidence

`InterviewEvidenceProvider` is upgraded to execute `RagQueryPlan`.

For each planned query:

1. Embed query text.
2. Call `KnowledgeRetriever`.
3. Merge results by stable evidence ID/source.
4. Limit total evidence passed to the target agent.

If planner output fails validation, the orchestrator uses direct fallback
queries:

- Main question: `Java backend <difficulty> interview question <number>`
- Evaluation: `<question text> <candidate answer>`
- Report: `Java backend interview scoring transcript`

If embedding or retrieval fails, evidence becomes empty and the target agent is
still called.

## Memory Behavior

MemoryManager suggestions are optional support, not the source of truth.

The orchestrator may write:

- short-term continuity notes keyed by authenticated user and a derived
  interview conversation key: `interview:<interviewId>`
- durable long-term candidates through the existing memory policy path

Memory write failures are logged and do not affect the interview response.

Candidate answers, raw model JSON, tokens, Redis keys, and Authorization
headers must not be logged.

## Error Handling

Auxiliary agent failures degrade; target agent failures do not fabricate
candidate-visible results.

- Router JSON failure: fallback to the task default agent.
- Router illegal target: fallback to the task default agent.
- Planner JSON failure: fallback to direct retrieval query.
- Embedding, pgvector, or retrieval failure: empty evidence, continue.
- Target agent JSON failure: propagate AI failure to `InterviewService`.
- MemoryManager JSON failure: skip model-driven memory suggestions, continue.
- Memory write failure: log safe metadata, continue.
- API key missing: preserve current model failure behavior; do not synthesize
  interview output.

Task defaults:

- Main question generation: `JAVA_SKILL`
- Answer evaluation: `INTERVIEWER`
- Report generation: `SCORE`

Existing endpoint semantics remain:

- Question generation failure keeps `QUESTION_GENERATION_PENDING` and returns
  503 through the controller.
- Evaluation failure keeps the current question unanswered and returns 503.
- Report failure keeps `SCORING_PENDING` and returns retryable pending state.

## Testing Plan

### Agent JSON Tests

`InterviewAgentJsonClientTest` covers strict parsing for:

- `RouterDecision`
- `RagQueryPlan`
- `InterviewAgentOutput`
- `MemoryWriteDecision`

It rejects:

- markdown fenced JSON
- blank output
- missing required fields
- illegal enum values
- invalid confidence or `topK`

### Orchestrator Tests

`InterviewAgentOrchestratorTest` covers:

- Router selects `PROJECT`, `JAVA_SKILL`, and `SCORE` and the matching target
  agent is called.
- Router JSON failure falls back to the task default agent.
- Router illegal target falls back to the task default agent.
- Planner JSON failure uses direct fallback retrieval.
- RAG failure still calls the selected target agent with empty evidence.
- Target agent JSON failure propagates as AI unavailable.
- MemoryManager failure does not block the successful target output.
- MemoryManager success invokes allowed memory write boundaries.

### Service Regression Tests

Existing service tests continue to prove:

- create/resume behavior
- answer progression
- two-follow-up maximum
- fifth-thread completion
- voluntary finish
- scoring retry
- optimistic conflict reload

### HTTP Flow Tests

`AuthenticatedInterviewFlowTest` is extended to use fake Router, Planner,
target agents, and MemoryManager.

It proves:

- `/api/interviews` still completes a five-question interview.
- Router/Planner/RAG/MemoryManager failures follow the degradation strategy.
- Target agent failures preserve current 503/202 behavior.
- Responses never expose internal evaluation, decision reason, evidence IDs, or
  raw model JSON.

## Out of Scope

- No changes to `/api/chat`.
- No new frontend interview UI.
- No candidate management backend.
- No new public agent-debug endpoint.
- No requirement to run real pgvector/model integration in unit tests.

## Acceptance Criteria

- `/api/interviews` uses the multi-agent orchestration layer for question
  generation, answer evaluation, and report generation.
- Router, Planner, target agents, and MemoryManager have explicit interfaces and
  strict JSON contracts.
- Router participates in every AI action with task-specific allowed agents.
- Auxiliary agent failures degrade according to this spec.
- Target agent JSON failures produce explicit retryable interview states instead
  of fabricated output.
- Existing authenticated interview lifecycle behavior and public response shape
  remain unchanged.
- Tests cover the main routing branches and degradation strategies.
