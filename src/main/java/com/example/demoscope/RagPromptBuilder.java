package com.example.demoscope;

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
        prompt.append("请优先依据下面的本地知识库资料回答。")
                .append("如果资料不足以回答，请明确说明知识库中没有足够信息，再基于通用知识补充。\n\n")
                .append("本地知识库资料：\n");

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

        prompt.append("用户问题：\n").append(userMessage);
        return prompt.toString();
    }
}
