package com.example.demoscope.common.llm;

import com.example.demoscope.common.llm.ChatTextModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class InterviewAiJsonClient {

    private final ChatTextModel model;
    private final ObjectMapper objectMapper;

    public InterviewAiJsonClient(
            ChatTextModel model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public <T> T call(
            String systemPrompt,
            String userPrompt,
            Class<T> type) {
        String raw = generate(systemPrompt, userPrompt);
        if (raw == null || raw.isBlank() || raw.contains("```")) {
            throw new InvalidOutputException("AI returned invalid JSON");
        }
        try {
            return objectMapper.readValue(raw, type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidOutputException(
                    "AI returned invalid JSON",
                    exception);
        }
    }

    private String generate(String systemPrompt, String userPrompt) {
        TokenUsageContext current = TokenUsageContextHolder.current();
        if (!"UNKNOWN".equals(current.businessType())) {
            return model.generate(systemPrompt, userPrompt);
        }
        return TokenUsageContextHolder.callWithContext(
                new TokenUsageContext(null, null, "INTERVIEW", null),
                () -> model.generate(systemPrompt, userPrompt));
    }

    public static final class InvalidOutputException extends RuntimeException {

        public InvalidOutputException(String message) {
            super(message);
        }

        public InvalidOutputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
