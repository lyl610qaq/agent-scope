package com.example.demoscope.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.util.StringUtils;

public class AgentScopeChatTextModel implements StreamingChatTextModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final OpenAiRequestLogger requestLogger;
    private final HttpClient httpClient;
    private final TokenUsageRecorder tokenUsageRecorder;
    private final Clock clock;

    public AgentScopeChatTextModel(
            String apiKey,
            String modelName,
            String baseUrl,
            OpenAiRequestLogger requestLogger) {
        this(
                apiKey,
                modelName,
                baseUrl,
                requestLogger,
                new NoopTokenUsageRecorder(),
                Clock.systemUTC(),
                HttpClient.newHttpClient());
    }

    public AgentScopeChatTextModel(
            String apiKey,
            String modelName,
            String baseUrl,
            OpenAiRequestLogger requestLogger,
            TokenUsageRecorder tokenUsageRecorder,
            Clock clock) {
        this(
                apiKey,
                modelName,
                baseUrl,
                requestLogger,
                tokenUsageRecorder,
                clock,
                HttpClient.newHttpClient());
    }

    AgentScopeChatTextModel(
            String apiKey,
            String modelName,
            String baseUrl,
            OpenAiRequestLogger requestLogger,
            HttpClient httpClient) {
        this(
                apiKey,
                modelName,
                baseUrl,
                requestLogger,
                new NoopTokenUsageRecorder(),
                Clock.systemUTC(),
                httpClient);
    }

    AgentScopeChatTextModel(
            String apiKey,
            String modelName,
            String baseUrl,
            OpenAiRequestLogger requestLogger,
            TokenUsageRecorder tokenUsageRecorder,
            Clock clock,
            HttpClient httpClient) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.requestLogger = requestLogger;
        this.httpClient = httpClient;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.clock = clock;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured.");
        }

        requestLogger.logChatRequest(apiKey, baseUrl, modelName, userPrompt);

        Instant startedAt = clock.instant();
        String requestBody = requestBody(systemPrompt, userPrompt, false);
        try {
            JsonNode root = sendJsonRequest(requestBody);
            TokenUsage usage = usageFrom(root.path("usage"));
            record(
                    false,
                    requestBody,
                    root.path("id").asText(null),
                    usage,
                    "SUCCESS",
                    null,
                    startedAt,
                    clock.instant());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (RuntimeException ex) {
            record(
                    false,
                    requestBody,
                    null,
                    TokenUsage.empty(),
                    "FAILED",
                    ex.getMessage(),
                    startedAt,
                    clock.instant());
            throw ex;
        }
    }

    @Override
    public void generateStream(String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured.");
        }

        requestLogger.logStreamingChatRequest(apiKey, baseUrl, modelName, userPrompt);
        Instant startedAt = clock.instant();
        String requestBody = requestBody(systemPrompt, userPrompt, true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizedBaseUrl() + "/chat/completions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI compatible stream request failed: HTTP " + response.statusCode());
            }
            StreamResult streamResult = consumeServerSentEvents(response, onDelta);
            record(
                    true,
                    requestBody,
                    streamResult.responseId(),
                    streamResult.usage(),
                    "SUCCESS",
                    null,
                    startedAt,
                    clock.instant());
        } catch (IOException e) {
            record(
                    true,
                    requestBody,
                    null,
                    TokenUsage.empty(),
                    "FAILED",
                    e.getMessage(),
                    startedAt,
                    clock.instant());
            throw new IllegalStateException("OpenAI compatible stream request failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record(
                    true,
                    requestBody,
                    null,
                    TokenUsage.empty(),
                    "FAILED",
                    e.getMessage(),
                    startedAt,
                    clock.instant());
            throw new IllegalStateException("OpenAI compatible stream request interrupted.", e);
        } catch (RuntimeException e) {
            record(
                    true,
                    requestBody,
                    null,
                    TokenUsage.empty(),
                    "FAILED",
                    e.getMessage(),
                    startedAt,
                    clock.instant());
            throw e;
        }
    }

    private JsonNode sendJsonRequest(String requestBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizedBaseUrl() + "/chat/completions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI compatible request failed: HTTP " + response.statusCode());
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("OpenAI compatible request failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI compatible request interrupted.", e);
        }
    }

    private StreamResult consumeServerSentEvents(
            HttpResponse<java.io.InputStream> response,
            Consumer<String> onDelta) throws IOException {
        StreamResult result = new StreamResult();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                response.body(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    return result;
                }
                emitDelta(payload, onDelta, result);
            }
        }
        return result;
    }

    private void emitDelta(String payload, Consumer<String> onDelta, StreamResult streamResult) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(payload);
        if (root.hasNonNull("id") && streamResult.responseId() == null) {
            streamResult.responseId(root.path("id").asText());
        }
        if (root.has("usage") && !root.path("usage").isNull()) {
            streamResult.usage(usageFrom(root.path("usage")));
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return;
        }
        JsonNode content = choices.get(0).path("delta").path("content");
        if (content.isMissingNode() || content.isNull()) {
            return;
        }
        String value = content.isTextual() ? content.asText() : content.toString();
        if (!value.isEmpty()) {
            onDelta.accept(value);
        }
    }

    private String requestBody(String systemPrompt, String userPrompt, boolean stream) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", modelName);
        root.put("stream", stream);
        if (stream) {
            root.putObject("stream_options").put("include_usage", true);
        }

        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", systemPrompt);
        messages.addObject()
                .put("role", "user")
                .put("content", userPrompt);

        return root.toString();
    }

    private TokenUsage usageFrom(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return TokenUsage.empty();
        }
        return new TokenUsage(
                intOrNull(usage, "prompt_tokens"),
                intOrNull(usage, "completion_tokens"),
                intOrNull(usage, "total_tokens"),
                usage.toString());
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() ? value.asInt() : null;
    }

    private void record(
            boolean streaming,
            String rawRequestJson,
            String responseId,
            TokenUsage usage,
            String status,
            String errorMessage,
            Instant startedAt,
            Instant completedAt) {
        TokenUsageContext context = TokenUsageContextHolder.current();
        tokenUsageRecorder.record(new TokenUsageRecord(
                UUID.randomUUID(),
                context.userId(),
                context.conversationId(),
                context.businessType(),
                context.businessId(),
                modelName,
                normalizedBaseUrl(),
                "/chat/completions",
                streaming,
                rawRequestJson,
                sha256(rawRequestJson),
                responseId,
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.usageJson(),
                status,
                errorMessage,
                startedAt,
                completedAt));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalizedBaseUrl() {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://api.openai.com/v1";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private record TokenUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String usageJson) {

        static TokenUsage empty() {
            return new TokenUsage(null, null, null, null);
        }
    }

    private static final class StreamResult {

        private String responseId;
        private TokenUsage usage = TokenUsage.empty();

        String responseId() {
            return responseId;
        }

        void responseId(String responseId) {
            this.responseId = responseId;
        }

        TokenUsage usage() {
            return usage;
        }

        void usage(TokenUsage usage) {
            this.usage = usage;
        }
    }
}
