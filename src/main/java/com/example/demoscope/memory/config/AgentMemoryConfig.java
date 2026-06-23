package com.example.demoscope.memory.config;

import com.example.demoscope.agent.infrastructure.AgentScopeChatTextModel;
import com.example.demoscope.identity.application.BearerTokenExtractor;
import com.example.demoscope.knowledge.application.PromptContextBuilder;
import com.example.demoscope.knowledge.domain.EmbeddingClient;
import com.example.demoscope.knowledge.domain.KnowledgeRetriever;
import com.example.demoscope.knowledge.domain.RetrievalSettings;
import com.example.demoscope.knowledge.infrastructure.PgVectorKnowledgeStore;
import com.example.demoscope.knowledge.infrastructure.SiliconFlowEmbeddingClient;
import com.example.demoscope.llm.domain.ChatTextModel;
import com.example.demoscope.llm.infrastructure.OpenAiRequestLogger;
import com.example.demoscope.memory.application.LongTermMemoryPolicy;
import com.example.demoscope.memory.application.MemoryOrchestrator;
import com.example.demoscope.memory.domain.LongTermMemoryExtractor;
import com.example.demoscope.memory.domain.LongTermMemoryRepository;
import com.example.demoscope.memory.domain.ShortTermMemoryStore;
import com.example.demoscope.memory.infrastructure.EmptyLongTermMemoryRepository;
import com.example.demoscope.memory.infrastructure.InMemoryShortTermMemoryStore;
import com.example.demoscope.memory.infrastructure.ModelLongTermMemoryExtractor;
import com.example.demoscope.memory.infrastructure.PgVectorLongTermMemoryRepository;
import java.time.Clock;
import java.util.List;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
public class AgentMemoryConfig {

    @Bean
    Clock memoryClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(BearerTokenExtractor.class)
    BearerTokenExtractor bearerTokenExtractor(
            @Value("${agentscope.auth.ruoyi.token-name:Authorization}") String tokenHeaderName) {
        return new BearerTokenExtractor(tokenHeaderName);
    }

    @Bean
    ShortTermMemoryStore shortTermMemoryStore(
            @Value("${agentscope.memory.short-term.max-turns:10}") int maxTurns) {
        return new InMemoryShortTermMemoryStore(maxTurns);
    }

    @Bean
    LongTermMemoryPolicy longTermMemoryPolicy() {
        return new LongTermMemoryPolicy();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper memoryObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    ChatTextModel chatTextModel(
            @Value("${agentscope.openai.api-key:}") String apiKey,
            @Value("${agentscope.openai.model-name:Pro/zai-org/GLM-4.7}") String modelName,
            @Value("${agentscope.openai.base-url:}") String baseUrl,
            OpenAiRequestLogger requestLogger) {
        return new AgentScopeChatTextModel(apiKey, modelName, baseUrl, requestLogger);
    }

    @Bean
    LongTermMemoryExtractor longTermMemoryExtractor(
            ChatTextModel chatTextModel,
            ObjectMapper objectMapper) {
        return new ModelLongTermMemoryExtractor(chatTextModel, objectMapper);
    }

    @Bean
    PromptContextBuilder promptContextBuilder() {
        return new PromptContextBuilder();
    }

    @Bean
    EmbeddingClient embeddingClient(
            @Value("${agentscope.embedding.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
            @Value("${agentscope.embedding.api-key:}") String apiKey,
            @Value("${agentscope.embedding.model:Qwen/Qwen3-Embedding-4B}") String model,
            @Value("${agentscope.embedding.dimensions:1024}") int dimensions) {
        return new SiliconFlowEmbeddingClient(baseUrl, apiKey, model, dimensions);
    }

    @Bean("knowledgeRetrievalSettings")
    RetrievalSettings knowledgeRetrievalSettings(
            @Value("${agentscope.retrieval.knowledge.vector-top-k:30}") int vectorTopK,
            @Value("${agentscope.retrieval.knowledge.final-top-n:6}") int finalTopN,
            @Value("${agentscope.retrieval.knowledge.min-score:0.70}") double minScore) {
        return new RetrievalSettings(vectorTopK, finalTopN, minScore);
    }

    @Bean("longTermMemoryRetrievalSettings")
    RetrievalSettings longTermMemoryRetrievalSettings(
            @Value("${agentscope.retrieval.long-term-memory.vector-top-k:20}") int vectorTopK,
            @Value("${agentscope.retrieval.long-term-memory.final-top-n:5}") int finalTopN,
            @Value("${agentscope.retrieval.long-term-memory.min-score:0.72}") double minScore) {
        return new RetrievalSettings(vectorTopK, finalTopN, minScore);
    }

    @Bean
    @ConditionalOnProperty(name = "agentscope.pgvector.enabled", havingValue = "true")
    @ConditionalOnMissingBean(JdbcOperations.class)
    JdbcOperations pgVectorJdbcOperations(
            @Value("${agentscope.pgvector.url:}") String url,
            @Value("${agentscope.pgvector.username:}") String username,
            @Value("${agentscope.pgvector.password:}") String password) {
        return new JdbcTemplate(dataSource(url, username, password));
    }

    @Bean
    @ConditionalOnProperty(name = "agentscope.pgvector.enabled", havingValue = "true")
    KnowledgeRetriever pgVectorKnowledgeRetriever(
            JdbcOperations jdbc,
            @Qualifier("knowledgeRetrievalSettings") RetrievalSettings settings) {
        PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(jdbc, settings);
        store.initializeSchema();
        return store;
    }

    @Bean
    @ConditionalOnProperty(name = "agentscope.pgvector.enabled", havingValue = "true")
    LongTermMemoryRepository pgVectorLongTermMemoryRepository(
            JdbcOperations jdbc,
            EmbeddingClient embeddingClient,
            @Qualifier("longTermMemoryRetrievalSettings") RetrievalSettings settings,
            Clock clock) {
        PgVectorLongTermMemoryRepository repository = new PgVectorLongTermMemoryRepository(
                jdbc,
                embeddingClient,
                settings,
                clock);
        repository.initializeSchema();
        return repository;
    }

    @Bean
    @ConditionalOnProperty(
            name = "agentscope.pgvector.enabled",
            havingValue = "false",
            matchIfMissing = true)
    LongTermMemoryRepository emptyLongTermMemoryRepository() {
        return new EmptyLongTermMemoryRepository();
    }

    @Bean
    @ConditionalOnProperty(
            name = "agentscope.pgvector.enabled",
            havingValue = "false",
            matchIfMissing = true)
    KnowledgeRetriever disabledKnowledgeRetriever() {
        return query -> List.of();
    }

    @Bean
    MemoryOrchestrator memoryOrchestrator(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            KnowledgeRetriever knowledgeRetriever,
            LongTermMemoryExtractor longTermMemoryExtractor,
            LongTermMemoryPolicy longTermMemoryPolicy,
            EmbeddingClient embeddingClient,
            Clock clock) {
        return new MemoryOrchestrator(
                shortTermMemoryStore,
                longTermMemoryRepository,
                knowledgeRetriever,
                longTermMemoryExtractor,
                longTermMemoryPolicy,
                embeddingClient,
                clock);
    }

    private DataSource dataSource(String url, String username, String password) {
        if (url.isBlank()) {
            throw new IllegalStateException("PGVECTOR_URL must be configured when pgvector is enabled");
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
