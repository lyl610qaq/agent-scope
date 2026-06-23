package com.example.demoscope.knowledge.application;

import com.example.demoscope.knowledge.domain.KnowledgeChunk;
import com.example.demoscope.memory.domain.LongTermMemory;
import com.example.demoscope.memory.domain.MemoryContext;
import com.example.demoscope.memory.domain.MemoryTurn;

public class PromptContextBuilder {

    public String build(String systemInstructions, MemoryContext context, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        appendSection(prompt, "System instructions", systemInstructions);

        if (!context.shortTermTurns().isEmpty()) {
            StringBuilder turns = new StringBuilder();
            for (MemoryTurn turn : context.shortTermTurns()) {
                turns.append("User: ").append(turn.userMessage()).append('\n')
                        .append("Assistant: ").append(turn.assistantMessage()).append('\n');
            }
            appendSection(prompt, "Short-term memory", turns.toString().trim());
        }

        if (!context.longTermMemories().isEmpty()) {
            StringBuilder memories = new StringBuilder();
            for (LongTermMemory memory : context.longTermMemories()) {
                memories.append("- [")
                        .append(memory.category().name().toLowerCase())
                        .append("] ")
                        .append(memory.text())
                        .append('\n');
            }
            appendSection(prompt, "Long-term memory", memories.toString().trim());
        }

        if (!context.knowledgeChunks().isEmpty()) {
            StringBuilder knowledge = new StringBuilder();
            for (int i = 0; i < context.knowledgeChunks().size(); i++) {
                KnowledgeChunk chunk = context.knowledgeChunks().get(i);
                knowledge.append('[').append(i + 1).append("] ")
                        .append(chunk.source()).append('\n')
                        .append(chunk.content()).append('\n');
            }
            appendSection(prompt, "Knowledge base", knowledge.toString().trim());
        }

        prompt.append("User question:\n").append(userMessage);
        return prompt.toString();
    }

    private void appendSection(StringBuilder target, String title, String content) {
        target.append(title).append(":\n")
                .append(content)
                .append("\n\n");
    }
}
