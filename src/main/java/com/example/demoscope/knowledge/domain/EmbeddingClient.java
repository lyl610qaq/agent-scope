package com.example.demoscope.knowledge.domain;

@FunctionalInterface
public interface EmbeddingClient {

    float[] embed(String input);
}
