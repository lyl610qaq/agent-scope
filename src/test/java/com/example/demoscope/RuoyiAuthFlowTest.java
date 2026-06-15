package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuoyiAuthFlowTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loginTokenAuthenticatesMeAndChatThenLogoutInvalidatesIt()
            throws Exception {
        AtomicBoolean activeSession = new AtomicBoolean();
        AtomicReference<String> loginBody = new AtomicReference<>();
        AtomicReference<String> logoutHeader = new AtomicReference<>();
        startServer(activeSession, loginBody, logoutHeader);
        RuoyiAuthProxySettings settings = settings();
        AuthenticatedUserContext userContext =
                new SharedSessionUserContext(activeSession);
        RuoyiAuthProxyController authController = new RuoyiAuthProxyController(
                new RuoyiAuthProxyClient(
                        RuoyiAuthProxyConfig.restClient(settings),
                        settings),
                userContext,
                settings);
        AgentChatController chatController = new AgentChatController(
                (userId, conversationId, message) -> "hello " + userId,
                userContext);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(authController, chatController)
                .build();
        String requestBody =
                "{\"username\":\"demo\",\"password\":\"secret\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("token-123"));

        assertEquals(requestBody, loginBody.get());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-42"));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"interview-a","message":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("hello user-42"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk());

        assertEquals("Bearer token-123", logoutHeader.get());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"interview-a","message":"hello"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private void startServer(
            AtomicBoolean activeSession,
            AtomicReference<String> loginBody,
            AtomicReference<String> logoutHeader)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/login", exchange -> {
            loginBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            activeSession.set(true);
            byte[] response = "{\"data\":{\"access_token\":\"token-123\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/auth/logout", exchange -> {
            logoutHeader.set(
                    exchange.getRequestHeaders().getFirst("Authorization"));
            activeSession.set(false);
            byte[] response = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private RuoyiAuthProxySettings settings() {
        return new RuoyiAuthProxySettings(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                16_384);
    }

    private static final class SharedSessionUserContext
            implements AuthenticatedUserContext {

        private final AtomicBoolean activeSession;

        private SharedSessionUserContext(AtomicBoolean activeSession) {
            this.activeSession = activeSession;
        }

        @Override
        public String requireUserId(HttpServletRequest request) {
            String value = request.getHeader("Authorization");
            if (!activeSession.get() || !"Bearer token-123".equals(value)) {
                throw new UnauthenticatedUserException(
                        "invalid authentication token");
            }
            return "user-42";
        }
    }
}
