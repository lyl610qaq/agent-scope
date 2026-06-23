package com.example.demoscope.agent.api;

import com.example.demoscope.agent.application.AgentChatService;
import com.example.demoscope.identity.application.AuthenticatedUserContext;
import com.example.demoscope.identity.domain.UnauthenticatedUserException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class AgentChatController {

    private final AgentChatService agentChatService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public AgentChatController(
            AgentChatService agentChatService,
            AuthenticatedUserContext authenticatedUserContext) {
        this.agentChatService = agentChatService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    @PostMapping
    public ChatResponse chat(
            HttpServletRequest servletRequest,
            @RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        if (!StringUtils.hasText(request.conversationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must not be blank");
        }

        String userId;
        try {
            userId = authenticatedUserContext.requireUserId(servletRequest);
        } catch (UnauthenticatedUserException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }

        try {
            return new ChatResponse(agentChatService.chat(userId, request.conversationId(), request.message()));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    public record ChatRequest(String conversationId, String message) {
    }

    public record ChatResponse(String answer) {
    }
}
