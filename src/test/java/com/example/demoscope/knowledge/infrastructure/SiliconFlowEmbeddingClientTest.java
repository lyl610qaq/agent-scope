package com.example.demoscope.knowledge.infrastructure;

import com.example.demoscope.knowledge.infrastructure.SiliconFlowEmbeddingClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class SiliconFlowEmbeddingClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOpenAiCompatibleEmbeddingRequestAndParsesVector() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"data":[{"embedding":[0.1,0.2,0.3],"index":0}],"model":"test-model"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(
                baseUrl(),
                "test-key",
                "Qwen/Qwen3-Embedding-4B",
                3);

        float[] vector = client.embed("hello");

        assertEquals(3, vector.length);
        assertEquals(0.2f, vector[1], 0.0001f);
        assertEquals("Bearer test-key", authorization.get());
        assertTrue(requestBody.get().contains("\"model\":\"Qwen/Qwen3-Embedding-4B\""));
        assertTrue(requestBody.get().contains("\"input\":\"hello\""));
        assertTrue(requestBody.get().contains("\"dimensions\":3"));
    }

    @Test
    void rejectsUnexpectedEmbeddingDimensions() throws Exception {
        startServer(exchange -> {
            byte[] response = """
                    {"data":[{"embedding":[0.1,0.2],"index":0}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(
                baseUrl(),
                "test-key",
                "test-model",
                3);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> client.embed("hello"));

        assertTrue(error.getMessage().contains("Expected embedding dimension 3"));
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", handler);
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
