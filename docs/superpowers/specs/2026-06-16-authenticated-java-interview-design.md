# Authenticated Java Technical Interview Design

Date: 2026-06-16

## Goal

Extend the completed RuoYi authentication flow into an API-only Java
technical interview workflow:

```text
RuoYi login
  -> create or resume the current interview
  -> answer main questions and AI follow-ups
  -> finish automatically or voluntarily
  -> receive an AI-generated score report
```

The first version supports the fixed `JAVA_BACKEND` direction and the
`JUNIOR`, `MIDDLE`, and `SENIOR` difficulty levels. Each interview contains at
most five main questions. Follow-ups do not count toward this limit, and each
main question permits at most two consecutive follow-ups.

## Scope

This design includes:

- Authenticated interview creation and recovery.
- One active interview per RuoYi user.
- AI-generated main questions.
- AI evaluation of every submitted answer.
- AI-selected follow-ups, with a Java-enforced maximum of two per main
  question.
- Internal answer evaluations that are persisted but not shown to candidates.
- Automatic completion after five main-question threads.
- Voluntary completion after at least one answer.
- AI-generated final score reports.
- PostgreSQL persistence using the existing database configuration.
- Safe retry behavior for AI and database failures.

This design does not include:

- Interview administration or candidate-management pages.
- A visual login or interview UI.
- Multiple simultaneous interviews for one user.
- Arbitrary interview directions or custom skill lists.
- Fixed question-bank management.
- Per-answer scores displayed to candidates.
- Replacing the existing `/api/chat` endpoint.

## Current Context

The application already provides:

- A narrow RuoYi login and logout proxy.
- `GET /api/auth/me` for local session identity.
- `AuthenticatedUserContext` backed by Sa-Token and shared Redis.
- An authenticated `POST /api/chat` endpoint.
- Short-term memory, long-term memory, embeddings, and knowledge retrieval.
- PostgreSQL and pgvector configuration.

The existing chat service is a general conversational path. Interview state,
question sequencing, follow-up limits, completion rules, and scoring are not
currently implemented. The interview feature therefore receives its own API
and domain model instead of encoding interview behavior into `/api/chat`.

## Chosen Approach

Use a deterministic Java interview state machine with AI-driven questioning,
answer evaluation, follow-up selection, and final scoring.

Java owns:

- Authentication and user isolation.
- One-active-interview enforcement.
- Persistence and transaction boundaries.
- Current-question validation.
- Main-question and follow-up counters.
- The five-main-question limit.
- The two-follow-up-per-main-question limit.
- Completion and cancellation rules.
- AI output validation and safe retry behavior.

AI owns:

- Generating the first and subsequent main questions.
- Evaluating every main-question and follow-up answer.
- Deciding whether the current answer warrants another follow-up.
- Generating each follow-up question.
- Generating the final score report.

This separation keeps business invariants deterministic without reducing the
adaptive nature of the interview.

## Alternatives Considered

### Multi-Agent Dynamic Router

A router could select interviewer, Java-skill, project, and scoring agents on
every turn. This is flexible but gives model output too much influence over
workflow correctness and makes retry, persistence, and concurrency behavior
harder to reason about.

### Reuse `/api/chat`

The current chat endpoint could interpret messages such as "start interview"
and infer state from conversation memory. This minimizes new endpoints but
mixes conversational memory with authoritative interview state and makes
resume, duplicate submission, and scoring contracts ambiguous.

### Deterministic State Machine With AI Decisions

This is the selected approach. It gives API clients explicit progress and
stable lifecycle rules while allowing AI to adapt questions and follow-ups to
the candidate's answers.

## Domain Model

### InterviewSession

An interview session contains:

```text
id
userId
direction: JAVA_BACKEND
difficulty: JUNIOR / MIDDLE / SENIOR
status
mainQuestionCount
currentQuestionId
answeredQuestionCount
version
createdAt
updatedAt
completedAt optional
```

Statuses:

```text
QUESTION_GENERATION_PENDING
IN_PROGRESS
SCORING_PENDING
COMPLETED
CANCELLED
```

`QUESTION_GENERATION_PENDING` represents an interview that exists but whose
next candidate-visible main question could not yet be generated. It may occur
before main question 1 or between two main-question threads. While pending,
`currentQuestionId` is empty and no answer is accepted.

`SCORING_PENDING` represents an interview whose questioning has ended but
whose report generation has not yet succeeded.

`COMPLETED` requires a persisted final report. `CANCELLED` is used only when a
candidate voluntarily ends an interview before submitting any answer.

### InterviewQuestion

Each question contains:

```text
id
interviewId
type: MAIN / FOLLOW_UP
mainQuestionNumber: 1..5
followUpNumber: 0 for MAIN, 1..2 for FOLLOW_UP
parentQuestionId optional
text
skillTags
evidenceIds internal
status: WAITING_FOR_ANSWER / ANSWERED
createdAt
answeredAt optional
```

