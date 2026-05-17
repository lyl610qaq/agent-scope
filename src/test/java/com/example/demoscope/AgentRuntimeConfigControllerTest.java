package com.example.demoscope;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "agentscope.openai.api-key=test-key",
        "agentscope.openai.model-name=Pro/zai-org/GLM-4.7",
        "agentscope.openai.base-url=https://api.siliconflow.cn/v1",
        "agentscope.rag.enabled=true",
        "agentscope.rag.knowledge-dir=data/knowledge",
        "agentscope.rag.top-k=3"
})
@AutoConfigureMockMvc
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
                .andExpect(jsonPath("$.ragEnabled").value(true))
                .andExpect(jsonPath("$.ragKnowledgeDir").value("data/knowledge"))
                .andExpect(jsonPath("$.ragTopK").value(3));
    }
}
