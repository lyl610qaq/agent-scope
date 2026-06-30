package com.example.demoscope.common.memory;

import com.example.demoscope.domain.rag.SemanticQuery;
import com.example.demoscope.biz.memory.LongTermMemoryPolicy;
import com.example.demoscope.domain.memory.LongTermMemory;
import com.example.demoscope.domain.memory.LongTermMemoryCandidate;
import com.example.demoscope.domain.memory.LongTermMemoryCategory;
import com.example.demoscope.domain.memory.LongTermMemoryRepository;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonLongTermMemoryRepository implements LongTermMemoryRepository {

    private static final TypeReference<List<LongTermMemory>> MEMORY_LIST = new TypeReference<>() {
    };

    private final Path file;
    private final ObjectMapper objectMapper;
    private final LongTermMemoryPolicy policy;
    private final Clock clock;

    public JsonLongTermMemoryRepository(
            Path file,
            ObjectMapper objectMapper,
            LongTermMemoryPolicy policy,
            Clock clock) {
        this.file = file;
        this.objectMapper = objectMapper;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public synchronized List<LongTermMemory> findRelevant(String userId, SemanticQuery query) {
        return load().stream()
                .filter(memory -> userId.equals(memory.userId()))
                .toList();
    }

    @Override
    public synchronized void save(String userId, String conversationId, LongTermMemoryCandidate candidate) {
        if (!policy.isAllowed(candidate)) {
            return;
        }

        List<LongTermMemory> memories = new ArrayList<>(load());
        String normalizedText = normalize(candidate.text());
        Instant now = clock.instant();
        int existingIndex = findExisting(memories, userId, candidate.category(), normalizedText);

        if (existingIndex >= 0) {
            LongTermMemory existing = memories.get(existingIndex);
            memories.set(existingIndex, new LongTermMemory(
                    existing.id(),
                    userId,
                    existing.category(),
                    normalizedText,
                    conversationId,
                    candidate.confidence(),
                    existing.createdAt(),
                    now));
        } else {
            memories.add(new LongTermMemory(
                    UUID.randomUUID().toString(),
                    userId,
                    candidate.category(),
                    normalizedText,
                    conversationId,
                    candidate.confidence(),
                    now,
                    now));
        }

        write(memories);
    }

    private int findExisting(
            List<LongTermMemory> memories,
            String userId,
            LongTermMemoryCategory category,
            String normalizedText) {
        for (int i = 0; i < memories.size(); i++) {
            LongTermMemory memory = memories.get(i);
            if (userId.equals(memory.userId())
                    && memory.category() == category
                    && normalize(memory.text()).toLowerCase(Locale.ROOT)
                            .equals(normalizedText.toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return -1;
    }

    private List<LongTermMemory> load() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(file.toFile(), MEMORY_LIST);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read long-term memory file: " + file, ex);
        }
    }

    private void write(List<LongTermMemory> memories) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), memories);
            moveIntoPlace(temporary);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write long-term memory file: " + file, ex);
        }
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
