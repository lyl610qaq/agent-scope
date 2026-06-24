package com.example.demoscope.common.llm;

@FunctionalInterface
public interface ChatTextModel {

    String generate(String systemPrompt, String userPrompt);
}
