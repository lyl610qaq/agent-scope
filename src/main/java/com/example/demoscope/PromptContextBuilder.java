package com.example.demoscope;

public class PromptContextBuilder {

    public String build(String systemInstructions, MemoryContext context, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        appendSection(prompt, "系统指令", systemInstructions);

        if (!context.shortTermTurns().isEmpty()) {
            StringBuilder turns = new StringBuilder();
            for (MemoryTurn turn : context.shortTermTurns()) {
                turns.append("用户：").append(turn.userMessage()).append('\n')
                        .append("助手：").append(turn.assistantMessage()).append('\n');
            }
            appendSection(prompt, "短期记忆", turns.toString().trim());
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
            appendSection(prompt, "长期记忆", memories.toString().trim());
        }

        if (!context.knowledgeChunks().isEmpty()) {
            StringBuilder knowledge = new StringBuilder();
            for (int i = 0; i < context.knowledgeChunks().size(); i++) {
                KnowledgeChunk chunk = context.knowledgeChunks().get(i);
                knowledge.append('[').append(i + 1).append("] ")
                        .append(chunk.source()).append('\n')
                        .append(chunk.content()).append('\n');
            }
            appendSection(prompt, "知识库资料", knowledge.toString().trim());
        }

        prompt.append("用户问题：\n").append(userMessage);
        return prompt.toString();
    }

    private void appendSection(StringBuilder target, String title, String content) {
        target.append(title).append("：\n")
                .append(content)
                .append("\n\n");
    }
}
