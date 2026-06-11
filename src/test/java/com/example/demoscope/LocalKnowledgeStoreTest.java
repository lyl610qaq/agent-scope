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
                        # Refund policy

                        Users can request refunds within 7 days after delivery.
                        Refunds are returned to the original payment method.
                        """);
        Files.writeString(
                knowledgeDir.resolve("install.txt"),
                "Install guide: configure OPENAI_API_KEY before starting the service.");

        Class<?> storeClass = Class.forName("com.example.demoscope.LocalKnowledgeStore");
        Constructor<?> constructor = storeClass.getConstructor(String.class, int.class, int.class, int.class);
        Object store = constructor.newInstance(knowledgeDir.toString(), 1, 2_000, 1);
        Method retrieve = storeClass.getMethod("retrieve", String.class);

        List<?> chunks = (List<?>) retrieve.invoke(store, "refund policy");

        assertEquals(1, chunks.size());
        Object chunk = chunks.get(0);
        assertTrue(((String) chunk.getClass().getMethod("source").invoke(chunk)).endsWith("refund-policy.md"));
        String content = (String) chunk.getClass().getMethod("content").invoke(chunk);
        assertTrue(content.contains("Refund policy") || content.contains("within 7 days"));
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
