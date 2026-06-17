package com.example.demoscope;

import java.util.List;
import java.util.Objects;

public record RagQueryPlan(List<Query> queries) {

    public RagQueryPlan {
        queries = List.copyOf(Objects.requireNonNull(queries, "queries"));
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("queries must not be empty");
        }
    }

    public record Query(
            String query,
            int topK,
            List<String> filters,
            String purpose,
            String expectedEvidenceType) {

        public Query {
            query = RouterDecision.requireText(query, "query");
            if (topK < 1 || topK > 20) {
                throw new IllegalArgumentException(
                        "topK must be between 1 and 20");
            }
            filters = RouterDecision.copyTextList(filters, "filters");
            purpose = RouterDecision.requireText(purpose, "purpose");
            expectedEvidenceType = RouterDecision.requireText(
                    expectedEvidenceType,
                    "expectedEvidenceType");
        }
    }
}
