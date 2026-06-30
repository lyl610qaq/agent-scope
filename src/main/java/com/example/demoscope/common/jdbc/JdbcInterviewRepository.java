package com.example.demoscope.common.jdbc;

import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewRepository;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionOperations;

public class JdbcInterviewRepository implements InterviewRepository {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final JdbcOperations jdbc;
    private final TransactionOperations transactions;
    private final ObjectMapper objectMapper;

    public JdbcInterviewRepository(
            JdbcOperations jdbc,
            TransactionOperations transactions,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<InterviewSnapshot> findActiveByUserId(String userId) {
        List<InterviewSession> sessions = jdbc.query("""
                select *
                from interview_session
                where user_id = ?
                  and status in (
                      'QUESTION_GENERATION_PENDING',
                      'IN_PROGRESS',
                      'SCORING_PENDING'
                  )
                order by created_at desc
                limit 1
                """, sessionMapper(), userId);
        return sessions.isEmpty()
                ? Optional.empty()
                : Optional.of(loadSnapshot(sessions.get(0)));
    }

    @Override
    public Optional<InterviewSnapshot> findByIdAndUserId(
            UUID interviewId,
            String userId) {
        List<InterviewSession> sessions = jdbc.query("""
                select *
                from interview_session
                where id = ? and user_id = ?
                """, sessionMapper(), interviewId, userId);
        return sessions.isEmpty()
                ? Optional.empty()
                : Optional.of(loadSnapshot(sessions.get(0)));
    }

    @Override
    public InterviewSnapshot createPending(
            UUID interviewId,
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty,
            Instant now) {
        transactions.execute(status -> {
            jdbc.update("""
                    insert into interview_session (
                        id,
                        user_id,
                        direction,
                        difficulty,
                        status,
                        main_question_count,
                        current_question_id,
                        answered_question_count,
                        version,
                        created_at,
                        updated_at,
                        completed_at
                    ) values (?, ?, ?, ?, 'QUESTION_GENERATION_PENDING',
                              0, null, 0, 0, ?, ?, null)
                    """,
                    interviewId,
                    userId,
                    direction.name(),
                    difficulty.name(),
                    now,
                    now);
            return null;
        });
        InterviewSession session = new InterviewSession(
                interviewId,
                userId,
                direction,
                difficulty,
                InterviewSession.Status.QUESTION_GENERATION_PENDING,
                0,
                null,
                0,
                0,
                now,
                now,
                null);
        return new InterviewSnapshot(session, List.of(), List.of(), null);
    }

    @Override
    public boolean addMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewQuestion question,
            Instant now) {
        return inTransaction(() -> {
            int changed = jdbc.update("""
                    update interview_session
                    set status = 'IN_PROGRESS',
                        main_question_count = main_question_count + 1,
                        current_question_id = ?,
                        version = version + 1,
                        updated_at = ?
                    where id = ? and user_id = ? and version = ?
                      and status = 'QUESTION_GENERATION_PENDING'
                    """,
                    question.id(),
                    now,
                    interviewId,
                    userId,
                    expectedVersion);
            if (changed == 0) {
                return false;
            }
            insertQuestion(question);
            return true;
        });
    }

    @Override
    public boolean recordAnswerAndFollowUp(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            InterviewQuestion followUp,
            Instant now) {
        return inTransaction(() -> {
            int changed = jdbc.update("""
                    update interview_session
                    set status = 'IN_PROGRESS',
                        current_question_id = ?,
                        answered_question_count = answered_question_count + 1,
                        version = version + 1,
                        updated_at = ?
                    where id = ? and user_id = ? and version = ?
                      and status = 'IN_PROGRESS'
                      and current_question_id = ?
                    """,
                    followUp.id(),
                    now,
                    interviewId,
                    userId,
                    expectedVersion,
                    answer.questionId());
            if (changed == 0) {
                return false;
            }
            markAnswered(answer.questionId(), now);
            insertAnswer(answer);
            insertQuestion(followUp);
            return true;
        });
    }

    @Override
    public boolean recordAnswerAndAwaitMainQuestion(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now) {
        return recordAnswerAndChangeState(
                interviewId,
                userId,
                expectedVersion,
                answer,
                InterviewSession.Status.QUESTION_GENERATION_PENDING,
                now);
    }

