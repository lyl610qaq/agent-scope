package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LocalKnowledgeStoreTest {

    @Test
    void retrievesRelevantChunksFromLocalMarkdownAndTextFiles() throws Exception {
        Path knowledgeDir = Files.createDirectories(Path.of("target", "rag-test", UUID.randomUUID().toString()));
        Files.writeString(
                knowledgeDir.resolve("refund-policy.md"),
                """
                        # 售后政策

                        退货规则：用户在签收后 7 天内可以申请无理由退货。
                        退款会在仓库验收后 3 个工作日内原路返回。
                        """);
        Files.writeString(
                knowledgeDir.resolve("install.txt"),
                "安装说明：启动服务前需要配置 OPENAI_API_KEY。");

        Class<?> storeClass = Class.forName("com.example.demoscope.LocalKnowledgeStore");
        Constructor<?> constructor = storeClass.getConstructor(String.class, int.class, int.class, int.class);
        Object store = constructor.newInstance(knowledgeDir.toString(), 2, 2_000, 1);
        Method retrieve = storeClass.getMethod("retrieve", String.class);

        List<?> chunks = (List<?>) retrieve.invoke(store, "退货规则是什么");

        assertEquals(1, chunks.size());
        Object chunk = chunks.get(0);
        assertTrue(((String) chunk.getClass().getMethod("source").invoke(chunk)).endsWith("refund-policy.md"));
        assertTrue(((String) chunk.getClass().getMethod("content").invoke(chunk)).contains("7 天内可以申请无理由退货"));
    }

    @Test
    void returnsNoChunksWhenKnowledgeDirectoryDoesNotExist() throws Exception {
        Path knowledgeDir = Path.of("target", "rag-test", UUID.randomUUID().toString());
        Class<?> storeClass = Class.forName("com.example.demoscope.LocalKnowledgeStore");
        Object store = storeClass.getConstructor(String.class, int.class, int.class, int.class).newInstance(
                knowledgeDir.resolve("missing").toString(),
                3,
                2_000,
                1);
        Method retrieve = storeClass.getMethod("retrieve", String.class);

        assertTrue(((List<?>) retrieve.invoke(store, "anything")).isEmpty());
    }
}
