# Authenticated Java Technical Interview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add authenticated, persistent Java technical interviews with five AI-generated main questions, at most two AI-selected follow-ups per main question, voluntary finish, and an AI-generated score report.

**Architecture:** Add a separate `/api/interviews` domain driven by a deterministic Java state machine. PostgreSQL stores authoritative session, question, answer, internal-evaluation, and report state; model adapters generate strictly parsed JSON outside database transactions, and optimistic version checks commit results only when the interview snapshot is still current.

**Tech Stack:** Java 17, Spring Boot 4.0.6, Spring MVC, Spring JDBC, Spring transactions, PostgreSQL, Jackson, AgentScope `ChatTextModel`, Sa-Token authentication, JUnit 5, Mockito, MockMvc

---

## Working-Tree Constraint

The repository already contains uncommitted retrieval and RuoYi authentication
work. Do not reset, revert, reformat, or stage unrelated files. Every commit in
this plan stages only the paths explicitly listed for that task.

The authenticated-interview design is:

`docs/superpowers/specs/2026-06-16-authenticated-java-interview-design.md`

## File Structure

Create:

- `src/main/java/com/example/demoscope/InterviewSession.java` - session enums, counters, status, and immutable validation.
- `src/main/java/com/example/demoscope/InterviewQuestion.java` - main/follow-up question state.
- `src/main/java/com/example/demoscope/InterviewAnswer.java` - candidate answer and private AI evaluation.
- `src/main/java/com/example/demoscope/InterviewReport.java` - validated final scores and feedback.
- `src/main/java/com/example/demoscope/InterviewSnapshot.java` - complete persisted aggregate and safe lookup helpers.
- `src/main/java/com/example/demoscope/InterviewAiContracts.java` - validated generated-question, answer-evaluation, and report-draft contracts.
- `src/main/java/com/example/demoscope/InterviewRepository.java` - optimistic persistence port.
- `src/main/java/com/example/demoscope/JdbcInterviewRepository.java` - PostgreSQL implementation.
- `src/main/java/com/example/demoscope/InterviewDatabaseConfig.java` - interview DataSource, JDBC, transactions, and schema initialization.
- `src/main/resources/interview-schema.sql` - interview-only PostgreSQL schema.
- `src/main/java/com/example/demoscope/InterviewEvidenceProvider.java` - non-fatal knowledge retrieval boundary.
- `src/main/java/com/example/demoscope/InterviewAiJsonClient.java` - strict model JSON parsing.
- `src/main/java/com/example/demoscope/InterviewQuestionGenerator.java` - main-question generation port.
- `src/main/java/com/example/demoscope/InterviewAnswerEvaluator.java` - private answer-evaluation port.
- `src/main/java/com/example/demoscope/InterviewReportGenerator.java` - final-report generation port.
- `src/main/java/com/example/demoscope/ModelInterviewQuestionGenerator.java` - AI main-question prompt.
- `src/main/java/com/example/demoscope/ModelInterviewAnswerEvaluator.java` - AI evaluation and follow-up prompt.
- `src/main/java/com/example/demoscope/ModelInterviewReportGenerator.java` - AI scoring prompt.
- `src/main/java/com/example/demoscope/InterviewServiceException.java` - typed candidate-safe failures.
- `src/main/java/com/example/demoscope/InterviewService.java` - state machine and optimistic orchestration.
- `src/main/java/com/example/demoscope/InterviewController.java` - authenticated HTTP contract and safe response DTOs.
- `src/main/java/com/example/demoscope/InterviewConfig.java` - interview service and AI beans.
- Corresponding focused test classes under `src/test/java/com/example/demoscope/`.

Modify:

- `src/main/java/com/example/demoscope/AgentMemoryConfig.java` - reuse interview JDBC when interview and pgvector are both enabled.
- `src/main/resources/application.properties` - add interview enablement and limits.
- Existing `@SpringBootTest` classes - disable interview database startup where the test is unrelated.

## Task 1: Define and Validate the Interview Domain

**Files:**
- Create: `src/main/java/com/example/demoscope/InterviewSession.java`
- Create: `src/main/java/com/example/demoscope/InterviewQuestion.java`
- Create: `src/main/java/com/example/demoscope/InterviewAnswer.java`
- Create: `src/main/java/com/example/demoscope/InterviewReport.java`
- Create: `src/main/java/com/example/demoscope/InterviewSnapshot.java`
- Create: `src/main/java/com/example/demoscope/InterviewAiContracts.java`
- Test: `src/test/java/com/example/demoscope/InterviewDomainTest.java`

- [ ] **Step 1: Write failing validation tests**

Create `InterviewDomainTest.java` with these cases:

```java
package com.example.demoscope;

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
                UUID.randomUUID(), interviewId, 1, "Explain HashMap.",
                List.of("JAVA"), List.of("k-1"), Instant.EPOCH);
        InterviewQuestion followUp = InterviewQuestion.followUp(
                UUID.randomUUID(), interviewId, 1, 2, main.id(),
                "Why powers of two?", List.of("JAVA"), List.of(), Instant.EPOCH);

        assertEquals(0, main.followUpNumber());
        assertEquals(2, followUp.followUpNumber());
    }

    @Test
    void rejectsMoreThanFiveMainQuestionsOrTwoFollowUps() {
        UUID interviewId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> InterviewQuestion.main(
                UUID.randomUUID(), interviewId, 6, "bad", List.of(), List.of(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> InterviewQuestion.followUp(
                UUID.randomUUID(), interviewId, 1, 3, UUID.randomUUID(),
                "bad", List.of(), List.of(), Instant.EPOCH));
    }

    @Test
    void rejectsInvalidReportScoresAndBlankFeedback() {
        assertThrows(IllegalArgumentException.class, () -> new InterviewReport(
                UUID.randomUUID(), 101, 80, 80, 80, 80, 80, 80,
                List.of("strength"), List.of("weakness"), List.of("improve"), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new InterviewReport(
                UUID.randomUUID(), 80, 80, 80, 80, 80, 80, 80,
                List.of(" "), List.of("weakness"), List.of("improve"), Instant.EPOCH));
    }

    @Test
    void answerEvaluationRequiresFollowUpTextOnlyForFollowUpDecision() {
        assertThrows(IllegalArgumentException.class, () ->
                new InterviewAiContracts.AnswerEvaluation(
                        "partial", List.of("JVM"),
                        InterviewAnswer.Decision.FOLLOW_UP, "", "needs detail"));

        InterviewAiContracts.AnswerEvaluation next =
                new InterviewAiContracts.AnswerEvaluation(
                        "good", List.of("JVM"),
                        InterviewAnswer.Decision.NEXT_MAIN_QUESTION, null, "complete");

        assertEquals(InterviewAnswer.Decision.NEXT_MAIN_QUESTION, next.decision());
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewDomainTest' test
```

Expected: test compilation fails because the interview domain types do not
exist.

- [ ] **Step 3: Implement immutable domain records**

