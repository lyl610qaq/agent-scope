package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class PromptContextBuilderTest {

    private final PromptContextBuilder builder = new PromptContextBuilder();

    @Test
    void labelsEachAvailableContextLayer() {
        MemoryContext context = new MemoryContext(
                List.of(new MemoryTurn("上一问", "上一答", Instant.parse("2026-06-06T10:00:00Z"))),
                List.of(new LongTermMemory(
                        "memory-1",
                        LongTermMemoryCategory.PREFERENCE,
                        "用户偏好中文回答",
                        "conversation-a",
                        0.9,
                        Instant.parse("2026-06-06T10:00:00Z"),
                        Instant.parse("2026-06-06T10:00:00Z"))),
                List.of(new KnowledgeChunk("guide.md", "项目使用 Java 17")));

        String prompt = builder.build("You are helpful.", context, "现在用什么版本？");

        assertTrue(prompt.contains("系统指令"));
        assertTrue(prompt.contains("短期记忆"));
        assertTrue(prompt.contains("长期记忆"));
        assertTrue(prompt.contains("知识库资料"));
        assertTrue(prompt.endsWith("用户问题：\n现在用什么版本？"));
    }

    @Test
    void omitsEmptyContextSections() {
        String prompt = builder.build(
                "You are helpful.",
                new MemoryContext(List.of(), List.of(), List.of()),
                "你好");

        assertFalse(prompt.contains("短期记忆"));
        assertFalse(prompt.contains("长期记忆"));
        assertFalse(prompt.contains("知识库资料"));
    }
}
