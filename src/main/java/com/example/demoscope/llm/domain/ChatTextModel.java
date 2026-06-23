package com.example.demoscope.llm.domain;

@FunctionalInterface
public interface ChatTextModel {

    String generate(String systemPrompt, String userPrompt);
}