Implement `InterviewSession` with nested enums:

```java
public record InterviewSession(
        UUID id,
        String userId,
        Direction direction,
        Difficulty difficulty,
        Status status,
        int mainQuestionCount,
        UUID currentQuestionId,
        int answeredQuestionCount,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public enum Direction { JAVA_BACKEND }
    public enum Difficulty { JUNIOR, MIDDLE, SENIOR }
    public enum Status {
        QUESTION_GENERATION_PENDING,
        IN_PROGRESS,
        SCORING_PENDING,
        COMPLETED,
        CANCELLED
    }

    public InterviewSession {
        Objects.requireNonNull(id, "id");
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(status, "status");
        if (mainQuestionCount < 0 || mainQuestionCount > 5) {
            throw new IllegalArgumentException("mainQuestionCount must be between 0 and 5");
        }
        if (answeredQuestionCount < 0 || version < 0) {
            throw new IllegalArgumentException("counts and version must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean unfinished() {
        return status != Status.COMPLETED && status != Status.CANCELLED;
    }
}
```

Implement `InterviewQuestion` with `Type { MAIN, FOLLOW_UP }`,
`Status { WAITING_FOR_ANSWER, ANSWERED }`, defensive `List.copyOf`, and the
`main(...)` and `followUp(...)` factories used by the test. Require:

- main question number `1..5`
- main question follow-up number `0` and no parent
- follow-up number `1..2` and a non-null parent
- non-blank text

Implement `InterviewAnswer`:

```java
public record InterviewAnswer(
        UUID id,
        UUID interviewId,
        UUID questionId,
        String answerText,
        String internalEvaluation,
        List<String> abilityTags,
        Decision decision,
        String decisionReason,
        Instant createdAt) {

    public enum Decision { FOLLOW_UP, NEXT_MAIN_QUESTION }
}
```

Require all text except ability tags to be non-blank and copy the tag list.

Implement `InterviewReport` with seven integer scores, three non-empty
non-blank string lists, and `0..100` validation.

Implement `InterviewSnapshot`:

```java
public record InterviewSnapshot(
        InterviewSession session,
        List<InterviewQuestion> questions,
        List<InterviewAnswer> answers,
        InterviewReport report) {

    public InterviewSnapshot {
        Objects.requireNonNull(session, "session");
        questions = List.copyOf(questions);
        answers = List.copyOf(answers);
    }

    public Optional<InterviewQuestion> currentQuestion() {
        UUID currentId = session.currentQuestionId();
        return currentId == null ? Optional.empty()
                : questions.stream().filter(q -> q.id().equals(currentId)).findFirst();
    }

    public Optional<InterviewAnswer> answerFor(UUID questionId) {
        return answers.stream().filter(a -> a.questionId().equals(questionId)).findFirst();
    }
}
```

Implement `InterviewAiContracts` as a non-instantiable utility class containing
validated nested records:

```java
public record GeneratedQuestion(
        String question,
        List<String> skillTags,
        List<String> evidenceIds) {}

public record AnswerEvaluation(
        String internalEvaluation,
        List<String> abilityTags,
        InterviewAnswer.Decision decision,
        String followUpQuestion,
        String decisionReason) {}

public record ReportDraft(
        int overallScore,
        int javaFundamentalsScore,
        int concurrencyScore,
        int jvmScore,
        int springScore,
        int databaseScore,
        int engineeringScore,
        List<String> strengths,
        List<String> weaknesses,
        List<String> improvementSuggestions) {}
```

Use the same score and non-blank validation as the persisted report.

- [ ] **Step 4: Run domain tests and verify GREEN**

Run the command from Step 2.

Expected: `InterviewDomainTest` passes.

- [ ] **Step 5: Commit the domain model**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewSession.java src/main/java/com/example/demoscope/InterviewQuestion.java src/main/java/com/example/demoscope/InterviewAnswer.java src/main/java/com/example/demoscope/InterviewReport.java src/main/java/com/example/demoscope/InterviewSnapshot.java src/main/java/com/example/demoscope/InterviewAiContracts.java src/test/java/com/example/demoscope/InterviewDomainTest.java
git commit -m "feat: define java interview domain"
```

## Task 2: Add Shared Interview PostgreSQL Infrastructure

**Files:**
- Create: `src/main/java/com/example/demoscope/InterviewDatabaseConfig.java`
- Create: `src/main/resources/interview-schema.sql`
- Create: `src/test/java/com/example/demoscope/InterviewDatabaseConfigTest.java`
- Modify: `src/main/java/com/example/demoscope/AgentMemoryConfig.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/com/example/demoscope/AgentChatControllerTest.java`
- Modify: `src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java`
- Modify: `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`
- Modify: `src/test/java/com/example/demoscope/OpenAiModelConfigTest.java`

- [ ] **Step 1: Write failing configuration tests**

Create `InterviewDatabaseConfigTest.java`:

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class InterviewDatabaseConfigTest {

    @Test
    void buildsPostgresDataSourceFromExistingPgvectorProperties() {
        DataSource dataSource = InterviewDatabaseConfig.dataSource(
                "jdbc:postgresql://localhost:5432/agent", "agent", "secret");

        assertTrue(dataSource instanceof DriverManagerDataSource);
    }

    @Test
    void rejectsBlankDatabaseUrlWhenInterviewIsEnabled() {
        assertThrows(
                IllegalStateException.class,
                () -> InterviewDatabaseConfig.dataSource("", "", ""));
    }
}
```

Also add a static schema assertion:

```java
@Test
void schemaContainsInterviewConstraints() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/interview-schema.sql"));
    assertTrue(sql.contains("interview_session_one_active_user_idx"));
    assertTrue(sql.contains("follow_up_number between 0 and 2"));
    assertTrue(sql.contains("unique (question_id)"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewDatabaseConfigTest' test
```

Expected: compilation fails because `InterviewDatabaseConfig` and
`interview-schema.sql` do not exist.

- [ ] **Step 3: Add the interview schema**

Create `interview-schema.sql` with these tables and constraints:

