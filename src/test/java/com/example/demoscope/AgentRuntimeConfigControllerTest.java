package com.example.demoscope;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "agentscope.openai.api-key=test-key",
        "agentscope.openai.model-name=Pro/zai-org/GLM-4.7",
        "agentscope.openai.base-url=https://api.siliconflow.cn/v1",
        "agentscope.auth.ruoyi.base-url=http://127.0.0.1:18081",
        "agentscope.auth.ruoyi.token-name=X-RuoYi-Token",
        "agentscope.embedding.api-key=embedding-key",
        "agentscope.embedding.model=Qwen/Qwen3-Embedding-4B",
        "agentscope.pgvector.enabled=true",
        "agentscope.pgvector.url=jdbc:postgresql://localhost:5432/agent",
        "agentscope.retrieval.knowledge.vector-top-k=30",
        "agentscope.retrieval.knowledge.final-top-n=6",
        "agentscope.retrieval.knowledge.min-score=0.70",
        "agentscope.retrieval.long-term-memory.vector-top-k=20",
        "agentscope.retrieval.long-term-memory.final-top-n=5",
        "agentscope.retrieval.long-term-memory.min-score=0.72"
})
@AutoConfigureMockMvc
@Import(TestRedissonConfig.class)
class AgentRuntimeConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runtimeConfigEndpointReturnsEffectiveSettings() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelName").value("Pro/zai-org/GLM-4.7"))
                .andExpect(jsonPath("$.baseUrl").value("https://api.siliconflow.cn/v1"))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.knowledge.vectorTopK").value(30))
                .andExpect(jsonPath("$.knowledge.finalTopN").value(6))
                .andExpect(jsonPath("$.knowledge.minScore").value(0.70))
                .andExpect(jsonPath("$.longTermMemory.vectorTopK").value(20))
                .andExpect(jsonPath("$.longTermMemory.finalTopN").value(5))
                .andExpect(jsonPath("$.longTermMemory.minScore").value(0.72))
                .andExpect(jsonPath("$.ruoyiAuth.loginPath").value("/api/auth/login"))
                .andExpect(jsonPath("$.ruoyiAuth.logoutPath").value("/api/auth/logout"))
                .andExpect(jsonPath("$.ruoyiAuth.mePath").value("/api/auth/me"))
                .andExpect(jsonPath("$.ruoyiAuth.tokenHeaderName").value("X-RuoYi-Token"))
                .andExpect(jsonPath("$.ruoyiAuth.baseUrl").doesNotExist())
                .andExpect(jsonPath("$.ragTopK").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        JdbcOperations testJdbcOperations() {
            return mock(JdbcOperations.class);
        }
    }
}
