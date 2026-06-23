package com.example.demoscope.llm.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenAiRequestLoggerTest {

    @Test
    void requestPreviewShowsSiliconFlowPayloadWithMaskedKey() throws Exception {
        OpenAiRequestLogger requestLogger = new OpenAiRequestLogger();

        String preview = requestLogger.buildRequestPreview(
                "sk-1234567890abcdef",
                "https://api.siliconflow.cn/v1",
                "Pro/zai-org/GLM-4.7",
                "hello \"agent\"");

        assertTrue(preview.contains("--url https://api.siliconflow.cn/v1/chat/completions"));
        assertTrue(preview.contains("Authorization: Bearer sk-1...cdef"));
        assertTrue(preview.contains("\"model\": \"Pro/zai-org/GLM-4.7\""));
        assertTrue(preview.contains("{\"role\": \"user\", \"content\": \"hello \\\"agent\\\"\"}"));
        assertFalse(preview.contains("sk-1234567890abcdef"));
    }

    @Test
    void requestPreviewEscapesMultilineRagPrompt() throws Exception {
        OpenAiRequestLogger requestLogger = new OpenAiRequestLogger();

        String preview = requestLogger.buildRequestPreview(
                "sk-1234567890abcdef",
                "https://api.siliconflow.cn/v1/",
                "Pro/zai-org/GLM-4.7",
                "Local knowledge base:\n[1] demo.md\nRAG enabled");

        assertTrue(preview.contains("--url https://api.siliconflow.cn/v1/chat/completions"));
        assertTrue(preview.contains("Local knowledge base:\\n[1] demo.md\\nRAG enabled"));
    }
}
