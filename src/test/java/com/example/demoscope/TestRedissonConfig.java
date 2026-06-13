package com.example.demoscope;

import static org.mockito.Mockito.mock;

import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestRedissonConfig {

    @Bean
    RedissonClient testRedissonClient() {
        return mock(RedissonClient.class);
    }
}
