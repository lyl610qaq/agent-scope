package com.example.demoscope.interview.infrastructure;

import com.example.demoscope.interview.domain.InterviewAnswer;
import com.example.demoscope.interview.domain.InterviewQuestion;
import com.example.demoscope.interview.domain.InterviewSession;
import com.example.demoscope.interview.domain.InterviewSnapshot;
import com.example.demoscope.interview.infrastructure.JdbcInterviewRepository;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class JdbcInterviewRepositoryTest {

    private JdbcOperations jdbc;
    private RecordingTransactions transactions;
    private JdbcInterviewRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcOperations.class);
        transactions = new RecordingTransactions();
        repository = new JdbcInterviewRepository(
                jdbc,
                transactions,
                new ObjectMapper());
    }

    @Test
    void createPendingInsertsVersionZeroSession() {
        UUID interviewId = UUID.randomUUID();

        InterviewSnapshot snapshot = repository.createPending(
                interviewId,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                Instant.EPOCH);

        assertTrue(snapshot.session().unfinished());
        assertTrue(transactions.executions > 0);
        assertSqlContains("insert into interview_session");
    }

    @Test
    void optimisticMutationReturnsFalseWhenVersionChanged() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        boolean changed = repository.markScoringPending(
                UUID.randomUUID(),
                "user-42",
                4,
                Instant.EPOCH);

        assertFalse(changed);
        assertSqlContains("version = ?");
    }

    @Test
    void answerAndFollowUpAreWrittenInsideOneTransaction() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        UUID interviewId = UUID.randomUUID();
        UUID mainQuestionId = UUID.randomUUID();
        InterviewAnswer answer = new InterviewAnswer(
                UUID.randomUUID(),
                interviewId,
                mainQuestionId,
                "candidate answer",
                "partial",
                List.of("JAVA"),
                InterviewAnswer.Decision.FOLLOW_UP,
                "needs detail",
                Instant.EPOCH);
        InterviewQuestion followUp = InterviewQuestion.followUp(
                UUID.randomUUID(),
                interviewId,
                1,
                1,
                mainQuestionId,
                "Why?",
                List.of("JAVA"),
                List.of(),
                Instant.EPOCH);

        boolean changed = repository.recordAnswerAndFollowUp(
                interviewId,
                "user-42",
                2,
                answer,
                followUp,
                Instant.EPOCH);

        assertTrue(changed);
        assertTrue(transactions.executions == 1);
        assertSqlContains("insert into interview_answer");
        assertSqlContains("insert into interview_question");
        assertSqlContains("status = 'IN_PROGRESS'");
    }

    @Test
    void interviewLookupScopesSessionByIdAndUser() {
        repository.findByIdAndUserId(UUID.randomUUID(), "user-42");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
                sql.capture(),
                any(RowMapper.class),
                any(Object[].class));
        assertTrue(sql.getValue().contains("where id = ? and user_id = ?"));
    }

    private void assertSqlContains(String expected) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(Object[].class));
        assertTrue(
                sql.getAllValues().stream().anyMatch(value -> value.contains(expected)),
                () -> "Expected SQL containing: " + expected);
    }

    private static final class RecordingTransactions
            implements TransactionOperations {

        private int executions;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            executions++;
            return action.doInTransaction(mock(TransactionStatus.class));
        }
    }
}