```sql
create table if not exists interview_session (
    id uuid primary key,
    user_id text not null,
    direction varchar(32) not null check (direction = 'JAVA_BACKEND'),
    difficulty varchar(16) not null check (difficulty in ('JUNIOR', 'MIDDLE', 'SENIOR')),
    status varchar(40) not null check (status in (
        'QUESTION_GENERATION_PENDING',
        'IN_PROGRESS',
        'SCORING_PENDING',
        'COMPLETED',
        'CANCELLED'
    )),
    main_question_count integer not null check (main_question_count between 0 and 5),
    current_question_id uuid,
    answered_question_count integer not null check (answered_question_count >= 0),
    version bigint not null check (version >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create unique index if not exists interview_session_one_active_user_idx
    on interview_session (user_id)
    where status in (
        'QUESTION_GENERATION_PENDING',
        'IN_PROGRESS',
        'SCORING_PENDING'
    );

create table if not exists interview_question (
    id uuid primary key,
    interview_id uuid not null references interview_session(id) on delete cascade,
    type varchar(16) not null check (type in ('MAIN', 'FOLLOW_UP')),
    main_question_number integer not null check (main_question_number between 1 and 5),
    follow_up_number integer not null check (follow_up_number between 0 and 2),
    parent_question_id uuid references interview_question(id),
    text text not null check (length(trim(text)) > 0),
    skill_tags_json text not null,
    evidence_ids_json text not null,
    status varchar(32) not null check (status in ('WAITING_FOR_ANSWER', 'ANSWERED')),
    created_at timestamptz not null,
    answered_at timestamptz,
    unique (interview_id, main_question_number, follow_up_number),
    check (
        (type = 'MAIN' and follow_up_number = 0 and parent_question_id is null)
        or
        (type = 'FOLLOW_UP' and follow_up_number between 1 and 2 and parent_question_id is not null)
    )
);

create table if not exists interview_answer (
    id uuid primary key,
    interview_id uuid not null references interview_session(id) on delete cascade,
    question_id uuid not null references interview_question(id) on delete cascade,
    answer_text text not null check (length(trim(answer_text)) > 0),
    internal_evaluation text not null check (length(trim(internal_evaluation)) > 0),
    ability_tags_json text not null,
    ai_decision varchar(32) not null check (ai_decision in ('FOLLOW_UP', 'NEXT_MAIN_QUESTION')),
    decision_reason text not null check (length(trim(decision_reason)) > 0),
    created_at timestamptz not null,
    unique (question_id)
);

create table if not exists interview_report (
    interview_id uuid primary key references interview_session(id) on delete cascade,
    overall_score integer not null check (overall_score between 0 and 100),
    java_fundamentals_score integer not null check (java_fundamentals_score between 0 and 100),
    concurrency_score integer not null check (concurrency_score between 0 and 100),
    jvm_score integer not null check (jvm_score between 0 and 100),
    spring_score integer not null check (spring_score between 0 and 100),
    database_score integer not null check (database_score between 0 and 100),
    engineering_score integer not null check (engineering_score between 0 and 100),
    strengths_json text not null,
    weaknesses_json text not null,
    improvement_suggestions_json text not null,
    created_at timestamptz not null
);
```

- [ ] **Step 4: Add conditional database configuration**

Create `InterviewDatabaseConfig`:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InterviewDatabaseConfig {

    @Bean("agentPostgresDataSource")
    DataSource agentPostgresDataSource(
            @Value("${agentscope.pgvector.url:}") String url,
            @Value("${agentscope.pgvector.username:}") String username,
            @Value("${agentscope.pgvector.password:}") String password) {
        return dataSource(url, username, password);
    }

    @Bean
    @ConditionalOnMissingBean(JdbcOperations.class)
    JdbcOperations agentJdbcOperations(
            @Qualifier("agentPostgresDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    TransactionOperations interviewTransactions(
            @Qualifier("agentPostgresDataSource") DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Bean
    DataSourceInitializer interviewSchemaInitializer(
            @Qualifier("agentPostgresDataSource") DataSource dataSource) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("interview-schema.sql"));
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    static DataSource dataSource(String url, String username, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "PGVECTOR_URL must be configured when interviews are enabled");
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
```

Keep `AgentMemoryConfig.pgVectorJdbcOperations` unchanged except for adding
`@ConditionalOnMissingBean(JdbcOperations.class)` if it is not already
present. This makes pgvector reuse the interview JDBC bean when both features
are enabled.

- [ ] **Step 5: Add configuration and isolate unrelated context tests**

Add:

```properties
agentscope.interview.enabled=${AGENTSCOPE_INTERVIEW_ENABLED:true}
agentscope.interview.max-main-questions=${AGENTSCOPE_INTERVIEW_MAX_MAIN_QUESTIONS:5}
agentscope.interview.max-follow-ups=${AGENTSCOPE_INTERVIEW_MAX_FOLLOW_UPS:2}
```

to `application.properties`.

Add this property to every unrelated `@SpringBootTest` property list:

```text
agentscope.interview.enabled=false
```

The affected classes are:

- `AgentChatControllerTest`
- `AgentRuntimeConfigControllerTest`
- `DemoScopeApplicationTests`
- `OpenAiModelConfigTest`

- [ ] **Step 6: Run configuration and existing context tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewDatabaseConfigTest,AgentChatControllerTest,AgentRuntimeConfigControllerTest,DemoScopeApplicationTests,OpenAiModelConfigTest' test
```

Expected: all tests pass without connecting to PostgreSQL.

- [ ] **Step 7: Commit database infrastructure**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewDatabaseConfig.java src/main/java/com/example/demoscope/AgentMemoryConfig.java src/main/resources/interview-schema.sql src/main/resources/application.properties src/test/java/com/example/demoscope/InterviewDatabaseConfigTest.java src/test/java/com/example/demoscope/AgentChatControllerTest.java src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java src/test/java/com/example/demoscope/DemoScopeApplicationTests.java src/test/java/com/example/demoscope/OpenAiModelConfigTest.java
git commit -m "feat: add interview postgres infrastructure"
```

## Task 3: Implement the Optimistic JDBC Repository

**Files:**
- Create: `src/main/java/com/example/demoscope/InterviewRepository.java`
- Create: `src/main/java/com/example/demoscope/JdbcInterviewRepository.java`
- Create: `src/test/java/com/example/demoscope/JdbcInterviewRepositoryTest.java`

- [ ] **Step 1: Define the repository port**

Create `InterviewRepository` with exact mutation boundaries:

```java
public interface InterviewRepository {

    Optional<InterviewSnapshot> findActiveByUserId(String userId);

    Optional<InterviewSnapshot> findByIdAndUserId(UUID interviewId, String userId);

    InterviewSnapshot createPending(
            UUID interviewId,
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty,
            Instant now);

    boolean addMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewQuestion question,
            Instant now);

    boolean recordAnswerAndFollowUp(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            InterviewQuestion followUp,
            Instant now);

    boolean recordAnswerAndAwaitMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now);

    boolean recordAnswerAndAwaitScoring(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now);

    boolean markScoringPending(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now);

    boolean cancel(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now);

    boolean completeReport(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewReport report,
            Instant now);
}
```

- [ ] **Step 2: Write failing repository behavior tests**

Create `JdbcInterviewRepositoryTest` using mocked `JdbcOperations` and a
`TransactionOperations` test double that executes callbacks immediately.
Cover:

```java
@Test
void createPendingInsertsVersionZeroSession() {
    repository.createPending(
            interviewId, "user-42",
            InterviewSession.Direction.JAVA_BACKEND,
            InterviewSession.Difficulty.MIDDLE,
            Instant.EPOCH);

    verify(jdbc).update(
            contains("insert into interview_session"),
            eq(interviewId),
            eq("user-42"),
            eq("JAVA_BACKEND"),
            eq("MIDDLE"),
            eq("QUESTION_GENERATION_PENDING"),
            eq(Instant.EPOCH),
            eq(Instant.EPOCH));
}

