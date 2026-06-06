package com.example.demoscope;

@FunctionalInterface
public interface EmbeddingClient {

    float[] embed(String input);
}
