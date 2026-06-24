package com.example.demoscope.config.interview;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true")
public class InterviewDatabaseConfig {

    @Bean("agentPostgresDataSource")
    DataSource agentPostgresDataSource(
            @Value("${agentscope.pgvector.url:}") String url,
            @Value("${agentscope.pgvector.username:}") String username,
            @Value("${agentscope.pgvector.password:}") String password) {
        return dataSource(url, username, password);
    }

    @Bean
    @ConditionalOnMissingBean(JdbcOperations.class)
    JdbcOperations agentJdbcOperations(
            @Qualifier("agentPostgresDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    TransactionOperations interviewTransactions(
            JdbcOperations jdbc,
            @Qualifier("agentPostgresDataSource") DataSource fallbackDataSource) {
        return new TransactionTemplate(
                new DataSourceTransactionManager(
                        dataSourceFor(jdbc, fallbackDataSource)));
    }

    @Bean
    DataSourceInitializer interviewSchemaInitializer(
            JdbcOperations jdbc,
            @Qualifier("agentPostgresDataSource") DataSource fallbackDataSource) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(
                        new ClassPathResource("interview-schema.sql"));
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSourceFor(jdbc, fallbackDataSource));
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    static DataSource dataSource(
            String url,
            String username,
            String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "PGVECTOR_URL must be configured when interviews are enabled");
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    static DataSource dataSourceFor(
            JdbcOperations jdbc,
            DataSource fallbackDataSource) {
        if (jdbc instanceof JdbcTemplate jdbcTemplate
                && jdbcTemplate.getDataSource() != null) {
            return jdbcTemplate.getDataSource();
        }
        return fallbackDataSource;
    }
}
