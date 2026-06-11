package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LongTermMemoryPolicyTest {

    private final LongTermMemoryPolicy policy = new LongTermMemoryPolicy();

    @Test
    void acceptsApprovedLowRiskCategories() {
        assertTrue(policy.isAllowed(new LongTermMemoryCandidate(
                LongTermMemoryCategory.PREFERENCE,
                "user prefers concise answers",
                0.9)));
        assertTrue(policy.isAllowed(new LongTermMemoryCandidate(
                LongTermMemoryCategory.PROJECT_CONVENTION,
                "project uses Java 17",
                0.9)));
    }

    @Test
    void rejectsSecretLookingContent() {
        assertFalse(policy.isAllowed(new LongTermMemoryCandidate(
                LongTermMemoryCategory.COMMON_CONFIG,
                "api_key=sk-sensitive-value",
                0.9)));
        assertFalse(policy.isAllowed(new LongTermMemoryCandidate(
                LongTermMemoryCategory.STABLE_FACT,
                "password is hunter2",
                0.9)));
    }

    @Test
    void rejectsBlankOrLowConfidenceCandidates() {
        assertFalse(policy.isAllowed(new LongTermMemoryCandidate(
                LongTermMemoryCategory.STABLE_FACT,
                " ",
                0.9)));
        assertFalse(policy.isAllowed(new LongTermMemoryCandidate(
                LongTermMemoryCategory.STABLE_FACT,
                "user maintains this project",
                0.2)));
    }
}
