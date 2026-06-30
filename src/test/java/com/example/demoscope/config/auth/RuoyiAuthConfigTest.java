package com.example.demoscope.config.auth;

import com.example.demoscope.config.auth.RuoyiAuthConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.dev33.satoken.config.SaTokenConfig;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.codec.CompositeCodec;

class RuoyiAuthConfigTest {

    @Test
    void createsLocalPasswordlessRedisConfiguration() {
        Config config = RuoyiAuthConfig.redissonConfig("localhost", 6379, 0, "", "");

        SingleServerConfig server = config.useSingleServer();
        assertEquals("redis://localhost:6379", server.getAddress());
        assertEquals(0, server.getDatabase());
        assertNull(server.getUsername());
        assertNull(server.getPassword());
        assertInstanceOf(CompositeCodec.class, config.getCodec());
    }

    @Test
    void usesConfiguredTokenNameForSharedRuoYiRedisKeys() {
        SaTokenConfig config = RuoyiAuthConfig.saTokenConfig("Authorization");

        assertEquals("Authorization", config.getTokenName());
    }
}
