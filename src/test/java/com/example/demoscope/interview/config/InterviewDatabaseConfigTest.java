package com.example.demoscope.interview.config;

import com.example.demoscope.interview.config.InterviewDatabaseConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class InterviewDatabaseConfigTest {

    @Test
    void missingInterviewFlagDoesNotRequirePgvectorUrl() {
        new ApplicationContextRunner()
                .withUserConfiguration(InterviewDatabaseConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean("agentPostgresDataSource");
                });
    }

    @Test
    void buildsPostgresDataSourceFromExistingPgvectorProperties() {
        DataSource dataSource = InterviewDatabaseConfig.dataSource(
                "jdbc:postgresql://localhost:5432/agent",
                "agent",
                "secret");

        assertTrue(dataSource instanceof DriverManagerDataSource);
    }

    @Test
    void rejectsBlankDatabaseUrlWhenInterviewIsEnabled() {
        assertThrows(
                IllegalStateException.class,
                () -> InterviewDatabaseConfig.dataSource("", "", ""));
    }

    @Test
    void reusesDataSourceBehindExistingJdbcOperations() {
        DataSource existing = InterviewDatabaseConfig.dataSource(
                "jdbc:postgresql://localhost:5432/existing",
                "agent",
                "secret");
        DataSource fallback = InterviewDatabaseConfig.dataSource(
                "jdbc:postgresql://localhost:5432/fallback",
                "agent",
                "secret");

        DataSource resolved = InterviewDatabaseConfig.dataSourceFor(
                new JdbcTemplate(existing),
                fallback);

        assertSame(existing, resolved);
    }

    @Test
    void schemaContainsInterviewConstraints() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/interview-schema.sql"));

        assertTrue(sql.contains("interview_session_one_active_user_idx"));
        assertTrue(sql.contains("follow_up_number between 0 and 2"));
        assertTrue(sql.contains("unique (question_id)"));
    }
}
