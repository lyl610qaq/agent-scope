package com.example.demoscope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiRequestLogger {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRequestLogger.class);

    public void logChatRequest(String apiKey, String baseUrl, String modelName, String userMessage) {
        log.info("AgentScope OpenAI compatible request:\n{}", buildRequestPreview(apiKey, baseUrl, modelName, userMessage));
    }

    String buildRequestPreview(String apiKey, String baseUrl, String modelName, String userMessage) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        return """
                curl --request POST \\
                  --url %s/chat/completions \\
                  -H "Content-Type: application/json" \\
                  -H "Authorization: Bearer %s" \\
                  -d '%s'
                """.formatted(
                normalizedBaseUrl,
                maskApiKey(apiKey),
                buildBody(modelName, userMessage));
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://api.openai.com/v1";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "(empty)";
        }

        String value = apiKey.trim();
        if (value.length() <= 8) {
            return "****";
        }

        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private String buildBody(String modelName, String userMessage) {
        return """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "You are a helpful AI assistant."},
                    {"role": "user", "content": "%s"}
                  ]
                }
                """.formatted(escapeJson(modelName), escapeJson(userMessage));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(current)) {
                        escaped.append("\\u%04x".formatted((int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
