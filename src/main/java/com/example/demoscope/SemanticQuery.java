package com.example.demoscope;

import java.util.Objects;

public record SemanticQuery(String text, float[] embedding) {

    public SemanticQuery {
        text = Objects.requireNonNull(text, "text");
        embedding = Objects.requireNonNull(embedding, "embedding").clone();
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}
