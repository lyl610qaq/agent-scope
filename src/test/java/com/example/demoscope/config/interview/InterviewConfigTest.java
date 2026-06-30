package com.example.demoscope.config.interview;

import com.example.demoscope.biz.auth.AuthenticatedUserContext;
import com.example.demoscope.biz.interview.AgenticInterviewAnswerEvaluator;
import com.example.demoscope.biz.interview.AgenticInterviewQuestionGenerator;
import com.example.demoscope.biz.interview.InterviewAgentOrchestrator;
import com.example.demoscope.biz.interview.InterviewMemoryManagerAgent;
import com.example.demoscope.biz.interview.InterviewRagPlannerAgent;
import com.example.demoscope.biz.interview.InterviewRouterAgent;
import com.example.demoscope.biz.interview.ScoreReviewAgent;
import com.example.demoscope.biz.interview.ScoreReviewBiz;
import com.example.demoscope.controller.interview.InterviewController;
import com.example.demoscope.biz.rag.InterviewEvidenceProvider;
import com.example.demoscope.biz.memory.InterviewMemoryContextProvider;
import com.example.demoscope.service.interview.InterviewService;
import com.example.demoscope.config.interview.InterviewConfig;
import com.example.demoscope.domain.interview.InterviewAnswerEvaluator;
import com.example.demoscope.domain.interview.InterviewMemoryWriter;
import com.example.demoscope.domain.interview.InterviewQuestionGenerator;
import com.example.demoscope.domain.interview.InterviewReportGenerator;
import com.example.demoscope.domain.interview.InterviewRepository;
import com.example.demoscope.common.embedding.EmbeddingClient;
import com.example.demoscope.domain.rag.KnowledgeRetriever;
import com.example.demoscope.common.llm.ChatTextModel;
import com.example.demoscope.biz.memory.LongTermMemoryPolicy;
import com.example.demoscope.domain.memory.LongTermMemoryRepository;
import com.example.demoscope.domain.memory.ShortTermMemoryStore;
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
                    assertThat(context).hasSingleBean(
                            InterviewAgentOrchestrator.class);
                    assertThat(context).hasSingleBean(InterviewRouterAgent.class);
                    assertThat(context).hasSingleBean(
                            InterviewRagPlannerAgent.class);
                    assertThat(context).hasSingleBean(
                            InterviewMemoryManagerAgent.class);
                    assertThat(context).hasSingleBean(
                            InterviewMemoryContextProvider.class);
                    assertThat(context).hasSingleBean(InterviewMemoryWriter.class);
                    assertThat(context).hasBean("interviewerAgent");
                    assertThat(context).hasBean("projectAgent");
                    assertThat(context).hasBean("javaSkillAgent");
                    assertThat(context).hasBean("scoreAgent");
                    assertThat(context).hasSingleBean(ScoreReviewAgent.class);
                    assertThat(context.getBean(InterviewQuestionGenerator.class))
                            .isInstanceOf(
                                    AgenticInterviewQuestionGenerator.class);
                    assertThat(context.getBean(InterviewAnswerEvaluator.class))
                            .isInstanceOf(
                                    AgenticInterviewAnswerEvaluator.class);
                    assertThat(context.getBean(InterviewReportGenerator.class))
                            .isInstanceOf(
                                    ScoreReviewBiz.class);
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
                            .doesNotHaveBean(InterviewAgentOrchestrator.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewRouterAgent.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewRagPlannerAgent.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewMemoryManagerAgent.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewService.class);
                    assertThat(context)
                            .doesNotHaveBean(InterviewController.class);
                });
    }

    @Test
    void missingInterviewFlagCreatesNoInterviewComponents() {
        contextRunner.run(context -> {
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
                    .doesNotHaveBean(InterviewAgentOrchestrator.class);
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

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