@Test
void optimisticMutationReturnsFalseWhenVersionChanged() {
    when(jdbc.update(
            contains("where id = ? and user_id = ? and version = ?"),
            any(Object[].class))).thenReturn(0);

    boolean changed = repository.markScoringPending(
            interviewId, "user-42", 4, Instant.EPOCH);

    assertFalse(changed);
}

@Test
void answerAndFollowUpAreWrittenInsideOneTransaction() {
    repository.recordAnswerAndFollowUp(
            interviewId, "user-42", 2, answer, followUp, Instant.EPOCH);

    verify(transactions).executeWithoutResult(any());
    verify(jdbc).update(contains("insert into interview_answer"), any(Object[].class));
    verify(jdbc).update(contains("insert into interview_question"), any(Object[].class));
    verify(jdbc).update(contains("status = 'IN_PROGRESS'"), any(Object[].class));
}
```

Also test that `findByIdAndUserId` scopes the session query by both ID and
user ID and never loads another user's aggregate.

- [ ] **Step 3: Run repository tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=JdbcInterviewRepositoryTest' test
```

Expected: compilation fails because the repository implementation does not
exist.

- [ ] **Step 4: Implement aggregate loading**

Implement `JdbcInterviewRepository` with:

- one scoped session query
- ordered question query by `main_question_number, follow_up_number`
- ordered answer query by `created_at`
- optional report query
- Jackson serialization for all JSON text columns

Use these session predicates:

```sql
where id = ? and user_id = ?
```

and:

```sql
where user_id = ?
  and status in (
      'QUESTION_GENERATION_PENDING',
      'IN_PROGRESS',
      'SCORING_PENDING'
  )
```

`findByIdAndUserId` returns `Optional.empty()` before running child queries if
the scoped session row is absent.

- [ ] **Step 5: Implement conditional mutations**

Each mutation runs inside `TransactionOperations.execute` and:

1. updates the session with `where id = ? and user_id = ? and version = ?`
2. stops and returns `false` if the update count is zero
3. inserts or updates child rows only after the version update succeeds

Use these state changes:

| Method | New status | Current question |
| --- | --- | --- |
| `addMainQuestion` | `IN_PROGRESS` | generated main question |
| `recordAnswerAndFollowUp` | `IN_PROGRESS` | generated follow-up |
| `recordAnswerAndAwaitMainQuestion` | `QUESTION_GENERATION_PENDING` | null |
| `recordAnswerAndAwaitScoring` | `SCORING_PENDING` | null |
| `markScoringPending` | `SCORING_PENDING` | null |
| `cancel` | `CANCELLED` | null |
| `completeReport` | `COMPLETED` | null |

Every successful mutation increments `version` and updates `updated_at`.
Answer mutations also:

- insert one `interview_answer`
- mark the answered question `ANSWERED`
- set `answered_at`
- increment `answered_question_count`

`addMainQuestion` increments `main_question_count`; follow-up insertion does
not.

- [ ] **Step 6: Run repository tests and verify GREEN**

Run the command from Step 3.

Expected: all repository tests pass.

- [ ] **Step 7: Commit the repository**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewRepository.java src/main/java/com/example/demoscope/JdbcInterviewRepository.java src/test/java/com/example/demoscope/JdbcInterviewRepositoryTest.java
git commit -m "feat: persist interview aggregates"
```

## Task 4: Add Strict AI JSON Adapters and Evidence Retrieval

**Files:**
- Create: `src/main/java/com/example/demoscope/InterviewEvidenceProvider.java`
- Create: `src/main/java/com/example/demoscope/InterviewAiJsonClient.java`
- Create: `src/main/java/com/example/demoscope/InterviewQuestionGenerator.java`
- Create: `src/main/java/com/example/demoscope/InterviewAnswerEvaluator.java`
- Create: `src/main/java/com/example/demoscope/InterviewReportGenerator.java`
- Create: `src/main/java/com/example/demoscope/ModelInterviewQuestionGenerator.java`
- Create: `src/main/java/com/example/demoscope/ModelInterviewAnswerEvaluator.java`
- Create: `src/main/java/com/example/demoscope/ModelInterviewReportGenerator.java`
- Create: `src/test/java/com/example/demoscope/InterviewAiJsonClientTest.java`
- Create: `src/test/java/com/example/demoscope/ModelInterviewAiTest.java`

- [ ] **Step 1: Write failing strict-JSON tests**

Create `InterviewAiJsonClientTest`:

```java
@Test
void parsesDirectJsonAndRejectsMarkdownFences() {
    ChatTextModel valid = (system, prompt) ->
            "{\"question\":\"Explain volatile\",\"skillTags\":[\"CONCURRENCY\"],\"evidenceIds\":[]}";
    InterviewAiJsonClient client = new InterviewAiJsonClient(valid, new ObjectMapper());

    GeneratedQuestion question =
            client.call("system", "prompt", GeneratedQuestion.class);

    assertEquals("Explain volatile", question.question());

    ChatTextModel fenced = (system, prompt) ->
            "```json\n{\"question\":\"bad\",\"skillTags\":[],\"evidenceIds\":[]}\n```";
    assertThrows(
            InterviewAiJsonClient.InvalidOutputException.class,
            () -> new InterviewAiJsonClient(fenced, new ObjectMapper())
                    .call("system", "prompt", GeneratedQuestion.class));
}
```

Add tests for missing required fields, invalid decisions, blank follow-up text,
and report scores above 100.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewAiJsonClientTest,ModelInterviewAiTest' test
```

Expected: compilation fails because the AI adapters do not exist.

- [ ] **Step 3: Implement the strict JSON client**

Implement:

