package com.example.demoscope.domain.rag;

import com.example.demoscope.domain.rag.SemanticQuery;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SemanticQueryTest {

    @Test
    void protectsItsEmbeddingFromExternalMutation() {
        float[] embedding = {0.1f, 0.2f};
        SemanticQuery query = new SemanticQuery("memory", embedding);
        embedding[0] = 9.0f;

        assertEquals("memory", query.text());
        assertArrayEquals(new float[] {0.1f, 0.2f}, query.embedding());

        float[] returned = query.embedding();
        returned[1] = 8.0f;
        assertArrayEquals(new float[] {0.1f, 0.2f}, query.embedding());
    }
}
