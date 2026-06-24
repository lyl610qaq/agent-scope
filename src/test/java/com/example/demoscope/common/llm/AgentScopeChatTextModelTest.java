package com.example.demoscope.common.llm;

import com.example.demoscope.common.llm.AgentScopeChatTextModel;
import com.example.demoscope.common.llm.OpenAiRequestLogger;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AgentScopeChatTextModelTest {

    @Test
    void rejectsBlankApiKeyBeforeCreatingRemoteClient() {
        AgentScopeChatTextModel model = new AgentScopeChatTextModel(
                " ",
                "test-model",
                "https://example.invalid/v1",
                new OpenAiRequestLogger());

        assertThrows(
                IllegalStateException.class,
                () -> model.generate("system", "user"));
    }
}
