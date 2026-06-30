package com.example.demoscope.common.embedding;

@FunctionalInterface
public interface EmbeddingClient {

    float[] embed(String input);
}
