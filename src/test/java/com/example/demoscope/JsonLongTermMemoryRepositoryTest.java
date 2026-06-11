package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        List<LongTermMemory> memories = reloaded.findRelevant("user-42", "concise");

        assertEquals(1, memories.size());
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

        List<LongTermMemory> memories = laterRepository.findRelevant("user-42", "concise");
        assertEquals(1, memories.size());
        assertEquals(0.95, memories.get(0).confidence());
        assertEquals(Instant.parse("2026-06-06T12:00:00Z"), memories.get(0).createdAt());
        assertEquals(Instant.parse("2026-06-06T13:00:00Z"), memories.get(0).updatedAt());
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
