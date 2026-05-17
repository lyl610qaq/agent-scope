package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class OpenAiRequestLoggerTest {

    @Test
    void requestPreviewShowsSiliconFlowPayloadWithMaskedKey() throws Exception {
        Class<?> loggerClass = Class.forName("com.example.demoscope.OpenAiRequestLogger");
        Object requestLogger = loggerClass.getConstructor().newInstance();
        Method buildRequestPreview = loggerClass.getDeclaredMethod(
                "buildRequestPreview",
                String.class,
                String.class,
                String.class,
                String.class);
        buildRequestPreview.setAccessible(true);

        String preview = (String) buildRequestPreview.invoke(
                requestLogger,
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
        Class<?> loggerClass = Class.forName("com.example.demoscope.OpenAiRequestLogger");
        Object requestLogger = loggerClass.getConstructor().newInstance();
        Method buildRequestPreview = loggerClass.getDeclaredMethod(
                "buildRequestPreview",
                String.class,
                String.class,
                String.class,
                String.class);
        buildRequestPreview.setAccessible(true);

        String preview = (String) buildRequestPreview.invoke(
                requestLogger,
                "sk-1234567890abcdef",
                "https://api.siliconflow.cn/v1/",
                "Pro/zai-org/GLM-4.7",
                "本地知识库资料：\n[1] demo.md\nRAG 已开启");

        assertTrue(preview.contains("--url https://api.siliconflow.cn/v1/chat/completions"));
        assertTrue(preview.contains("本地知识库资料：\\n[1] demo.md\\nRAG 已开启"));
    }
}
