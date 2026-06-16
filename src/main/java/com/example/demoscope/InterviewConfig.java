package com.example.demoscope;

import java.time.Clock;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InterviewConfig {

    @Bean
    InterviewRepository interviewRepository(
            JdbcOperations jdbc,
            TransactionOperations transactions,
            ObjectMapper objectMapper) {
        return new JdbcInterviewRepository(jdbc, transactions, objectMapper);
    }

    @Bean
    InterviewEvidenceProvider interviewEvidenceProvider(
            EmbeddingClient embeddingClient,
            KnowledgeRetriever knowledgeRetriever) {
        return new InterviewEvidenceProvider(
                embeddingClient,
                knowledgeRetriever);
    }

    @Bean
    InterviewAiJsonClient interviewAiJsonClient(
            ChatTextModel chatTextModel,
            ObjectMapper objectMapper) {
        return new InterviewAiJsonClient(chatTextModel, objectMapper);
    }

    @Bean
    InterviewQuestionGenerator interviewQuestionGenerator(
            InterviewAiJsonClient aiClient,
            InterviewEvidenceProvider evidenceProvider) {
        return new ModelInterviewQuestionGenerator(aiClient, evidenceProvider);
    }

    @Bean
    InterviewAnswerEvaluator interviewAnswerEvaluator(
            InterviewAiJsonClient aiClient,
            InterviewEvidenceProvider evidenceProvider) {
        return new ModelInterviewAnswerEvaluator(aiClient, evidenceProvider);
    }

    @Bean
    InterviewReportGenerator interviewReportGenerator(
            InterviewAiJsonClient aiClient) {
        return new ModelInterviewReportGenerator(aiClient);
    }

    @Bean
    InterviewService interviewService(
            InterviewRepository repository,
            InterviewQuestionGenerator questionGenerator,
            InterviewAnswerEvaluator answerEvaluator,
            InterviewReportGenerator reportGenerator,
            Clock clock,
            @Value("${agentscope.interview.max-main-questions:5}")
            int maxMainQuestions,
            @Value("${agentscope.interview.max-follow-ups:2}")
            int maxFollowUps) {
        return new InterviewService(
                repository,
                questionGenerator,
                answerEvaluator,
                reportGenerator,
                clock,
                UUID::randomUUID,
                maxMainQuestions,
                maxFollowUps);
    }
}
