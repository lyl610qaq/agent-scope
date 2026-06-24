package com.example.demoscope.controller.chat;

import com.example.demoscope.controller.chat.AgentChatController;
import com.example.demoscope.service.chat.AgentChatService;
import com.example.demoscope.biz.auth.AuthenticatedUserContext;
import com.example.demoscope.biz.auth.BearerTokenExtractor;
import com.example.demoscope.biz.auth.RuoyiSaTokenUserContext;
import com.example.demoscope.common.ruoyi.SaTokenFacade;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentChatRuoyiAuthTest {

    private CapturingAgentChatService agentChatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentChatService = new CapturingAgentChatService();
        SaTokenFacade saTokenFacade = token -> "valid-token".equals(token) ? "user-42" : null;
        AuthenticatedUserContext userContext = new RuoyiSaTokenUserContext(
                new BearerTokenExtractor("Authorization"),
                saTokenFacade);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AgentChatController(agentChatService, userContext))
                .build();
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        performChat(null).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        performChat("Bearer invalid-token").andExpect(status().isUnauthorized());
    }

    @Test
    void validRuoyiTokenPassesLoginIdToChatService() throws Exception {
        performChat("Bearer valid-token").andExpect(status().isOk());

        assertEquals("user-42", agentChatService.lastUserId);
        assertEquals("conversation-a", agentChatService.lastConversationId);
    }

    private org.springframework.test.web.servlet.ResultActions performChat(String authorization)
            throws Exception {
        var request = post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "conversationId": "conversation-a",
                          "message": "Hello"
                        }
                        """);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    private static final class CapturingAgentChatService implements AgentChatService {
        private String lastUserId;
        private String lastConversationId;

        @Override
        public String chat(String userId, String conversationId, String message) {
            this.lastUserId = userId;
            this.lastConversationId = conversationId;
            return "ok";
        }
    }
}
