package com.example.demoscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

class InterviewConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            Infrastructure.class,
                            InterviewConfig.class,
                            InterviewController.class);

    @Test
    void enabledInterviewWiresAllProductionComponents() {
        contextRunner
                .withPropertyValues(
                        "agentscope.interview.enabled=true",
                        "agentscope.interview.max-main-questions=5",
                        "agentscope.interview.max-follow-ups=2")
                .run(context -> {
                    assertThat(context).hasSingleBean(InterviewRepository.class);
                    assertThat(context).hasSingleBean(
                            InterviewEvidenceProvider.class);
                    assertThat(context).hasSingleBean(
                            InterviewQuestionGenerator.class);
                    assertThat(context).hasSingleBean(
                            InterviewAnswerEvaluator.class);
                    assertThat(context).hasSingleBean(
                            InterviewReportGenerator.class);
                    assertThat(context).hasSingleBean(InterviewService.class);
                    assertThat(context).hasSingleBean(InterviewController.class);
                    assertThat(context).hasSingleBean(ChatTextModel.class);
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(EmbeddingClient.class);
                    assertThat(context).hasSingleBean(KnowledgeRetriever.class);
                    assertThat(context)
                            .hasSingleBean(AuthenticatedUserContext.class);
                });
    }

    @Test
    void disabledInterviewCreatesNoInterviewComponents() {
        contextRunner
                .withPropertyValues("agentscope.interview.enabled=false")
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(InterviewRepository.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewEvidenceProvider.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewQuestionGenerator.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewAnswerEvaluator.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewReportGenerator.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewService.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewController.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Infrastructure {

        @Bean
        JdbcOperations jdbcOperations() {
            return mock(JdbcOperations.class);
        }

        @Bean
        TransactionOperations transactionOperations() {
            return mock(TransactionOperations.class);
        }

        @Bean
        ChatTextModel chatTextModel() {
            return mock(ChatTextModel.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return mock(ObjectMapper.class);
        }

        @Bean
        EmbeddingClient embeddingClient() {
            return mock(EmbeddingClient.class);
        }

        @Bean
        KnowledgeRetriever knowledgeRetriever() {
            return mock(KnowledgeRetriever.class);
        }

        @Bean
        AuthenticatedUserContext authenticatedUserContext() {
            return mock(AuthenticatedUserContext.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
