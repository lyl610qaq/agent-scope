package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "agentscope.openai.api-key=test-key",
        "agentscope.embedding.api-key=test-embedding-key",
        "agentscope.auth.ruoyi.base-url=http://127.0.0.1:18081"
})
@Import(TestRedissonConfig.class)
class DemoScopeApplicationTests {

    @Autowired
    @Qualifier("knowledgeRetrievalSettings")
    private RetrievalSettings knowledgeSettings;

    @Autowired
    @Qualifier("longTermMemoryRetrievalSettings")
    private RetrievalSettings longTermMemorySettings;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void contextLoads() {
    }

    @Test
    void usesLayeredRetrievalDefaults() {
        assertEquals(new RetrievalSettings(30, 6, 0.70), knowledgeSettings);
        assertEquals(new RetrievalSettings(20, 5, 0.72), longTermMemorySettings);
    }
}
