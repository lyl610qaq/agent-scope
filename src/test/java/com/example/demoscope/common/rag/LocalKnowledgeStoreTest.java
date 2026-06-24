package com.example.demoscope.common.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demoscope.domain.rag.KnowledgeChunk;
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

        LocalKnowledgeStore store = new LocalKnowledgeStore(knowledgeDir.toString(), 1, 2_000, 1);

        List<KnowledgeChunk> chunks = store.retrieve("refund policy");

        assertEquals(1, chunks.size());
        KnowledgeChunk chunk = chunks.get(0);
        assertTrue(chunk.source().endsWith("refund-policy.md"));
        assertTrue(chunk.content().contains("Refund policy") || chunk.content().contains("within 7 days"));
    }

    @Test
    void returnsNoChunksWhenKnowledgeDirectoryDoesNotExist() throws Exception {
        Path knowledgeDir = Path.of("target", "rag-test", UUID.randomUUID().toString());
        LocalKnowledgeStore store = new LocalKnowledgeStore(
                knowledgeDir.resolve("missing").toString(),
                3,
                2_000,
                1);

        assertTrue(store.retrieve("anything").isEmpty());
    }
}
