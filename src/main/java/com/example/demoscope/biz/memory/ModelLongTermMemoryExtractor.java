package com.example.demoscope.biz.memory;

import com.example.demoscope.common.llm.ChatTextModel;
import com.example.demoscope.domain.memory.LongTermMemoryCandidate;
import com.example.demoscope.domain.memory.LongTermMemoryCategory;
import com.example.demoscope.domain.memory.LongTermMemoryExtractor;
import com.example.demoscope.domain.memory.MemoryTurn;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ModelLongTermMemoryExtractor implements LongTermMemoryExtractor {

    private static final String SYSTEM_PROMPT = """
            Extract durable, low-risk memory candidates from the conversation.
            Return only a JSON array. Each item must contain:
            category: preference, project_convention, stable_fact, or common_config
            text: a concise fact grounded in the conversation
            confidence: a number from 0 to 1
            Return [] when nothing should be remembered.
            """;

    private final ChatTextModel model;
    private final ObjectMapper objectMapper;

    public ModelLongTermMemoryExtractor(ChatTextModel model, ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LongTermMemoryCandidate> extract(MemoryTurn turn) {
        String userPrompt = """
                User:
                %s

                Assistant:
                %s
                """.formatted(turn.userMessage(), turn.assistantMessage());
        try {
            JsonNode root = objectMapper.readTree(model.generate(SYSTEM_PROMPT, userPrompt));
            if (!root.isArray()) {
                return List.of();
            }

            List<LongTermMemoryCandidate> candidates = new ArrayList<>();
            for (JsonNode node : root) {
                LongTermMemoryCategory category = parseCategory(node.path("category").asText());
                String text = node.path("text").asText("");
                double confidence = node.path("confidence").asDouble(-1);
                candidates.add(new LongTermMemoryCandidate(category, text, confidence));
            }
            return List.copyOf(candidates);
        } catch (RuntimeException | java.io.IOException ex) {
            return List.of();
        }
    }

    private LongTermMemoryCategory parseCategory(String value) {
        return LongTermMemoryCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
