package com.example.demoscope;

@FunctionalInterface
public interface ChatTextModel {

    String generate(String systemPrompt, String userPrompt);
}
