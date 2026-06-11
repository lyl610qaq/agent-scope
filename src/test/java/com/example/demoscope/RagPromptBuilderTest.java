package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

class RagPromptBuilderTest {

    @Test
    void keepsOriginalMessageWhenRagIsDisabled() throws Exception {
        Object builder = builder(false);

        assertEquals("hello", build(builder, "hello", List.of(chunk("doc.md", "context"))));
    }

    @Test
    void addsRetrievedContextBeforeUserQuestionWhenEnabled() throws Exception {
        Object builder = builder(true);

        String prompt = build(
                builder,
                "What is the refund policy?",
                List.of(chunk("refund-policy.md", "Users can request refunds within 7 days.")));

        assertTrue(prompt.contains("Answer using the local knowledge base first"));
        assertTrue(prompt.contains("[1] refund-policy.md"));
        assertTrue(prompt.contains("Users can request refunds within 7 days."));
        assertTrue(prompt.endsWith("User question:\nWhat is the refund policy?"));
    }

    @Test
    void keepsOriginalMessageWhenNoContextWasRetrieved() throws Exception {
        Object builder = builder(true);

        assertEquals("hello", build(builder, "hello", List.of()));
    }

    private Object builder(boolean enabled) throws Exception {
        return Class.forName("com.example.demoscope.RagPromptBuilder")
                .getConstructor(boolean.class)
                .newInstance(enabled);
    }

    private Object chunk(String source, String content) throws Exception {
        Constructor<?> constructor = Class.forName("com.example.demoscope.KnowledgeChunk")
                .getConstructor(String.class, String.class);
        return constructor.newInstance(source, content);
    }

    private String build(Object builder, String message, List<?> chunks) throws Exception {
        Method build = builder.getClass().getMethod("build", String.class, List.class);
        return (String) build.invoke(builder, message, chunks);
    }
}
