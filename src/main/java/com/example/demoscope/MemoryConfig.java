package com.example.demoscope;

import java.time.Clock;
import java.util.List;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
public class MemoryConfig {

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
    @ConditionalOnMissingBean(SaTokenFacade.class)
    SaTokenFacade saTokenFacade() {
        return new DefaultSaTokenFacade();
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticatedUserContext.class)
    AuthenticatedUserContext authenticatedUserContext(
            BearerTokenExtractor tokenExtractor,
            SaTokenFacade saTokenFacade) {
        return new RuoyiSaTokenUserContext(tokenExtractor, saTokenFacade);
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

    @Bean
    @ConditionalOnProperty(
            name = "agentscope.pgvector.enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(JdbcOperations.class)
    JdbcOperations pgVectorJdbcOperations(
            @Value("${agentscope.pgvector.url:}") String url,
            @Value("${agentscope.pgvector.username:}") String username,
            @Value("${agentscope.pgvector.password:}") String password) {
        return new JdbcTemplate(dataSource(url, username, password));
    }

    @Bean
    @ConditionalOnProperty(
            name = "agentscope.pgvector.enabled",
            havingValue = "true")
    PgVectorKnowledgeStore pgVectorKnowledgeStore(
            JdbcOperations pgVectorJdbcOperations,
            EmbeddingClient embeddingClient,
            @Value("${agentscope.rag.top-k:3}") int topK,
            @Value("${agentscope.embedding.dimensions:1024}") int embeddingDimensions,
            @Value("${agentscope.rag.max-distance:}") String maxDistance) {
        Double distance = maxDistance.isBlank() ? null : Double.valueOf(maxDistance);
        PgVectorKnowledgeStore store = new PgVectorKnowledgeStore(
                pgVectorJdbcOperations,
                embeddingClient,
                topK,
                embeddingDimensions,
                distance);
        store.initializeSchema();
        return store;
    }

    @Bean
    @ConditionalOnProperty(
            name = "agentscope.pgvector.enabled",
            havingValue = "true")
    LongTermMemoryRepository postgresLongTermMemoryRepository(
            JdbcOperations pgVectorJdbcOperations,
            EmbeddingClient embeddingClient,
            LongTermMemoryPolicy policy,
            Clock clock,
            @Value("${agentscope.memory.long-term.top-k:5}") int topK,
            @Value("${agentscope.embedding.dimensions:1024}") int embeddingDimensions,
            @Value("${agentscope.memory.long-term.max-distance:}") String maxDistance) {
        Double distance = maxDistance.isBlank() ? null : Double.valueOf(maxDistance);
        PostgresLongTermMemoryRepository repository = new PostgresLongTermMemoryRepository(
                pgVectorJdbcOperations,
                embeddingClient,
                policy,
                clock,
                topK,
                embeddingDimensions,
                distance);
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
            Clock clock) {
        return new MemoryOrchestrator(
                shortTermMemoryStore,
                longTermMemoryRepository,
                knowledgeRetriever,
                longTermMemoryExtractor,
                longTermMemoryPolicy,
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
