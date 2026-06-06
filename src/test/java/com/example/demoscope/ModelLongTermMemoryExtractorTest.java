package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ModelLongTermMemoryExtractorTest {

    @Test
    void parsesStrictJsonCandidatesFromModel() {
        AtomicReference<String> receivedUserPrompt = new AtomicReference<>();
        ChatTextModel model = (systemPrompt, userPrompt) -> {
            receivedUserPrompt.set(userPrompt);
            return """
                    [
                      {
                        "category": "preference",
                        "text": "用户偏好中文回答",
                        "confidence": 0.92
                      }
                    ]
                    """;
        };
        ModelLongTermMemoryExtractor extractor = new ModelLongTermMemoryExtractor(
                model,
                new ObjectMapper());

        List<LongTermMemoryCandidate> candidates = extractor.extract(new MemoryTurn(
                "请一直使用中文回答",
                "好的",
                Instant.parse("2026-06-06T10:00:00Z")));

        assertEquals(1, candidates.size());
        assertEquals(LongTermMemoryCategory.PREFERENCE, candidates.get(0).category());
        assertEquals("用户偏好中文回答", candidates.get(0).text());
        assertTrue(receivedUserPrompt.get().contains("请一直使用中文回答"));
    }

    @Test
    void returnsEmptyForMalformedOrUnknownModelOutput() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertEquals(
                List.of(),
                new ModelLongTermMemoryExtractor(
                        (systemPrompt, userPrompt) -> "not-json",
                        objectMapper)
                        .extract(turn()));
        assertEquals(
                List.of(),
                new ModelLongTermMemoryExtractor(
                        (systemPrompt, userPrompt) -> """
                                [{"category":"private_secret","text":"secret","confidence":0.9}]
                                """,
                        objectMapper)
                        .extract(turn()));
    }

    private MemoryTurn turn() {
        return new MemoryTurn("hello", "hi", Instant.parse("2026-06-06T10:00:00Z"));
    }
}
