package com.example.demoscope.config.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void interviewsAreOptInByDefault() throws Exception {
        Properties properties = loadProperties();

        assertEquals(
                "${AGENTSCOPE_INTERVIEW_ENABLED:false}",
                properties.getProperty("agentscope.interview.enabled"));
    }

    @Test
    void ruoyiLoginIsDirectAndOnlyTokenIdentityConfigRemains()
            throws Exception {
        Properties properties = loadProperties();

        assertEquals(
                "${AGENTSCOPE_RUOYI_TOKEN_NAME:Authorization}",
                properties.getProperty("agentscope.auth.ruoyi.token-name"));
        assertTrue(properties.getProperty("spring.data.redis.host")
                .startsWith("${AGENTSCOPE_RUOYI_REDIS_HOST:"));
        assertEquals(
                "${AGENTSCOPE_RUOYI_REDIS_PORT:6379}",
                properties.getProperty("spring.data.redis.port"));
        assertEquals(
                "${AGENTSCOPE_RUOYI_REDIS_DATABASE:0}",
                properties.getProperty("spring.data.redis.database"));

        String ruoyiAuthPrefix = "agentscope.auth.ruoyi.";
        assertFalse(properties.containsKey(ruoyiAuthPrefix + "base-url"));
        assertFalse(properties.containsKey(ruoyiAuthPrefix + "login-path"));
        assertFalse(properties.containsKey(ruoyiAuthPrefix + "logout-path"));
        assertFalse(properties.containsKey(
                ruoyiAuthPrefix + "connect-timeout"));
        assertFalse(properties.containsKey(
                ruoyiAuthPrefix + "read-timeout"));
        assertFalse(properties.containsKey(
                ruoyiAuthPrefix + "max-login-body-bytes"));
    }

    private Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        return properties;
    }
}