A follow-up belongs to the current main-question thread. It does not increment
`mainQuestionCount`.

### InterviewAnswer

Each answer contains:

```text
id
interviewId
questionId
answerText
internalEvaluation
abilityTags
aiDecision: FOLLOW_UP / NEXT_MAIN_QUESTION / FINISH
decisionReason
createdAt
```

`questionId` is unique in the answer table. This prevents one question from
being answered twice and advancing the interview more than once.

Internal evaluations, ability tags, decision reasons, reference answers, and
model reasoning are never returned to the candidate.

### InterviewReport

The report contains:

```text
interviewId
overallScore: 0..100
javaFundamentalsScore: 0..100
concurrencyScore: 0..100
jvmScore: 0..100
springScore: 0..100
databaseScore: 0..100
engineeringScore: 0..100
strengths
weaknesses
improvementSuggestions
createdAt
```

The report is based on all main questions, follow-ups, candidate answers, and
persisted internal evaluations.

## State Machine

### Creation

```text
no active interview
  -> create QUESTION_GENERATION_PENDING session
  -> AI generates main question 1
  -> persist question
  -> IN_PROGRESS
```

If the user already has an unfinished interview, creation returns that
interview's current state instead of creating a second one.

### Answer Progression

For every current question:

```text
candidate submits answer
  -> AI evaluates answer
  -> AI returns FOLLOW_UP or NEXT_MAIN_QUESTION
  -> Java validates the decision against hard limits
  -> persist answer, evaluation, and the validated decision atomically
```

If AI requests `FOLLOW_UP` and the current main-question thread has fewer than
two follow-ups, the generated follow-up becomes the next current question.

If two follow-ups have already been asked, Java overrides another follow-up
decision and advances to the next main question.

When a main-question thread ends:

- If fewer than five main questions have been generated, the session enters
  `QUESTION_GENERATION_PENDING` and AI generates the next main question.
- If main question 5 has ended, the interview enters `SCORING_PENDING` and AI
  generates the final report.

Main question 5 follows the same follow-up rules and may have zero, one, or two
follow-ups before scoring begins.

### Voluntary Finish

When the candidate calls the finish endpoint:

- With zero persisted answers, the interview becomes `CANCELLED`; no report is
  generated.
- With at least one persisted answer, the interview becomes
  `SCORING_PENDING`; AI generates a report from the available evidence.

Once an interview is `SCORING_PENDING`, no more answers are accepted.

### Retry States

AI output is validated before any state transition that depends on it:

- If next-question generation fails, the session remains
  `QUESTION_GENERATION_PENDING`.
- If answer evaluation or follow-up generation fails, the current question
  remains unanswered and the same `questionId` can be retried.
- If report generation fails, the session remains `SCORING_PENDING`.

`POST /api/interviews` is the retry command for
`QUESTION_GENERATION_PENDING`, including generation between main-question
threads. `POST /api/interviews/{interviewId}/finish` is the retry command for
`SCORING_PENDING`. The two GET endpoints are read-only and never call AI.

Retries must not create duplicate answers, questions, or reports.

## Components

### InterviewController

Responsibilities:

- Expose interview APIs.
- Resolve the RuoYi user through `AuthenticatedUserContext`.
- Validate request shape.
- Return candidate-safe structured responses.
- Map ownership failures to HTTP 404.

It does not call the model or database directly.

### InterviewService

Responsibilities:

- Enforce lifecycle and ownership rules.
- Create or resume the user's active interview.
- Validate the current `questionId`.
- Coordinate AI generation, evaluation, and scoring.
- Enforce question and follow-up limits independently of AI decisions.
- Execute state transitions through transactional repository operations.

### InterviewRepository

Responsibilities:

- Persist sessions, questions, answers, evaluations, and reports.
- Lock or version the active session during progression.
- Enforce uniqueness and ownership queries.
- Return complete interview aggregates for recovery and scoring.

The repository uses structured JDBC operations and typed row mapping rather
than constructing SQL from request values.

### InterviewQuestionGenerator

Generates a main question from:

- Direction and difficulty.
- Current main-question number.
- Previous questions and answers.
- Persisted internal evaluations.
- Relevant knowledge evidence.

It must return a validated structured result containing question text, skill
tags, and internal evidence identifiers.

### InterviewAnswerEvaluator

Evaluates each submitted answer and returns:

```text
internalEvaluation
abilityTags
decision: FOLLOW_UP / NEXT_MAIN_QUESTION
followUpQuestion optional
decisionReason
```

`followUpQuestion` is required only for a permitted `FOLLOW_UP` decision.

### InterviewReportGenerator

Generates the final report from the complete persisted interview aggregate.
It cannot change interview ownership, question counts, answers, or status.

