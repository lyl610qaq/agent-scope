package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class JsonLongTermMemoryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsMemories() {
        Path file = tempDir.resolve("memory.json");
        Clock clock = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
        JsonLongTermMemoryRepository repository = repository(file, clock);

        repository.save("conversation-a", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "用户偏好中文回答",
                0.9));

        JsonLongTermMemoryRepository reloaded = repository(file, clock);
        List<LongTermMemory> memories = reloaded.findRelevant("中文");

        assertEquals(1, memories.size());
        assertEquals("用户偏好中文回答", memories.get(0).text());
        assertEquals("conversation-a", memories.get(0).sourceConversationId());
    }

    @Test
    void normalizesTextAndUpdatesDuplicateMemory() {
        Path file = tempDir.resolve("memory.json");
        Clock firstClock = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
        JsonLongTermMemoryRepository repository = repository(file, firstClock);
        repository.save("conversation-a", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "用户偏好中文回答",
                0.8));

        Clock secondClock = Clock.fixed(Instant.parse("2026-06-06T13:00:00Z"), ZoneOffset.UTC);
        JsonLongTermMemoryRepository laterRepository = repository(file, secondClock);
        laterRepository.save("conversation-b", new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "  用户偏好中文回答  ",
                0.95));

        List<LongTermMemory> memories = laterRepository.findRelevant("中文");
        assertEquals(1, memories.size());
        assertEquals(0.95, memories.get(0).confidence());
        assertEquals(Instant.parse("2026-06-06T12:00:00Z"), memories.get(0).createdAt());
        assertEquals(Instant.parse("2026-06-06T13:00:00Z"), memories.get(0).updatedAt());
    }

    private JsonLongTermMemoryRepository repository(Path file, Clock clock) {
        return new JsonLongTermMemoryRepository(
                file,
                new ObjectMapper().findAndRegisterModules(),
                new LongTermMemoryPolicy(),
                clock);
    }
}
