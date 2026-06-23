package com.example.demoscope.interview.config;

import com.example.demoscope.interview.agent.AgenticInterviewAnswerEvaluator;
import com.example.demoscope.interview.agent.AgenticInterviewQuestionGenerator;
import com.example.demoscope.interview.agent.AgenticInterviewReportGenerator;
import com.example.demoscope.interview.agent.InterviewAgentOrchestrator;
import com.example.demoscope.interview.agent.InterviewMemoryManagerAgent;
import com.example.demoscope.interview.agent.InterviewRagPlannerAgent;
import com.example.demoscope.interview.agent.InterviewRouterAgent;
import com.example.demoscope.interview.agent.InterviewTargetAgent;
import com.example.demoscope.interview.agent.ModelInterviewerAgent;
import com.example.demoscope.interview.agent.ModelInterviewMemoryManagerAgent;
import com.example.demoscope.interview.agent.ModelInterviewRagPlannerAgent;
import com.example.demoscope.interview.agent.ModelInterviewRouterAgent;
import com.example.demoscope.interview.agent.ModelJavaSkillAgent;
import com.example.demoscope.interview.agent.ModelProjectAgent;
import com.example.demoscope.interview.agent.ModelScoreAgent;
import com.example.demoscope.interview.application.DefaultInterviewMemoryWriter;
import com.example.demoscope.interview.application.InterviewEvidenceProvider;
import com.example.demoscope.interview.application.InterviewMemoryContextProvider;
import com.example.demoscope.interview.application.InterviewService;
import com.example.demoscope.interview.domain.InterviewAnswerEvaluator;
import com.example.demoscope.interview.domain.InterviewMemoryWriter;
import com.example.demoscope.interview.domain.InterviewQuestionGenerator;
import com.example.demoscope.interview.domain.InterviewReportGenerator;
import com.example.demoscope.interview.domain.InterviewRepository;
import com.example.demoscope.interview.infrastructure.InterviewAiJsonClient;
import com.example.demoscope.interview.infrastructure.JdbcInterviewRepository;
import com.example.demoscope.knowledge.domain.EmbeddingClient;
import com.example.demoscope.knowledge.domain.KnowledgeRetriever;
import com.example.demoscope.llm.domain.ChatTextModel;
import com.example.demoscope.memory.application.LongTermMemoryPolicy;
import com.example.demoscope.memory.domain.LongTermMemoryRepository;
import com.example.demoscope.memory.domain.ShortTermMemoryStore;
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
    InterviewReportGenerator interviewReportGenerator(
            InterviewAgentOrchestrator orchestrator) {
        return new AgenticInterviewReportGenerator(orchestrator);
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