### Knowledge Retrieval

Question generation and answer evaluation may retrieve technical evidence
through the existing embedding and knowledge-retrieval infrastructure.

Retrieval failure is non-fatal: AI proceeds without evidence, while the
failure is logged without exposing internal details to the candidate.

## API Contract

Every endpoint requires the configured RuoYi token header.

### Create or Resume Interview

```http
POST /api/interviews
Content-Type: application/json
```

Request:

```json
{
  "direction": "JAVA_BACKEND",
  "difficulty": "MIDDLE"
}
```

If there is no active interview, the service creates one and attempts to
generate main question 1. If an unfinished interview already exists, the
service ignores the new creation parameters. If that interview is
`QUESTION_GENERATION_PENDING`, this call retries generation; otherwise it
returns the current state without changing it.

Successful response:

```json
{
  "interviewId": "018f...",
  "status": "IN_PROGRESS",
  "direction": "JAVA_BACKEND",
  "difficulty": "MIDDLE",
  "mainQuestionNumber": 1,
  "followUpNumber": 0,
  "nextAction": "MAIN_QUESTION",
  "question": {
    "questionId": "0190...",
    "text": "请说明 HashMap 在 Java 8 中的扩容过程。"
  },
  "report": null
}
```

If generation is still pending, the response exposes the pending status but
does not expose an internal model error.

### Get Current Interview

```http
GET /api/interviews/current
```

Returns the authenticated user's unfinished interview. If none exists, returns
HTTP 404.

### Get Interview

```http
GET /api/interviews/{interviewId}
```

Returns the owned interview, current candidate-visible question, progress, and
final report when available. A missing interview or an interview owned by
another user returns HTTP 404.

### Submit Answer

```http
POST /api/interviews/{interviewId}/answers
Content-Type: application/json
```

Request:

```json
{
  "questionId": "0190...",
  "answer": "HashMap 的容量通常保持为 2 的幂..."
}
```

In-progress follow-up response:

```json
{
  "interviewId": "018f...",
  "status": "IN_PROGRESS",
  "mainQuestionNumber": 2,
  "followUpNumber": 1,
  "nextAction": "FOLLOW_UP",
  "question": {
    "questionId": "0191...",
    "text": "为什么容量为 2 的幂可以优化桶下标计算？"
  },
  "report": null
}
```

Possible `nextAction` values:

- `FOLLOW_UP`
- `MAIN_QUESTION`
- `REPORT_PENDING`
- `REPORT`
- `CANCELLED`

The response never includes internal evaluations, decision reasons, evidence
identifiers, reference answers, or model reasoning.

Only the current `WAITING_FOR_ANSWER` question may be answered. A blank answer
returns HTTP 400. A stale or unrelated `questionId` returns HTTP 409.

If the exact question was already answered, the service returns the current
latest interview representation without inserting another answer or advancing
the state again. If that state is `QUESTION_GENERATION_PENDING`, the client
uses `POST /api/interviews` to retry generation.

### Finish Interview

```http
POST /api/interviews/{interviewId}/finish
```

With no answers, returns a `CANCELLED` interview and no report.

With one or more answers, begins or retries scoring and returns either:

- HTTP 202 with `SCORING_PENDING` and `REPORT_PENDING`, or
- `COMPLETED` with `REPORT`.

Calling finish again is idempotent.

## Persistence Design

Reuse the existing PostgreSQL connection configuration and provide shared
`JdbcOperations` independently of whether pgvector retrieval is enabled.

Tables:

- `interview_session`
- `interview_question`
- `interview_answer`
- `interview_report`

Required constraints:

- One unfinished interview per `user_id`, implemented with a PostgreSQL partial
  unique index over unfinished statuses.
- Unique `interview_answer.question_id`.
- Unique `(interview_id, main_question_number, follow_up_number)` question
  position.
- Main-question number constrained to `1..5`.
- Follow-up number constrained to `0..2`.
- Follow-up rows require a parent main question.
- One report per interview.

The session row includes a version column. Answer submission and state
progression use either optimistic version checks or a `SELECT ... FOR UPDATE`
lock inside one transaction. On a conflict, the service reloads and returns
the latest state instead of producing duplicate work.

Model calls must not hold a database lock for their full network duration.
The service reads a versioned snapshot, calls AI, then conditionally commits
the result only if the current question and version still match.

## AI Contracts

All model-facing operations require strict structured output.

### Main Question Output

```json
{
  "question": "string",
  "skillTags": ["JVM"],
  "evidenceIds": ["knowledge-123"]
}
```

The question must be non-blank. Skill tags and evidence IDs are internal.

### Answer Evaluation Output

```json
{
  "internalEvaluation": "string",
  "abilityTags": ["CONCURRENCY"],
  "decision": "FOLLOW_UP",
  "followUpQuestion": "string",
  "decisionReason": "string"
}
```

