package com.example.demoscope.common.rag;

import com.example.demoscope.domain.rag.KnowledgeChunk;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class LocalKnowledgeStore {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeStore.class);
    private static final Pattern CHUNK_SPLIT = Pattern.compile("(\\R\\s*\\R)+");
    private static final Pattern LATIN_TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

    private final Path knowledgeDir;
    private final int topK;
    private final int maxChunkChars;
    private final int minScore;

    public LocalKnowledgeStore(String knowledgeDir, int topK, int maxChunkChars, int minScore) {
        this.knowledgeDir = Path.of(StringUtils.hasText(knowledgeDir) ? knowledgeDir : "data/knowledge");
        this.topK = Math.max(1, topK);
        this.maxChunkChars = Math.max(200, maxChunkChars);
        this.minScore = Math.max(1, minScore);
    }

    public List<KnowledgeChunk> retrieve(String query) {
        if (!StringUtils.hasText(query) || !Files.isDirectory(knowledgeDir)) {
            return List.of();
        }

        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(knowledgeDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .flatMap(path -> readChunks(path).stream())
                    .map(chunk -> new ScoredChunk(chunk, score(queryTerms, chunk)))
                    .filter(scored -> scored.score() >= minScore)
                    .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                    .limit(topK)
                    .map(ScoredChunk::chunk)
                    .toList();
        } catch (IOException ex) {
            log.warn("Failed to read local knowledge directory: {}", knowledgeDir, ex);
            return List.of();
        }
    }

    private boolean isSupportedFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".md") || fileName.endsWith(".txt");
    }

    private List<KnowledgeChunk> readChunks(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (String part : CHUNK_SPLIT.split(text)) {
                String normalized = normalizeWhitespace(part);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }

                for (String slice : slice(normalized)) {
                    chunks.add(new KnowledgeChunk(sourceName(path), slice));
                }
            }
            return chunks;
        } catch (IOException ex) {
            log.warn("Failed to read knowledge file: {}", path, ex);
            return List.of();
        }
    }

    private List<String> slice(String text) {
        if (text.length() <= maxChunkChars) {
            return List.of(text);
        }

        List<String> slices = new ArrayList<>();
        for (int start = 0; start < text.length(); start += maxChunkChars) {
            int end = Math.min(text.length(), start + maxChunkChars);
            slices.add(text.substring(start, end));
        }
        return slices;
    }

    private String sourceName(Path path) {
        try {
            return knowledgeDir.relativize(path).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return path.getFileName().toString();
        }
    }

    private int score(Set<String> queryTerms, KnowledgeChunk chunk) {
        String haystack = chunk.content().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : queryTerms) {
            if (haystack.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        String lower = value.toLowerCase(Locale.ROOT);

        for (String token : LATIN_TOKEN_SPLIT.split(lower)) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }

        String compact = lower.replaceAll("\\s+", "");
        for (int i = 0; i < compact.length(); i++) {
            char current = compact.charAt(i);
            if (Character.UnicodeScript.of(current) != Character.UnicodeScript.HAN) {
                continue;
            }

            tokens.add(String.valueOf(current));
            if (i + 1 < compact.length()
                    && Character.UnicodeScript.of(compact.charAt(i + 1)) == Character.UnicodeScript.HAN) {
                tokens.add(compact.substring(i, i + 2));
            }
        }

        return tokens;
    }

    private String normalizeWhitespace(String value) {
        return value.replaceAll("[\\t ]+", " ")
                .replaceAll("\\R+", "\n")
                .trim();
    }

    private record ScoredChunk(KnowledgeChunk chunk, int score) {
    }
}
