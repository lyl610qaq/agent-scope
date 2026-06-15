package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class RuoyiAuthProxyClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsExactLoginJsonAndPreservesUpstreamResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        startServer("/auth/login", exchange -> {
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] response = "{\"code\":401,\"msg\":\"captcha required\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/problem+json");
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        RuoyiAuthProxyClient client = client(settings(baseUrl(), Duration.ofSeconds(2)));
        byte[] login = """
                {"tenantId":"000000","username":"demo","password":"secret","code":"1234","uuid":"u-1"}
                """.strip().getBytes(StandardCharsets.UTF_8);

        RuoyiAuthProxyResponse response = client.login(login);

        assertEquals(new String(login, StandardCharsets.UTF_8), requestBody.get());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, contentType.get());
        assertEquals(401, response.statusCode());
        assertEquals(
                MediaType.parseMediaType("application/problem+json"),
                response.contentType());
        assertArrayEquals(
                "{\"code\":401,\"msg\":\"captcha required\"}"
                        .getBytes(StandardCharsets.UTF_8),
                response.body());
    }

    @Test
    void forwardsOnlyTheConfiguredLogoutTokenValue() throws Exception {
        AtomicReference<String> tokenHeader = new AtomicReference<>();
        AtomicReference<String> unrelatedHeader = new AtomicReference<>();
        startServer("/auth/logout", exchange -> {
            tokenHeader.set(exchange.getRequestHeaders().getFirst("X-Token"));
            unrelatedHeader.set(exchange.getRequestHeaders().getFirst("X-Unrelated"));
            byte[] response = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        RuoyiAuthProxySettings settings = new RuoyiAuthProxySettings(
                baseUrl(),
                "/auth/login",
                "/auth/logout",
                "X-Token",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                16_384);

        RuoyiAuthProxyResponse response =
                client(settings).logout("Bearer token-123");

        assertEquals("Bearer token-123", tokenHeader.get());
        assertEquals(null, unrelatedHeader.get());
        assertEquals(200, response.statusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.contentType());
        assertArrayEquals(
                "{\"code\":200}".getBytes(StandardCharsets.UTF_8),
                response.body());
    }

    @Test
    void preservesUpstreamServerErrorsWithoutThrowing() throws Exception {
        startServer("/auth/login", exchange -> {
            byte[] response = "{\"code\":500,\"msg\":\"upstream failure\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        RuoyiAuthProxyResponse response =
                client(settings(baseUrl(), Duration.ofSeconds(2)))
                        .login("{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(500, response.statusCode());
        assertArrayEquals(
                "{\"code\":500,\"msg\":\"upstream failure\"}"
                        .getBytes(StandardCharsets.UTF_8),
                response.body());
    }

    @Test
    void classifiesConnectionFailuresAsUnavailable() {
        RuoyiAuthProxySettings settings = settings(
                URI.create("http://localhost:8081"),
                Duration.ofSeconds(1));
        RuoyiAuthProxyClient client = client(settings, (uri, method) -> {
            throw new ConnectException("connection refused");
        });

        RuoyiAuthProxyException error = assertThrows(
                RuoyiAuthProxyException.class,
                () -> client.login("{}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(RuoyiAuthProxyException.Kind.UNAVAILABLE, error.kind());
    }

    @Test
    void classifiesReadTimeoutsAsTimeout() throws Exception {
        startServer("/auth/login", exchange -> {
            try {
                Thread.sleep(500);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        RuoyiAuthProxyClient client =
                client(settings(baseUrl(), Duration.ofMillis(50)));

        RuoyiAuthProxyException error = assertThrows(
                RuoyiAuthProxyException.class,
                () -> client.login("{}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(RuoyiAuthProxyException.Kind.TIMEOUT, error.kind());
    }

    @Test
    void configuredClientDoesNotFollowUpstreamRedirects() throws Exception {
        startServer("/auth/login", exchange -> {
            exchange.getResponseHeaders().set("Location", "/unexpected-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        RuoyiAuthProxySettings settings =
                settings(baseUrl(), Duration.ofSeconds(2));

        RuoyiAuthProxyResponse response =
                new RuoyiAuthProxyClient(
                        RuoyiAuthProxyConfig.restClient(settings),
                        settings)
                        .login("{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(302, response.statusCode());
    }

    @Test
    void configurationRejectsBlankBaseUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RuoyiAuthProxyConfig.settings(
                        "",
                        "/auth/login",
                        "/auth/logout",
                        "Authorization",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        16_384));
    }

    private void startServer(
            String path,
            com.sun.net.httpserver.HttpHandler handler)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        server.start();
    }

    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private RuoyiAuthProxySettings settings(
            URI baseUrl,
            Duration readTimeout) {
        return new RuoyiAuthProxySettings(
                baseUrl,
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(1),
                readTimeout,
                16_384);
    }

    private RuoyiAuthProxyClient client(RuoyiAuthProxySettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        return client(settings, requestFactory);
    }

    private RuoyiAuthProxyClient client(
            RuoyiAuthProxySettings settings,
            ClientHttpRequestFactory requestFactory) {
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        return new RuoyiAuthProxyClient(restClient, settings);
    }
}
