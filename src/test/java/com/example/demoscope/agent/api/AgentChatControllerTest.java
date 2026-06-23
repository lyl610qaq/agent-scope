package com.example.demoscope.agent.api;

import com.example.demoscope.agent.application.AgentChatService;
import com.example.demoscope.identity.application.AuthenticatedUserContext;
import com.example.demoscope.identity.domain.UnauthenticatedUserException;
import com.example.demoscope.testsupport.TestRedissonConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agentscope.openai.api-key=",
        "agentscope.interview.enabled=false"
})
@AutoConfigureMockMvc
@Import(TestRedissonConfig.class)
class AgentChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeAuthenticatedUserContext authenticatedUserContext;

    @Autowired
    private CapturingAgentChatService agentChatService;

    @BeforeEach
    void resetFakes() {
        authenticatedUserContext.fail = false;
        agentChatService.fail = false;
        agentChatService.lastUserId = null;
        agentChatService.lastConversationId = null;
        agentChatService.lastMessage = null;
    }

    @Test
    void rootPreviewPageLoads() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void previewHtmlContainsDemoTitleAndRuoyiTokenInput() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AgentScope Java Demo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"token\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RuoYi Token")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("config.embeddingModel"))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("config.ruoyiAuth")));
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conversation-a",
                                  "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatRejectsBlankConversationId() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "   ",
                                  "message": "Hello"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatPassesAuthenticatedUserIdToService() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer valid-token")
                        .content("""
                                {
                                  "conversationId": "conversation-a",
                                  "message": "Hello"
                                }
                                """))
                .andExpect(status().isOk());

        assertEquals("user-42", agentChatService.lastUserId);
        assertEquals("conversation-a", agentChatService.lastConversationId);
        assertEquals("Hello", agentChatService.lastMessage);
    }

    @Test
    void chatRejectsMissingToken() throws Exception {
        authenticatedUserContext.fail = true;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conversation-a",
                                  "message": "Hello"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatReturnsServerErrorWhenServiceFails() throws Exception {
        agentChatService.fail = true;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conversation-a",
                                  "message": "Introduce AgentScope Java"
                                }
                                """))
                .andExpect(status().isInternalServerError());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        FakeAuthenticatedUserContext testAuthenticatedUserContext() {
            return new FakeAuthenticatedUserContext();
        }

        @Bean
        @Primary
        CapturingAgentChatService testAgentChatService() {
            return new CapturingAgentChatService();
        }
    }

    static final class FakeAuthenticatedUserContext implements AuthenticatedUserContext {
        boolean fail;

        @Override
        public String requireUserId(HttpServletRequest request) {
            if (fail) {
                throw new UnauthenticatedUserException("invalid token");
            }
            return "user-42";
        }
    }

    static final class CapturingAgentChatService implements AgentChatService {
        boolean fail;
        String lastUserId;
        String lastConversationId;
        String lastMessage;

        @Override
        public String chat(String userId, String conversationId, String message) {
            if (fail) {
                throw new IllegalStateException("api key missing");
            }
            this.lastUserId = userId;
            this.lastConversationId = conversationId;
            this.lastMessage = message;
            return "ok";
        }
    }
}
