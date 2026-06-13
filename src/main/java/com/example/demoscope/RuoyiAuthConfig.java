package com.example.demoscope;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class RuoyiAuthConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    RedissonClient ruoyiRedissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password) {
        return Redisson.create(redissonConfig(host, port, database, username, password));
    }

    @Bean
    SaTokenConfig ruoyiSaTokenConfig(
            @Value("${agentscope.auth.ruoyi.token-name:Authorization}") String tokenName) {
        SaTokenConfig config = saTokenConfig(tokenName);
        SaManager.setConfig(config);
        return config;
    }

    @Bean
    SaTokenDao ruoyiSaTokenDao(
            RedissonClient redissonClient,
            SaTokenConfig ruoyiSaTokenConfig) {
        SaManager.setConfig(ruoyiSaTokenConfig);
        SaTokenDao dao = new RedissonSaTokenDao(redissonClient);
        SaManager.setSaTokenDao(dao);
        return dao;
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

    static Config redissonConfig(
            String host,
            int port,
            int database,
            String username,
            String password) {
        Config config = new Config();
        config.setCodec(ruoyiCodec());
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        if (StringUtils.hasText(username)) {
            server.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        return config;
    }

    static SaTokenConfig saTokenConfig(String tokenName) {
        return new SaTokenConfig().setTokenName(tokenName);
    }

    private static CompositeCodec ruoyiCodec() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        TypedJsonJacksonCodec jsonCodec = new TypedJsonJacksonCodec(Object.class, objectMapper);
        return new CompositeCodec(StringCodec.INSTANCE, jsonCodec, jsonCodec);
    }
}
