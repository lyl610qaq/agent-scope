package com.example.demoscope;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuoyiAuthProxyControllerTest {

    private RuoyiAuthProxyClient client;
    private MutableUserContext userContext;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        client = mock(RuoyiAuthProxyClient.class);
        userContext = new MutableUserContext();
        mockMvc = mockMvc(settings(16_384));
    }

    @Test
    void loginForwardsExactJsonAndPreservesResponse() throws Exception {
        when(client.login(any(byte[].class))).thenReturn(new RuoyiAuthProxyResponse(
                201,
                MediaType.APPLICATION_JSON,
                "{\"data\":{\"access_token\":\"token-123\"}}"
                        .getBytes(StandardCharsets.UTF_8)));
        String body = """
                {"tenantId":"000000","username":"demo","password":"secret","extra":{"code":"42"}}
                """.strip();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"data\":{\"access_token\":\"token-123\"}}"));

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(client).login(bodyCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                body,
                new String(bodyCaptor.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    void loginRejectsMissingAndWrongContentTypeBodies() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        verify(client, never()).login(any(byte[].class));
    }

    @Test
    void loginRejectsOversizedBodiesWithoutContactingUpstream() throws Exception {
        MockMvc smallLimitMockMvc = mockMvc(settings(8));

        smallLimitMockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("123456789"))
                .andExpect(status().is(413));

        verify(client, never()).login(any(byte[].class));
    }

    @Test
    void meReturnsAuthenticatedUserIdAndRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("user-42"));

        userContext.fail = true;
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutValidatesLocallyThenForwardsExactHeader() throws Exception {
        when(client.logout("Bearer token-123"))
                .thenReturn(new RuoyiAuthProxyResponse(
                        200,
                        MediaType.APPLICATION_JSON,
                        "{\"code\":200}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":200}"));

        verify(client).logout("Bearer token-123");
    }

    @Test
    void invalidLogoutNeverContactsUpstream() throws Exception {
        userContext.fail = true;

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verify(client, never()).logout(anyString());
    }

    @Test
    void mapsConnectionFailuresWithoutExposingInternalDetails() throws Exception {
        when(client.login(any(byte[].class)))
                .thenThrow(new RuoyiAuthProxyException(
                        RuoyiAuthProxyException.Kind.UNAVAILABLE,
                        new IllegalStateException(
                                "http://internal-ruoyi:8080 refused")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("internal-ruoyi"))));
    }

    @Test
    void mapsUpstreamTimeoutToGatewayTimeout() throws Exception {
        when(client.login(any(byte[].class)))
                .thenThrow(new RuoyiAuthProxyException(
                        RuoyiAuthProxyException.Kind.TIMEOUT,
                        new java.net.SocketTimeoutException("timed out")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isGatewayTimeout());
    }

    private MockMvc mockMvc(RuoyiAuthProxySettings settings) {
        return MockMvcBuilders.standaloneSetup(
                        new RuoyiAuthProxyController(
                                client,
                                userContext,
                                settings))
                .build();
    }

    private RuoyiAuthProxySettings settings(int maxLoginBodyBytes) {
        return new RuoyiAuthProxySettings(
                URI.create("http://localhost:8081"),
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                maxLoginBodyBytes);
    }

    private static final class MutableUserContext
            implements AuthenticatedUserContext {

        private boolean fail;

        @Override
        public String requireUserId(HttpServletRequest request) {
            if (fail) {
                throw new UnauthenticatedUserException(
                        "invalid authentication token");
            }
            return "user-42";
        }
    }
}
