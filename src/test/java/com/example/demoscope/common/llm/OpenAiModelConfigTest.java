package com.example.demoscope.common.llm;

import com.example.demoscope.testsupport.TestRedissonConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "AGENTSCOPE_OPENAI_MODEL_NAME=Pro/zai-org/GLM-4.7",
        "agentscope.interview.enabled=false"
})
@Import(TestRedissonConfig.class)
class OpenAiModelConfigTest {

    @Value("${agentscope.openai.model-name}")
    private String modelName;

    @Test
    void modelNameCanBeOverriddenByEnvironmentStyleProperty() {
        assertEquals("Pro/zai-org/GLM-4.7", modelName);
    }
}
