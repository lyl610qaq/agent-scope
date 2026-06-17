package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class InterviewEvidenceProviderPlanTest {

    @Test
    void plannedRetrievalDeduplicatesBySourceAndCapsResults() {
        List<String> embeddedQueries = new ArrayList<>();
        InterviewEvidenceProvider provider = new InterviewEvidenceProvider(
                query -> {
                    embeddedQueries.add(query);
                    return new float[] {embeddedQueries.size()};
                },
                query -> {
                    if (query.text().contains("first")) {
                        return List.of(
                                new KnowledgeChunk("doc-1", "first"),
                                new KnowledgeChunk("doc-2", "second"));
                    }
                    return List.of(
                            new KnowledgeChunk("doc-2", "duplicate"),
                            new KnowledgeChunk("doc-3", "third"));
                });

        List<KnowledgeChunk> chunks = provider.retrieve(
                new RagQueryPlan(List.of(
                        new RagQueryPlan.Query(
                                "first query",
                                2,
                                List.of("java"),
                                "purpose",
                                "reference"),
                        new RagQueryPlan.Query(
                                "second query",
                                2,
                                List.of("java"),
                                "purpose",
                                "reference"))),
                3);

        assertEquals(List.of("first query", "second query"), embeddedQueries);
        assertEquals(List.of("doc-1", "doc-2", "doc-3"), chunks.stream()
                .map(KnowledgeChunk::source)
                .toList());
    }

    @Test
    void plannedRetrievalSkipsFailingQueriesAndKeepsSuccessfulEvidence() {
        InterviewEvidenceProvider provider = new InterviewEvidenceProvider(
                query -> {
                    if (query.contains("bad")) {
                        throw new IllegalStateException("embedding failed");
                    }
                    return new float[] {1.0f};
                },
                query -> List.of(new KnowledgeChunk("doc-ok", "ok")));

        List<KnowledgeChunk> chunks = provider.retrieve(
                new RagQueryPlan(List.of(
                        new RagQueryPlan.Query(
                                "bad query",
                                1,
                                List.of("java"),
                                "purpose",
                                "reference"),
                        new RagQueryPlan.Query(
                                "good query",
                                1,
                                List.of("java"),
                                "purpose",
                                "reference"))),
                2);

        assertEquals(List.of("doc-ok"), chunks.stream()
                .map(KnowledgeChunk::source)
                .toList());
    }
}
