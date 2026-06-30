package com.example.demoscope.config.interview;

import com.example.demoscope.biz.interview.AgenticInterviewAnswerEvaluator;
import com.example.demoscope.biz.interview.AgenticInterviewQuestionGenerator;
import com.example.demoscope.biz.interview.AgenticInterviewReportGenerator;
import com.example.demoscope.biz.interview.InterviewAgentOrchestrator;
import com.example.demoscope.biz.interview.InterviewMemoryManagerAgent;
import com.example.demoscope.biz.interview.InterviewRagPlannerAgent;
import com.example.demoscope.biz.interview.InterviewRouterAgent;
import com.example.demoscope.biz.interview.InterviewTargetAgent;
import com.example.demoscope.biz.interview.ModelInterviewerAgent;
import com.example.demoscope.biz.interview.ModelInterviewMemoryManagerAgent;
import com.example.demoscope.biz.interview.ModelInterviewRagPlannerAgent;
import com.example.demoscope.biz.interview.ModelInterviewRouterAgent;
import com.example.demoscope.biz.interview.ModelJavaSkillAgent;
import com.example.demoscope.biz.interview.ModelProjectAgent;
import com.example.demoscope.biz.interview.ModelScoreAgent;
import com.example.demoscope.biz.interview.ModelScoreReviewAgent;
import com.example.demoscope.biz.interview.ScoreDraftGenerator;
import com.example.demoscope.biz.interview.ScoreReviewAgent;
import com.example.demoscope.biz.interview.ScoreReviewBiz;
import com.example.demoscope.biz.memory.DefaultInterviewMemoryWriter;
import com.example.demoscope.biz.rag.InterviewEvidenceProvider;
import com.example.demoscope.biz.memory.InterviewMemoryContextProvider;
import com.example.demoscope.service.interview.InterviewService;
import com.example.demoscope.domain.interview.InterviewAnswerEvaluator;
import com.example.demoscope.domain.interview.InterviewMemoryWriter;
import com.example.demoscope.domain.interview.InterviewQuestionGenerator;
import com.example.demoscope.domain.interview.InterviewReportGenerator;
import com.example.demoscope.domain.interview.InterviewRepository;
import com.example.demoscope.common.llm.InterviewAiJsonClient;
import com.example.demoscope.common.jdbc.JdbcInterviewRepository;
import com.example.demoscope.common.embedding.EmbeddingClient;
import com.example.demoscope.domain.rag.KnowledgeRetriever;
import com.example.demoscope.common.llm.ChatTextModel;
import com.example.demoscope.biz.memory.LongTermMemoryPolicy;
import com.example.demoscope.domain.memory.LongTermMemoryRepository;
import com.example.demoscope.domain.memory.ShortTermMemoryStore;
import java.time.Clock;
import java.util.List;
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
        havingValue = "true")
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
    InterviewRouterAgent interviewRouterAgent(InterviewAiJsonClient aiClient) {
        return new ModelInterviewRouterAgent(aiClient);
    }

    @Bean
    InterviewRagPlannerAgent interviewRagPlannerAgent(
            InterviewAiJsonClient aiClient) {
        return new ModelInterviewRagPlannerAgent(aiClient);
    }

    @Bean
    InterviewMemoryManagerAgent interviewMemoryManagerAgent(
            InterviewAiJsonClient aiClient) {
        return new ModelInterviewMemoryManagerAgent(aiClient);
    }

    @Bean("interviewerAgent")
    InterviewTargetAgent interviewerAgent(InterviewAiJsonClient aiClient) {
        return new ModelInterviewerAgent(aiClient);
    }

    @Bean("projectAgent")
    InterviewTargetAgent projectAgent(InterviewAiJsonClient aiClient) {
        return new ModelProjectAgent(aiClient);
    }

    @Bean("javaSkillAgent")
    InterviewTargetAgent javaSkillAgent(InterviewAiJsonClient aiClient) {
        return new ModelJavaSkillAgent(aiClient);
    }

    @Bean("scoreAgent")
    InterviewTargetAgent scoreAgent(InterviewAiJsonClient aiClient) {
        return new ModelScoreAgent(aiClient);
    }

    @Bean
    ScoreReviewAgent scoreReviewAgent(InterviewAiJsonClient aiClient) {
        return new ModelScoreReviewAgent(aiClient);
    }

    @Bean
    InterviewMemoryContextProvider interviewMemoryContextProvider(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            EmbeddingClient embeddingClient) {
        return new InterviewMemoryContextProvider(
                shortTermMemoryStore,
                longTermMemoryRepository,
                embeddingClient);
    }

    @Bean
    InterviewMemoryWriter interviewMemoryWriter(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            LongTermMemoryPolicy longTermMemoryPolicy,
            Clock clock) {
        return new DefaultInterviewMemoryWriter(
                shortTermMemoryStore,
                longTermMemoryRepository,
                longTermMemoryPolicy,
                clock);
    }

    @Bean
    InterviewAgentOrchestrator interviewAgentOrchestrator(
            InterviewRouterAgent routerAgent,
            InterviewRagPlannerAgent plannerAgent,
            InterviewEvidenceProvider evidenceProvider,
            InterviewMemoryContextProvider memoryContextProvider,
            InterviewMemoryManagerAgent memoryManagerAgent,
            InterviewMemoryWriter memoryWriter,
            List<InterviewTargetAgent> targetAgents,
            @Value("${agentscope.interview.agent.max-evidence:6}")
            int maxEvidence) {
        return new InterviewAgentOrchestrator(
                routerAgent,
                plannerAgent,
                evidenceProvider,
                memoryContextProvider,
                memoryManagerAgent,
                memoryWriter,
                targetAgents,
                maxEvidence);
    }

    @Bean
    InterviewQuestionGenerator interviewQuestionGenerator(
            InterviewAgentOrchestrator orchestrator) {
        return new AgenticInterviewQuestionGenerator(orchestrator);
    }

    @Bean
    InterviewAnswerEvaluator interviewAnswerEvaluator(
            InterviewAgentOrchestrator orchestrator) {
        return new AgenticInterviewAnswerEvaluator(orchestrator);
    }

    @Bean
    ScoreDraftGenerator scoreDraftGenerator(
            InterviewAgentOrchestrator orchestrator) {
        AgenticInterviewReportGenerator generator =
                new AgenticInterviewReportGenerator(orchestrator);
        return generator::generate;
    }

    @Bean
    InterviewReportGenerator interviewReportGenerator(
            ScoreDraftGenerator scoreDraftGenerator,
            ScoreReviewAgent scoreReviewAgent,
            @Value("${agentscope.interview.score-review.max-attempts:2}")
            int maxAttempts) {
        return new ScoreReviewBiz(
                scoreDraftGenerator,
                scoreReviewAgent,
                maxAttempts);
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
