package com.example.demoscope.memory.infrastructure;

import com.example.demoscope.knowledge.domain.SemanticQuery;
import com.example.demoscope.memory.application.LongTermMemoryPolicy;
import com.example.demoscope.memory.domain.LongTermMemory;
import com.example.demoscope.memory.domain.LongTermMemoryCandidate;
import com.example.demoscope.memory.domain.LongTermMemoryCategory;
import com.example.demoscope.memory.infrastructure.JsonLongTermMemoryRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class JsonLongTermMemoryRepositoryTest {

    @Test
    void persistsAndReloadsMemories() {
        Path file = memoryFile();
        Clock clock = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
        JsonLongTermMemoryRepository repository = repository(file, clock);

        repository.save("user-42", "conversation-a", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "user prefers concise answers",
                0.9));

        JsonLongTermMemoryRepository reloaded = repository(file, clock);
        SemanticQuery query = new SemanticQuery("concise", new float[] {0.1f});
        List<LongTermMemory> memories = reloaded.findRelevant("user-42", query);

        assertEquals(1, memories.size());
        assertEquals(List.of(), reloaded.findRelevant("user-43", query));
        assertEquals("user-42", memories.get(0).userId());
        assertEquals("user prefers concise answers", memories.get(0).text());
        assertEquals("conversation-a", memories.get(0).sourceConversationId());
    }

    @Test
    void normalizesTextAndUpdatesDuplicateMemory() {
        Path file = memoryFile();
        Clock firstClock = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
        JsonLongTermMemoryRepository repository = repository(file, firstClock);
        repository.save("user-42", "conversation-a", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "user prefers concise answers",
                0.8));

        Clock secondClock = Clock.fixed(Instant.parse("2026-06-06T13:00:00Z"), ZoneOffset.UTC);
        JsonLongTermMemoryRepository laterRepository = repository(file, secondClock);
        laterRepository.save("user-42", "conversation-b", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "  user prefers concise answers  ",
                0.95));

        List<LongTermMemory> memories = laterRepository.findRelevant(
                "user-42",
                new SemanticQuery("concise", new float[] {0.1f}));
        assertEquals(1, memories.size());
        assertEquals(0.95, memories.get(0).confidence());
        assertEquals(Instant.parse("2026-06-06T12:00:00Z"), memories.get(0).createdAt());
        assertEquals(Instant.parse("2026-06-06T13:00:00Z"), memories.get(0).updatedAt());
    }

    @Test
    void ignoresLegacyMemoriesWithoutUserOwnership() throws IOException {
        Path file = memoryFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                [
                  {
                    "id": "legacy-memory",
                    "category": "PREFERENCE",
                    "text": "legacy text",
                    "sourceConversationId": "conversation-a",
                    "confidence": 0.9,
                    "createdAt": "2026-06-06T12:00:00Z",
                    "updatedAt": "2026-06-06T12:00:00Z"
                  }
                ]
                """);
        JsonLongTermMemoryRepository repository = repository(
                file,
                Clock.systemUTC());

        assertEquals(
                List.of(),
                repository.findRelevant(
                        "user-42",
                        new SemanticQuery("legacy", new float[] {0.1f})));
    }

    private JsonLongTermMemoryRepository repository(Path file, Clock clock) {
        return new JsonLongTermMemoryRepository(
                file,
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                new LongTermMemoryPolicy(),
                clock);
    }

    private Path memoryFile() {
        return Path.of("target", "json-memory-test", UUID.randomUUID().toString(), "memory.json");
    }
}
