package com.example.demoscope.config.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.demoscope.common.jdbc.JdbcTokenUsageRecorder;
import com.example.demoscope.common.llm.NoopTokenUsageRecorder;
import com.example.demoscope.common.llm.OpenAiRequestLogger;
import com.example.demoscope.common.llm.TokenUsageRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;

class AgentMemoryConfigTokenUsageTest {

    @Test
    void createsNoopTokenUsageRecorderWhenJdbcIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(LoggerConfig.class, AgentMemoryConfig.class)
                .run(context -> assertThat(context.getBean(TokenUsageRecorder.class))
                        .isInstanceOf(NoopTokenUsageRecorder.class));
    }

    @Test
    void createsJdbcTokenUsageRecorderAndInitializesSchemaWhenJdbcExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(LoggerConfig.class, JdbcConfig.class, AgentMemoryConfig.class)
                .run(context -> {
                    assertThat(context.getBean(TokenUsageRecorder.class))
                            .isInstanceOf(JdbcTokenUsageRecorder.class);
                    JdbcOperations jdbc = context.getBean(JdbcOperations.class);
                    verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("llm_call_records"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class LoggerConfig {

        @Bean
        OpenAiRequestLogger openAiRequestLogger() {
            return new OpenAiRequestLogger();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcConfig {

        @Bean
        JdbcOperations jdbcOperations() {
            return mock(JdbcOperations.class);
        }
    }
}
