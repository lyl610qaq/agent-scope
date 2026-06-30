package com.example.demoscope.common.llm;

import java.util.Objects;
import java.util.function.Consumer;

public interface StreamingChatTextModel extends ChatTextModel {

    void generateStream(String systemPrompt, String userPrompt, Consumer<String> onDelta);

    @Override
    default String generate(String systemPrompt, String userPrompt) {
        StringBuilder answer = new StringBuilder();
        generateStream(systemPrompt, userPrompt, delta -> answer.append(Objects.requireNonNull(delta, "delta")));
        return answer.toString();
    }
}