    @Override
    public boolean recordAnswerAndAwaitScoring(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            Instant now) {
        return recordAnswerAndChangeState(
                interviewId,
                userId,
                expectedVersion,
                answer,
                InterviewSession.Status.SCORING_PENDING,
                now);
    }

    @Override
    public boolean markScoringPending(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now) {
        return inTransaction(() -> jdbc.update("""
                update interview_session
                set status = 'SCORING_PENDING',
                    current_question_id = null,
                    version = version + 1,
                    updated_at = ?
                where id = ? and user_id = ? and version = ?
                  and status in ('IN_PROGRESS', 'QUESTION_GENERATION_PENDING')
                """,
                now,
                interviewId,
                userId,
                expectedVersion) > 0);
    }

    @Override
    public boolean cancel(
            UUID interviewId,
            String userId,
            long expectedVersion,
            Instant now) {
        return inTransaction(() -> jdbc.update("""
                update interview_session
                set status = 'CANCELLED',
                    current_question_id = null,
                    version = version + 1,
                    updated_at = ?,
                    completed_at = ?
                where id = ? and user_id = ? and version = ?
                  and status in ('IN_PROGRESS', 'QUESTION_GENERATION_PENDING')
                """,
                now,
                now,
                interviewId,
                userId,
                expectedVersion) > 0);
    }

