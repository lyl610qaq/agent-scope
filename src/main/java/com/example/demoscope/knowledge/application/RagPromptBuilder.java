package com.example.demoscope.knowledge.application;

import com.example.demoscope.knowledge.domain.KnowledgeChunk;
import java.util.List;

public class RagPromptBuilder {

    private final boolean enabled;

    public RagPromptBuilder(boolean enabled) {
        this.enabled = enabled;
    }

    public String build(String userMessage, List<KnowledgeChunk> chunks) {
        if (!enabled || chunks == null || chunks.isEmpty()) {
            return userMessage;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Answer using the local knowledge base first. ")
                .append("If the context is insufficient, say so clearly before adding general knowledge.\n\n")
                .append("Local knowledge base:\n");

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            prompt.append("[")
                    .append(i + 1)
                    .append("] ")
                    .append(chunk.source())
                    .append("\n")
                    .append(chunk.content())
                    .append("\n\n");
        }

        prompt.append("User question:\n").append(userMessage);
        return prompt.toString();
    }
}
