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

        assertEquals("你好", build(builder, "你好", List.of(chunk("doc.md", "资料"))));
    }

    @Test
    void addsRetrievedContextBeforeUserQuestionWhenEnabled() throws Exception {
        Object builder = builder(true);

        String prompt = build(
                builder,
                "退货规则是什么",
                List.of(chunk("refund-policy.md", "用户签收后 7 天内可以申请无理由退货。")));

        assertTrue(prompt.contains("请优先依据下面的本地知识库资料回答"));
        assertTrue(prompt.contains("[1] refund-policy.md"));
        assertTrue(prompt.contains("用户签收后 7 天内可以申请无理由退货。"));
        assertTrue(prompt.endsWith("用户问题：\n退货规则是什么"));
    }

    @Test
    void keepsOriginalMessageWhenNoContextWasRetrieved() throws Exception {
        Object builder = builder(true);

        assertEquals("你好", build(builder, "你好", List.of()));
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
