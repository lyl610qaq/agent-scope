package com.example.demoscope.controller.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.springframework.util.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamMessage(
        String type,
        String action,
        String content,
        Object data,
        String message) {

    public static StreamMessage start(String action) {
        return new StreamMessage("start", requireText(action, "action"), null, null, null);
    }

    public static StreamMessage delta(String content) {
        return new StreamMessage("delta", null, Objects.requireNonNull(content, "content"), null, null);
    }

    public static StreamMessage snapshot(Object data) {
        return new StreamMessage("snapshot", null, null, Objects.requireNonNull(data, "data"), null);
    }

    public static StreamMessage done() {
        return new StreamMessage("done", null, null, null, null);
    }

    public static StreamMessage error(String message) {
        return new StreamMessage("error", null, null, null, requireText(message, "message"));
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
