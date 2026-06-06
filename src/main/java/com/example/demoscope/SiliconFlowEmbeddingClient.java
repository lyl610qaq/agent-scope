package com.example.demoscope;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SiliconFlowEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int dimensions;

    public SiliconFlowEmbeddingClient(
            String baseUrl,
            String apiKey,
            String model,
            int dimensions) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.objectMapper = new ObjectMapper();
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String input) {
        String responseBody = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingRequest(model, input, dimensions))
                .retrieve()
                .body(String.class);
        JsonNode response = parseResponse(responseBody);

        JsonNode embedding = response == null
                ? null
                : response.path("data").path(0).path("embedding");
        if (embedding == null || !embedding.isArray()) {
            throw new IllegalStateException("Embedding response did not contain data[0].embedding");
        }
        if (embedding.size() != dimensions) {
            throw new IllegalStateException(
                    "Expected embedding dimension " + dimensions + " but received " + embedding.size());
        }

        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        return vector;
    }

    private JsonNode parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Embedding response was empty");
        }
        try {
            return objectMapper.readTree(responseBody);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embedding response was not valid JSON", exception);
        }
    }

    private record EmbeddingRequest(String model, String input, int dimensions) {
    }
}