```java
public final class InterviewAiJsonClient {

    private final ChatTextModel model;
    private final ObjectMapper objectMapper;

    public <T> T call(String systemPrompt, String userPrompt, Class<T> type) {
        String raw = model.generate(systemPrompt, userPrompt);
        try {
            if (raw == null || raw.isBlank() || raw.contains("```")) {
                throw new InvalidOutputException("AI returned invalid JSON");
            }
            return objectMapper.readValue(raw, type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidOutputException("AI returned invalid JSON", exception);
        }
    }

    public static final class InvalidOutputException extends RuntimeException {
        public InvalidOutputException(String message) {
            super(message);
        }

        public InvalidOutputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

Do not log raw prompts, candidate answers, evaluations, or model responses.

- [ ] **Step 4: Implement non-fatal evidence retrieval**

Create `InterviewEvidenceProvider`:

```java
public class InterviewEvidenceProvider {

    private final EmbeddingClient embeddingClient;
    private final KnowledgeRetriever knowledgeRetriever;

    public List<KnowledgeChunk> retrieve(String query) {
        try {
            float[] embedding = embeddingClient.embed(query);
            return knowledgeRetriever.retrieve(new SemanticQuery(query, embedding));
        } catch (RuntimeException exception) {
            log.warn("Interview evidence retrieval failed");
            return List.of();
        }
    }
}
```

The warning must not include the query or candidate answer.

- [ ] **Step 5: Implement the three model adapters**

Each adapter receives an `InterviewAiJsonClient`. The question generator and
evaluator also receive `InterviewEvidenceProvider`.

Use these interfaces:

```java
@FunctionalInterface
public interface InterviewQuestionGenerator {
    GeneratedQuestion generate(InterviewSnapshot snapshot, int mainQuestionNumber);
}

@FunctionalInterface
public interface InterviewAnswerEvaluator {
    AnswerEvaluation evaluate(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer);
}

@FunctionalInterface
public interface InterviewReportGenerator {
    ReportDraft generate(InterviewSnapshot snapshot);
}
```

Create the three public interfaces in:

- `InterviewQuestionGenerator.java`
- `InterviewAnswerEvaluator.java`
- `InterviewReportGenerator.java`

Question system prompt:

```text
You are a Java backend technical interviewer.
Return one JSON object only. Do not use markdown.
Generate exactly one main question suitable for the requested difficulty.
Do not include an answer, hints, scoring, or private reasoning.
Schema:
{"question":"non-blank","skillTags":["tag"],"evidenceIds":["id"]}
```

Evaluation system prompt:

```text
You evaluate one candidate answer in a Java backend interview.
Return one JSON object only. Do not use markdown.
Choose FOLLOW_UP only when one focused clarification would materially improve
the evidence. Otherwise choose NEXT_MAIN_QUESTION.
Never reveal the internal evaluation to the candidate.
Schema:
{"internalEvaluation":"non-blank","abilityTags":["tag"],
"decision":"FOLLOW_UP|NEXT_MAIN_QUESTION",
"followUpQuestion":"required only for FOLLOW_UP",
"decisionReason":"non-blank"}
```

Report system prompt:

```text
You score a completed Java backend interview.
Return one JSON object only. Do not use markdown.
Base the report only on the supplied questions, answers, and evaluations.
All seven scores are integers from 0 to 100.
Schema:
{"overallScore":0,"javaFundamentalsScore":0,"concurrencyScore":0,
"jvmScore":0,"springScore":0,"databaseScore":0,"engineeringScore":0,
"strengths":["non-blank"],"weaknesses":["non-blank"],
"improvementSuggestions":["non-blank"]}
```

User prompts include direction, difficulty, numbered transcript, and retrieved
knowledge text. They do not include RuoYi tokens or user IDs.

- [ ] **Step 6: Test prompts and parsed contracts**

`ModelInterviewAiTest` must assert:

- question generation sends the current main-question number and difficulty
- evaluation sends the current question and answer
- report generation sends all persisted main/follow-up exchanges
- raw output is parsed into validated contracts
- evidence retrieval failure produces an empty evidence section and still
  calls the model

Run the command from Step 2.

Expected: all AI adapter tests pass.

- [ ] **Step 7: Commit AI adapters**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewEvidenceProvider.java src/main/java/com/example/demoscope/InterviewAiJsonClient.java src/main/java/com/example/demoscope/InterviewQuestionGenerator.java src/main/java/com/example/demoscope/InterviewAnswerEvaluator.java src/main/java/com/example/demoscope/InterviewReportGenerator.java src/main/java/com/example/demoscope/ModelInterviewQuestionGenerator.java src/main/java/com/example/demoscope/ModelInterviewAnswerEvaluator.java src/main/java/com/example/demoscope/ModelInterviewReportGenerator.java src/test/java/com/example/demoscope/InterviewAiJsonClientTest.java src/test/java/com/example/demoscope/ModelInterviewAiTest.java
git commit -m "feat: add interview ai contracts"
```

## Task 5: Implement Create and Resume State Transitions

**Files:**
- Create: `src/main/java/com/example/demoscope/InterviewServiceException.java`
- Create: `src/main/java/com/example/demoscope/InterviewService.java`
- Create: `src/test/java/com/example/demoscope/InterviewServiceCreationTest.java`

- [ ] **Step 1: Write failing create/resume tests**

Use a mutable fake `InterviewRepository`, deterministic UUID supplier, and
fixed `Clock`.

Required tests:

```java
@Test
void createsPendingSessionGeneratesFirstQuestionAndReturnsInProgress() {
    when(generator.generate(any(), eq(1))).thenReturn(
            new GeneratedQuestion("Explain HashMap", List.of("JAVA"), List.of()));

    InterviewSnapshot result = service.createOrResume(
            "user-42", Direction.JAVA_BACKEND, Difficulty.MIDDLE);

    assertEquals(Status.IN_PROGRESS, result.session().status());
    assertEquals(1, result.session().mainQuestionCount());
    assertEquals("Explain HashMap", result.currentQuestion().orElseThrow().text());
}

@Test
void returnsExistingInProgressInterviewWithoutGeneratingAnotherQuestion() {
    repository.seed(inProgressSnapshot());

    InterviewSnapshot result = service.createOrResume(
            "user-42", Direction.JAVA_BACKEND, Difficulty.SENIOR);

    assertEquals(Difficulty.MIDDLE, result.session().difficulty());
    verifyNoInteractions(generator);
}

@Test
void generationFailureLeavesPendingStateAndThrowsSafeUnavailableError() {
    when(generator.generate(any(), eq(1)))
            .thenThrow(new InterviewAiJsonClient.InvalidOutputException("bad"));

    InterviewServiceException error = assertThrows(
            InterviewServiceException.class,
            () -> service.createOrResume(
                    "user-42", Direction.JAVA_BACKEND, Difficulty.MIDDLE));

    assertEquals(InterviewServiceException.Kind.AI_UNAVAILABLE, error.kind());
    assertEquals(Status.QUESTION_GENERATION_PENDING, error.snapshot().session().status());
}
```

Also test an optimistic conflict: when `addMainQuestion` returns `false`, the
service reloads and returns the winner's latest snapshot.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewServiceCreationTest' test
```

Expected: compilation fails because `InterviewService` does not exist.

- [ ] **Step 3: Implement typed service failures**

Create:

```java
public final class InterviewServiceException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
        AI_UNAVAILABLE
    }

    private final Kind kind;
    private final InterviewSnapshot snapshot;

    public InterviewServiceException(
            Kind kind,
            String message,
            InterviewSnapshot snapshot) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.snapshot = snapshot;
    }

    public InterviewServiceException(
            Kind kind,
            String message,
            InterviewSnapshot snapshot,
            Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.snapshot = snapshot;
    }

    public Kind kind() {
        return kind;
    }

    public InterviewSnapshot snapshot() {
        return snapshot;
    }
}
```

Messages must be stable and candidate-safe:

- `unsupported interview configuration`
- `interview not found`
- `interview state conflict`
- `interview AI is temporarily unavailable`

- [ ] **Step 4: Implement create or resume**

`InterviewService` dependencies:

```java
InterviewRepository repository
InterviewQuestionGenerator questionGenerator
InterviewAnswerEvaluator answerEvaluator
InterviewReportGenerator reportGenerator
Clock clock
Supplier<UUID> idSupplier
int maxMainQuestions
int maxFollowUps
```

Constructor validation requires limits exactly `5` and `2` for this version.

Implement:

```java
public InterviewSnapshot createOrResume(
        String userId,
        InterviewSession.Direction direction,
        InterviewSession.Difficulty difficulty)
```

Algorithm:

1. load active by `userId`
2. if absent, call `createPending` with a generated UUID
3. if status is not `QUESTION_GENERATION_PENDING`, return it unchanged
4. call the question generator outside repository mutation
5. create a main question at `mainQuestionCount + 1`
6. call `addMainQuestion` with the snapshot version
7. if the mutation loses an optimistic race, reload and return the latest
8. on AI failure, reload the persisted pending snapshot and throw
   `AI_UNAVAILABLE` carrying that snapshot

If concurrent creation violates the one-active-user index, catch
`DuplicateKeyException` and reload the active interview.

- [ ] **Step 5: Run creation tests and verify GREEN**

Run the command from Step 2.

Expected: all creation tests pass.

- [ ] **Step 6: Commit creation state machine**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewServiceException.java src/main/java/com/example/demoscope/InterviewService.java src/test/java/com/example/demoscope/InterviewServiceCreationTest.java
git commit -m "feat: create and resume interviews"
```

## Task 6: Implement Answer Evaluation and Follow-Up Limits

**Files:**
- Modify: `src/main/java/com/example/demoscope/InterviewService.java`
- Create: `src/test/java/com/example/demoscope/InterviewServiceAnswerTest.java`

- [ ] **Step 1: Write failing answer-progression tests**

Cover these exact branches:

1. AI `FOLLOW_UP` after a main question creates follow-up 1.
2. AI `FOLLOW_UP` after follow-up 1 creates follow-up 2.
3. AI requests another follow-up after follow-up 2; Java overrides it and
   enters `QUESTION_GENERATION_PENDING`.
4. AI `NEXT_MAIN_QUESTION` enters pending generation immediately.
5. Main question 5 can still create follow-up 1 and 2.
6. Finishing the fifth thread enters `SCORING_PENDING`.
7. Evaluation failure leaves the question unanswered.
8. Blank answer is rejected.
9. Stale question ID is rejected.
10. Already answered question ID returns the latest snapshot without another
    evaluator call.

Representative test:

```java
@Test
void thirdFollowUpRequestIsOverriddenAndDoesNotCreateFollowUpThree() {
    repository.seed(snapshotWaitingForFollowUpTwo());
    when(evaluator.evaluate(any(), any(), eq("candidate answer")))
            .thenReturn(new AnswerEvaluation(
                    "still partial", List.of("JVM"),
                    Decision.FOLLOW_UP, "illegal third follow-up", "needs more"));

    InterviewSnapshot result = service.answer(
            "user-42", interviewId, followUpTwoId, "candidate answer");

    assertEquals(Status.QUESTION_GENERATION_PENDING, result.session().status());
    assertEquals(2, result.questions().stream()
            .filter(q -> q.type() == InterviewQuestion.Type.FOLLOW_UP)
            .count());
}
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewServiceAnswerTest' test
```

Expected: compilation fails because `InterviewService.answer` is absent.

- [ ] **Step 3: Implement answer validation and idempotency**

Add:

```java
public InterviewSnapshot answer(
        String userId,
        UUID interviewId,
        UUID questionId,
        String answerText)
```

Before calling AI:

1. reject blank answers with `BAD_REQUEST`
2. load by both interview ID and user ID; absent becomes `NOT_FOUND`
3. if `snapshot.answerFor(questionId)` is present, return the snapshot
4. require session status `IN_PROGRESS`
5. require `currentQuestionId` equals `questionId`
6. require the question status `WAITING_FOR_ANSWER`

- [ ] **Step 4: Implement AI evaluation and validated transitions**

Call `answerEvaluator.evaluate` outside a repository transaction.

Build and persist an `InterviewAnswer` from the AI's private evaluation.

When decision is `FOLLOW_UP` and current follow-up number is below
`maxFollowUps`, create the next follow-up and call
`recordAnswerAndFollowUp`.

Otherwise:

- if `mainQuestionNumber < maxMainQuestions`, call
  `recordAnswerAndAwaitMainQuestion`
- if `mainQuestionNumber == maxMainQuestions`, call
  `recordAnswerAndAwaitScoring`

On optimistic failure, reload and return the latest snapshot.

On AI failure, throw `AI_UNAVAILABLE` with the unchanged latest snapshot.

After a successful `recordAnswerAndAwaitMainQuestion`, call
`createOrResume` so the answer response normally contains the next generated
main question. If generation fails, propagate the safe 503 with the persisted
pending snapshot.

Do not automatically score yet; Task 7 adds that transition.

- [ ] **Step 5: Run answer tests and verify GREEN**

Run the command from Step 2.

Expected: all answer-progression tests pass.

- [ ] **Step 6: Commit answer progression**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewService.java src/test/java/com/example/demoscope/InterviewServiceAnswerTest.java
git commit -m "feat: evaluate interview answers and follow ups"
```

## Task 7: Implement Voluntary Finish and Final Scoring

**Files:**
- Modify: `src/main/java/com/example/demoscope/InterviewService.java`
- Create: `src/test/java/com/example/demoscope/InterviewServiceFinishTest.java`

- [ ] **Step 1: Write failing finish and scoring tests**

Required cases:

```java
@Test
void zeroAnswerInterviewIsCancelledWithoutScoring() {
    repository.seed(snapshotWithZeroAnswers());

    InterviewSnapshot result = service.finish("user-42", interviewId);

    assertEquals(Status.CANCELLED, result.session().status());
    verifyNoInteractions(reportGenerator);
}

@Test
void voluntaryFinishAfterOneAnswerGeneratesReport() {
    repository.seed(snapshotWithOneAnswer());
    when(reportGenerator.generate(any())).thenReturn(reportDraft(78));

    InterviewSnapshot result = service.finish("user-42", interviewId);

    assertEquals(Status.COMPLETED, result.session().status());
    assertEquals(78, result.report().overallScore());
}

@Test
void scoringFailureKeepsRetryablePendingState() {
    repository.seed(scoringPendingSnapshot());
    when(reportGenerator.generate(any())).thenThrow(new RuntimeException("model down"));

    InterviewSnapshot result = service.finish("user-42", interviewId);

    assertEquals(Status.SCORING_PENDING, result.session().status());
    assertEquals(null, result.report());
}
```

Also test:

- automatic fifth-thread completion immediately attempts scoring
- completed finish is idempotent
- cancelled finish is idempotent
- scoring-pending interviews reject answers
- optimistic scoring conflicts reload the winner

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewServiceFinishTest' test
```

Expected: tests fail because finish and report completion are absent.

- [ ] **Step 3: Implement report conversion**

Add a private conversion:

```java
private InterviewReport toReport(
        UUID interviewId,
        InterviewAiContracts.ReportDraft draft,
        Instant now) {
    return new InterviewReport(
            interviewId,
            draft.overallScore(),
            draft.javaFundamentalsScore(),
            draft.concurrencyScore(),
            draft.jvmScore(),
            draft.springScore(),
            draft.databaseScore(),
            draft.engineeringScore(),
            draft.strengths(),
            draft.weaknesses(),
            draft.improvementSuggestions(),
            now);
}
```

- [ ] **Step 4: Implement finish**

Add:

```java
public InterviewSnapshot finish(String userId, UUID interviewId)
```

Rules:

- `COMPLETED` or `CANCELLED`: return unchanged
- zero answers: optimistic `cancel`, then reload
- `IN_PROGRESS` or `QUESTION_GENERATION_PENDING` with answers: optimistic
  `markScoringPending`, then reload
- `SCORING_PENDING`: call report generator outside the transaction
- report failure: return the persisted `SCORING_PENDING` snapshot
- report success: optimistic `completeReport`, then reload

Modify automatic fifth-thread completion in `answer` to call `finish` after
`recordAnswerAndAwaitScoring` succeeds.

- [ ] **Step 5: Run finish and full service tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewServiceCreationTest,InterviewServiceAnswerTest,InterviewServiceFinishTest' test
```

Expected: all service tests pass.

- [ ] **Step 6: Commit scoring**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewService.java src/test/java/com/example/demoscope/InterviewServiceFinishTest.java
git commit -m "feat: finish and score java interviews"
```

## Task 8: Expose Authenticated Interview APIs

**Files:**
- Create: `src/main/java/com/example/demoscope/InterviewController.java`
- Create: `src/test/java/com/example/demoscope/InterviewControllerTest.java`

- [ ] **Step 1: Write failing MockMvc contract tests**

Use standalone MockMvc with mocked `InterviewService` and a controlled
`AuthenticatedUserContext`.

Cover:

- `POST /api/interviews`
- `GET /api/interviews/current`
- `GET /api/interviews/{id}`
- `POST /api/interviews/{id}/answers`
- `POST /api/interviews/{id}/finish`
- missing token -> 401
- unsupported enum -> 400
- ownership failure -> 404
- stale question -> 409
- AI generation/evaluation failure -> 503 with pending safe view
- scoring pending -> 202
- internal evaluation fields absent from serialized JSON

Representative assertion:

```java
mockMvc.perform(post("/api/interviews/{id}/answers", interviewId)
                .header("Authorization", "Bearer token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"questionId":"%s","answer":"volatile guarantees visibility"}
                        """.formatted(questionId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextAction").value("FOLLOW_UP"))
        .andExpect(jsonPath("$.question.questionId").exists())
        .andExpect(jsonPath("$.internalEvaluation").doesNotExist())
        .andExpect(jsonPath("$.decisionReason").doesNotExist())
        .andExpect(jsonPath("$.evidenceIds").doesNotExist());
```

- [ ] **Step 2: Run controller tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewControllerTest' test
```

Expected: compilation fails because `InterviewController` does not exist.

- [ ] **Step 3: Implement request and response DTOs**

Define nested request records:

```java
public record CreateInterviewRequest(
        InterviewSession.Direction direction,
        InterviewSession.Difficulty difficulty) {}

public record SubmitAnswerRequest(UUID questionId, String answer) {}
```

Define candidate-safe response records:

```java
public record InterviewResponse(
        UUID interviewId,
        InterviewSession.Status status,
        InterviewSession.Direction direction,
        InterviewSession.Difficulty difficulty,
        int mainQuestionNumber,
        int followUpNumber,
        NextAction nextAction,
        QuestionResponse question,
        ReportResponse report) {}

public enum NextAction {
    FOLLOW_UP,
    MAIN_QUESTION,
    REPORT_PENDING,
    REPORT,
    CANCELLED
}
```

`QuestionResponse` exposes only ID and text. `ReportResponse` exposes the seven
scores and three feedback lists.

Map actions:

- current follow-up -> `FOLLOW_UP`
- current main question -> `MAIN_QUESTION`
- scoring pending -> `REPORT_PENDING`
- completed -> `REPORT`
- cancelled -> `CANCELLED`

- [ ] **Step 4: Implement authenticated endpoints**

Use:

```java
@RestController
@RequestMapping("/api/interviews")
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InterviewController {
```

Every method resolves `userId` from `AuthenticatedUserContext` before invoking
the service.

Return:

- 200 for in-progress, completed, cancelled, and idempotent reads
- 202 for `SCORING_PENDING`
- 503 for `AI_UNAVAILABLE`, using the exception's pending snapshot as the body
- 400/404/409 for the matching `InterviewServiceException.Kind`

Catch `UnauthenticatedUserException` and return 401 using the same pattern as
`AgentChatController`.

- [ ] **Step 5: Run controller tests and verify GREEN**

Run the command from Step 2.

Expected: all controller tests pass.

- [ ] **Step 6: Commit HTTP APIs**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewController.java src/test/java/com/example/demoscope/InterviewControllerTest.java
git commit -m "feat: expose authenticated interview APIs"
```

## Task 9: Wire Production Beans and Protect Existing Contexts

**Files:**
- Create: `src/main/java/com/example/demoscope/InterviewConfig.java`
- Create: `src/test/java/com/example/demoscope/InterviewConfigTest.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Write a failing bean-wiring test**

Create a small `ApplicationContextRunner` test that registers
`InterviewConfig.class` and `InterviewController.class`, with mocked:

- `JdbcOperations`
- `TransactionOperations`
- `ChatTextModel`
- `ObjectMapper`
- `EmbeddingClient`
- `KnowledgeRetriever`
- `AuthenticatedUserContext`

Assert that with:

```text
agentscope.interview.enabled=true
agentscope.interview.max-main-questions=5
agentscope.interview.max-follow-ups=2
```

the context contains:

- `InterviewRepository`
- `InterviewEvidenceProvider`
- the three AI interfaces
- `InterviewService`
- `InterviewController`

Also assert `agentscope.interview.enabled=false` creates none of them.

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewConfigTest' test
```

Expected: compilation fails because `InterviewConfig` does not exist.

- [ ] **Step 3: Implement interview bean configuration**

Create:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InterviewConfig {
```

Register:

- `JdbcInterviewRepository`
- `InterviewEvidenceProvider`
- `InterviewAiJsonClient`
- `ModelInterviewQuestionGenerator`
- `ModelInterviewAnswerEvaluator`
- `ModelInterviewReportGenerator`
- `InterviewService`

Use `Clock` from `AgentMemoryConfig`, `UUID::randomUUID`, and the configured
limits.

Do not create a second `ChatTextModel`, `ObjectMapper`, embedding client,
knowledge retriever, or authentication context.

- [ ] **Step 4: Run configuration and complete focused tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=InterviewDomainTest,InterviewDatabaseConfigTest,JdbcInterviewRepositoryTest,InterviewAiJsonClientTest,ModelInterviewAiTest,InterviewServiceCreationTest,InterviewServiceAnswerTest,InterviewServiceFinishTest,InterviewControllerTest,InterviewConfigTest' test
```

Expected: all interview-focused tests pass.

- [ ] **Step 5: Commit production wiring**

```powershell
git add -- src/main/java/com/example/demoscope/InterviewConfig.java src/test/java/com/example/demoscope/InterviewConfigTest.java src/main/resources/application.properties
git commit -m "feat: wire java interview services"
```

## Task 10: Prove the Authenticated Interview Flow

**Files:**
- Create: `src/test/java/com/example/demoscope/AuthenticatedInterviewFlowTest.java`

- [ ] **Step 1: Write an HTTP flow test with deterministic fakes**

Use:

- actual `InterviewController`
- actual `InterviewService`
- a mutable in-memory `InterviewRepository` test double
- fake authenticated context accepting `Bearer token-123`
- queued question generator
- queued evaluator decisions
- fixed report generator

The test sequence must:

1. create `JAVA_BACKEND / MIDDLE`
2. receive main question 1
3. answer it and receive follow-up 1
4. answer follow-up 1 and receive follow-up 2
5. make AI request a third follow-up and prove main question 2 is returned
6. complete main questions 2 through 5
7. allow two follow-ups on main question 5
8. receive a completed report
9. fetch the completed interview
10. assert no private evaluation fields appear in any response

Add a second flow:

1. create an interview
2. answer one question
3. call finish
4. receive a report

Add a third flow:

1. create an interview
2. call finish before answering
3. receive `CANCELLED` with no report

- [ ] **Step 2: Run the flow test and verify its initial failure**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AuthenticatedInterviewFlowTest' test
```

Expected before all prior tasks are complete: compilation or contract failure.
Expected after Tasks 1-9: the flow passes.

- [ ] **Step 3: Add retry and isolation assertions**

Extend the flow test to prove:

- repeating the same answered `questionId` does not add an answer
- a second authenticated user gets HTTP 404 for the first user's interview
- AI evaluation failure returns 503 and retrying the same question succeeds
- score generation failure returns 202 and retrying finish succeeds
- a pending next-main-question generation is retried through
  `POST /api/interviews`

- [ ] **Step 4: Run all authentication and interview tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthFlowTest,RuoyiAuthProxyControllerTest,RuoyiSaTokenUserContextTest,InterviewDomainTest,InterviewDatabaseConfigTest,JdbcInterviewRepositoryTest,InterviewAiJsonClientTest,ModelInterviewAiTest,InterviewServiceCreationTest,InterviewServiceAnswerTest,InterviewServiceFinishTest,InterviewControllerTest,InterviewConfigTest,AuthenticatedInterviewFlowTest' test
```

Expected: all focused tests pass.

- [ ] **Step 5: Commit the end-to-end proof**

```powershell
git add -- src/test/java/com/example/demoscope/AuthenticatedInterviewFlowTest.java
git commit -m "test: verify authenticated java interview flow"
```

## Task 11: Full Verification and Real-Environment Smoke Test

**Files:**
- No source changes expected.

- [ ] **Step 1: Run the full test suite**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B test
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Build the application**

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B -DskipTests package
git diff --check
```

Expected: Maven build succeeds and `git diff --check` exits 0.

- [ ] **Step 3: Run static security checks**

```powershell
rg -n "internalEvaluation|decisionReason|evidenceIds|rawJson|token" src/main/java/com/example/demoscope/InterviewController.java
rg -n "request.getHeader|Cookie|X-Forwarded|baseUrl" src/main/java/com/example/demoscope/Interview*.java
rg -n "@RequestMapping|@PostMapping|@GetMapping" src/main/java/com/example/demoscope/InterviewController.java
```

Expected:

- private AI fields appear only in domain/storage code, not response records
- no interview endpoint forwards arbitrary headers or destination URLs
- only the five explicit interview routes are exposed

- [ ] **Step 4: Verify real service prerequisites**

Required environment:

```text
AGENTSCOPE_RUOYI_BASE_URL
AGENTSCOPE_SMOKE_BASE_URL
RUOYI_LOGIN_BODY_FILE
PGVECTOR_URL
PGVECTOR_USERNAME
PGVECTOR_PASSWORD
OPENAI_API_KEY
SILICONFLOW_API_KEY
```

RuoYi and AgentScope must share Redis and Sa-Token configuration. PostgreSQL
must be reachable and support the existing pgvector schema when retrieval is
enabled.

- [ ] **Step 5: Execute the real API flow**

```powershell
$base = $env:AGENTSCOPE_SMOKE_BASE_URL
$loginBody = Get-Content -Raw -Encoding UTF8 $env:RUOYI_LOGIN_BODY_FILE
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
  -ContentType 'application/json' -Body $loginBody
$token = $login.data.access_token
$headers = @{ Authorization = "Bearer $token" }

$interview = Invoke-RestMethod -Method Post -Uri "$base/api/interviews" `
  -Headers $headers -ContentType 'application/json' `
  -Body '{"direction":"JAVA_BACKEND","difficulty":"MIDDLE"}'

for ($i = 0; $i -lt 20 -and $interview.status -eq 'IN_PROGRESS'; $i++) {
    $body = @{
        questionId = $interview.question.questionId
        answer = '这是用于验证登录到 Java 技术面试闭环的测试回答。'
    } | ConvertTo-Json
    $interview = Invoke-RestMethod -Method Post `
      -Uri "$base/api/interviews/$($interview.interviewId)/answers" `
      -Headers $headers -ContentType 'application/json' -Body $body
}

if ($interview.status -eq 'SCORING_PENDING') {
    $interview = Invoke-RestMethod -Method Post `
      -Uri "$base/api/interviews/$($interview.interviewId)/finish" `
      -Headers $headers
}

if ($interview.status -ne 'COMPLETED') {
    throw "Interview did not complete: $($interview.status)"
}
if ($null -eq $interview.report.overallScore) {
    throw 'Interview report is missing'
}
```

Expected: login succeeds, interview questions progress within the configured
limits, and the final response contains a report.

- [ ] **Step 6: Verify persistence across restart**

1. Create an interview and answer at least one question.
2. Record its `interviewId` and current `questionId`.
3. Restart AgentScope without changing PostgreSQL.
4. Call:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$base/api/interviews/$interviewId" `
  -Headers $headers
```

Expected: the same current question and progress are returned.

- [ ] **Step 7: Record completion status**

Report separately:

- automated test result
- package result
- login result
- interview creation result
- follow-up-limit result
- automatic or voluntary finish result
- score report result
- restart persistence result

If external services are unavailable, explicitly state that automated closure
passed but production interview closure remains unverified.