For `NEXT_MAIN_QUESTION`, `followUpQuestion` must be absent or blank. For a
permitted `FOLLOW_UP`, it must be non-blank.

### Final Report Output

```json
{
  "overallScore": 78,
  "scores": {
    "javaFundamentals": 82,
    "concurrency": 70,
    "jvm": 75,
    "spring": 80,
    "database": 76,
    "engineering": 84
  },
  "strengths": ["string"],
  "weaknesses": ["string"],
  "improvementSuggestions": ["string"]
}
```

Every score must be an integer from 0 through 100. Required text lists must be
present and contain non-blank values.

Prompts and logs must not expose RuoYi tokens, credentials, Redis settings, or
other users' interview data.

## Error Handling

| Condition | Result |
| --- | --- |
| Missing or invalid RuoYi token | HTTP 401 |
| Unsupported direction or difficulty | HTTP 400 |
| Blank answer | HTTP 400 |
| Interview absent or owned by another user | HTTP 404 |
| Stale or non-current question | HTTP 409 |
| Interview no longer accepts answers | HTTP 409 |
| Duplicate answer retry | Return current state without another transition |
| AI question/evaluation failure | HTTP 503; retain retryable state |
| AI scoring failure | HTTP 202 with `SCORING_PENDING`; retry with finish |
| Database version conflict | Reload and return latest state |
| Knowledge retrieval failure | Continue without evidence |

Local errors must not include model prompts, internal evaluations, database
details, tokens, or upstream service URLs.

## Security and Isolation

- Every repository lookup is scoped by both `interviewId` and authenticated
  `userId`.
- Cross-user access returns HTTP 404.
- Direction, difficulty, question IDs, and answers are request data; ownership,
  counters, state, and report scores come only from persisted server state.
- Candidate answers and AI evaluations are not logged.
- The candidate never receives internal evaluation, AI decision reason,
  evidence IDs, reference material, or raw model output.
- The existing configured RuoYi token header remains the only authentication
  input.

## Testing Strategy

### State Machine Tests

- Creation generates main question 1.
- A user with an unfinished interview receives the existing interview.
- Five main questions complete automatically.
- Follow-ups do not increment the main-question count.
- AI can select zero, one, or two follow-ups.
- A requested third follow-up is overridden by Java.
- Main question 5 still permits up to two follow-ups before scoring.
- Voluntary finish with zero answers cancels without a report.
- Voluntary finish after one or more answers produces a report.
- Scoring-pending interviews reject additional answers.

### AI Contract Tests

- Valid main-question, evaluation, and report JSON is parsed.
- Missing fields, invalid decisions, blank questions, and out-of-range scores
  are rejected.
- Evaluation failure leaves the question answerable.
- Question-generation failure leaves a generation-pending state.
- Scoring failure leaves a scoring-pending state.
- Retry succeeds without duplicate rows.

### Repository Tests

- The same user cannot own two unfinished interviews.
- Different users can have independent active interviews.
- Only one answer can exist for one question.
- Question-position constraints enforce main and follow-up limits.
- Version or lock conflicts cannot advance an interview twice.
- Persisted interviews can be recovered after application restart.

### HTTP Contract Tests

- A RuoYi token can create an interview and submit answers.
- Missing or invalid tokens return HTTP 401.
- Cross-user lookup returns HTTP 404.
- Stale question submission returns HTTP 409.
- Duplicate submission returns the latest state without another transition.
- Responses expose structured progress and final reports.
- Responses do not expose internal evaluations or AI reasoning.

### End-to-End Flow

Automated fakes prove:

```text
login token
  -> create interview
  -> answer five main-question threads
  -> enforce at most two follow-ups per thread
  -> generate report
  -> retrieve completed interview
```

A real-environment smoke test additionally requires RuoYi, shared Redis,
PostgreSQL, the configured model endpoint, embeddings, and knowledge retrieval
dependencies to be available.

## Acceptance Criteria

- An authenticated RuoYi user can create or resume one active Java interview.
- Creation accepts only `JAVA_BACKEND` and a supported difficulty.
- The interview contains no more than five main questions.
- Each main question contains no more than two follow-ups.
- Follow-ups are selected and written by AI but constrained by Java.
- Every answer receives a persisted internal AI evaluation that is not exposed
  to the candidate.
- The interview completes after the fifth main-question thread or when an
  eligible candidate voluntarily finishes it.
- Voluntary finish with zero answers cancels without scoring.
- The final AI report contains the overall score, six component scores,
  strengths, weaknesses, and improvement suggestions.
- All interview state survives application restart.
- Duplicate and concurrent submissions do not duplicate answers or advance the
  interview more than once.
- Cross-user interview access is prevented.
- The feature uses explicit `/api/interviews` contracts and does not overload
  `/api/chat`.