    @Override
    public boolean completeReport(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewReport report,
            Instant now) {
        return inTransaction(() -> {
            int changed = jdbc.update("""
                    update interview_session
                    set status = 'COMPLETED',
                        current_question_id = null,
                        version = version + 1,
                        updated_at = ?,
                        completed_at = ?
                    where id = ? and user_id = ? and version = ?
                      and status = 'SCORING_PENDING'
                    """,
                    now,
                    now,
                    interviewId,
                    userId,
                    expectedVersion);
            if (changed == 0) {
                return false;
            }
            jdbc.update("""
                    insert into interview_report (
                        interview_id,
                        overall_score,
                        java_fundamentals_score,
                        concurrency_score,
                        jvm_score,
                        spring_score,
                        database_score,
                        engineering_score,
                        strengths_json,
                        weaknesses_json,
                        improvement_suggestions_json,
                        created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    report.interviewId(),
                    report.overallScore(),
                    report.javaFundamentalsScore(),
                    report.concurrencyScore(),
                    report.jvmScore(),
                    report.springScore(),
                    report.databaseScore(),
                    report.engineeringScore(),
                    writeList(report.strengths()),
                    writeList(report.weaknesses()),
                    writeList(report.improvementSuggestions()),
                    report.createdAt());
            return true;
        });
    }

    private boolean recordAnswerAndChangeState(
            UUID interviewId,
            String userId,
            long expectedVersion,
            InterviewAnswer answer,
            InterviewSession.Status status,
            Instant now) {
        return inTransaction(() -> {
            int changed = jdbc.update("""
                    update interview_session
                    set status = ?,
                        current_question_id = null,
                        answered_question_count = answered_question_count + 1,
                        version = version + 1,
                        updated_at = ?
                    where id = ? and user_id = ? and version = ?
                      and status = 'IN_PROGRESS'
                      and current_question_id = ?
                    """,
                    status.name(),
                    now,
                    interviewId,
                    userId,
                    expectedVersion,
                    answer.questionId());
            if (changed == 0) {
                return false;
            }
            markAnswered(answer.questionId(), now);
            insertAnswer(answer);
            return true;
        });
    }

    private InterviewSnapshot loadSnapshot(InterviewSession session) {
        List<InterviewQuestion> questions = jdbc.query("""
                select *
                from interview_question
                where interview_id = ?
                order by main_question_number, follow_up_number
                """, questionMapper(), session.id());
        List<InterviewAnswer> answers = jdbc.query("""
                select *
                from interview_answer
                where interview_id = ?
                order by created_at
                """, answerMapper(), session.id());
        List<InterviewReport> reports = jdbc.query("""
                select *
                from interview_report
                where interview_id = ?
                """, reportMapper(), session.id());
        return new InterviewSnapshot(
                session,
                questions,
                answers,
                reports.isEmpty() ? null : reports.get(0));
    }

    private void insertQuestion(InterviewQuestion question) {
        jdbc.update("""
                insert into interview_question (
                    id,
                    interview_id,
                    type,
                    main_question_number,
                    follow_up_number,
                    parent_question_id,
                    text,
                    skill_tags_json,
                    evidence_ids_json,
                    status,
                    created_at,
                    answered_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                question.id(),
                question.interviewId(),
                question.type().name(),
                question.mainQuestionNumber(),
                question.followUpNumber(),
                question.parentQuestionId(),
                question.text(),
                writeList(question.skillTags()),
                writeList(question.evidenceIds()),
                question.status().name(),
                question.createdAt(),
                question.answeredAt());
    }

    private void insertAnswer(InterviewAnswer answer) {
        jdbc.update("""
                insert into interview_answer (
                    id,
                    interview_id,
                    question_id,
                    answer_text,
                    internal_evaluation,
                    ability_tags_json,
                    ai_decision,
                    decision_reason,
                    created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                answer.id(),
                answer.interviewId(),
                answer.questionId(),
                answer.answerText(),
                answer.internalEvaluation(),
                writeList(answer.abilityTags()),
                answer.decision().name(),
                answer.decisionReason(),
                answer.createdAt());
    }

    private void markAnswered(UUID questionId, Instant now) {
        jdbc.update("""
                update interview_question
                set status = 'ANSWERED', answered_at = ?
                where id = ? and status = 'WAITING_FOR_ANSWER'
                """, now, questionId);
    }

    private boolean inTransaction(BooleanOperation operation) {
        return Boolean.TRUE.equals(transactions.execute(status -> operation.run()));
    }

    private RowMapper<InterviewSession> sessionMapper() {
        return (resultSet, rowNumber) -> new InterviewSession(
                uuid(resultSet, "id"),
                resultSet.getString("user_id"),
                InterviewSession.Direction.valueOf(
                        resultSet.getString("direction")),
                InterviewSession.Difficulty.valueOf(
                        resultSet.getString("difficulty")),
                InterviewSession.Status.valueOf(
                        resultSet.getString("status")),
                resultSet.getInt("main_question_count"),
                nullableUuid(resultSet, "current_question_id"),
                resultSet.getInt("answered_question_count"),
                resultSet.getLong("version"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                nullableInstant(resultSet, "completed_at"));
    }

    private RowMapper<InterviewQuestion> questionMapper() {
        return (resultSet, rowNumber) -> new InterviewQuestion(
                uuid(resultSet, "id"),
                uuid(resultSet, "interview_id"),
                InterviewQuestion.Type.valueOf(resultSet.getString("type")),
                resultSet.getInt("main_question_number"),
                resultSet.getInt("follow_up_number"),
                nullableUuid(resultSet, "parent_question_id"),
                resultSet.getString("text"),
                readList(resultSet.getString("skill_tags_json")),
                readList(resultSet.getString("evidence_ids_json")),
                InterviewQuestion.Status.valueOf(resultSet.getString("status")),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "answered_at"));
    }

    private RowMapper<InterviewAnswer> answerMapper() {
        return (resultSet, rowNumber) -> new InterviewAnswer(
                uuid(resultSet, "id"),
                uuid(resultSet, "interview_id"),
                uuid(resultSet, "question_id"),
                resultSet.getString("answer_text"),
                resultSet.getString("internal_evaluation"),
                readList(resultSet.getString("ability_tags_json")),
                InterviewAnswer.Decision.valueOf(
                        resultSet.getString("ai_decision")),
                resultSet.getString("decision_reason"),
                instant(resultSet, "created_at"));
    }

    private RowMapper<InterviewReport> reportMapper() {
        return (resultSet, rowNumber) -> new InterviewReport(
                uuid(resultSet, "interview_id"),
                resultSet.getInt("overall_score"),
                resultSet.getInt("java_fundamentals_score"),
                resultSet.getInt("concurrency_score"),
                resultSet.getInt("jvm_score"),
                resultSet.getInt("spring_score"),
                resultSet.getInt("database_score"),
                resultSet.getInt("engineering_score"),
                readList(resultSet.getString("strengths_json")),
                readList(resultSet.getString("weaknesses_json")),
                readList(resultSet.getString("improvement_suggestions_json")),
                instant(resultSet, "created_at"));
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize interview data",
                    exception);
        }
    }

    private List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not read interview data",
                    exception);
        }
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private UUID nullableUuid(ResultSet resultSet, String column)
            throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    @FunctionalInterface
    private interface BooleanOperation {
        boolean run();
    }
}
