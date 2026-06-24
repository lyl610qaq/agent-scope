package com.example.demoscope.biz.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demoscope.domain.rag.KnowledgeChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagPromptBuilderTest {

    @Test
    void keepsOriginalMessageWhenRagIsDisabled() throws Exception {
        RagPromptBuilder builder = builder(false);

        assertEquals("hello", build(builder, "hello", List.of(chunk("doc.md", "context"))));
    }

    @Test
    void addsRetrievedContextBeforeUserQuestionWhenEnabled() throws Exception {
        RagPromptBuilder builder = builder(true);

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
        RagPromptBuilder builder = builder(true);

        assertEquals("hello", build(builder, "hello", List.of()));
    }

    private RagPromptBuilder builder(boolean enabled) {
        return new RagPromptBuilder(enabled);
    }

    private KnowledgeChunk chunk(String source, String content) {
        return new KnowledgeChunk(source, content);
    }

    private String build(RagPromptBuilder builder, String message, List<KnowledgeChunk> chunks) {
        return builder.build(message, chunks);
    }
}
