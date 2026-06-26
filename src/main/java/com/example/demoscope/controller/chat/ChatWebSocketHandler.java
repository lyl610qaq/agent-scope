package com.example.demoscope.controller.chat;

import com.example.demoscope.common.ruoyi.SaTokenFacade;
import com.example.demoscope.controller.stream.StreamMessage;
import com.example.demoscope.domain.auth.UnauthenticatedUserException;
import com.example.demoscope.service.chat.StreamingAgentChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String BEARER_PREFIX = "Bearer ";

    private final StreamingAgentChatService chatService;
    private final SaTokenFacade saTokenFacade;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(
            StreamingAgentChatService chatService,
            SaTokenFacade saTokenFacade,
            ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.saTokenFacade = saTokenFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            ChatStreamRequest request = objectMapper.readValue(message.getPayload(), ChatStreamRequest.class);
            validate(request);
            String userId = requireUserId(request.token());

            send(session, StreamMessage.start("chat"));
            chatService.chat(userId, request.conversationId(), request.message(),
                    delta -> sendUnchecked(session, StreamMessage.delta(delta)));
            send(session, StreamMessage.done());
        } catch (UnauthenticatedUserException | IllegalArgumentException ex) {
            send(session, StreamMessage.error(ex.getMessage()));
        } catch (JsonProcessingException ex) {
            send(session, StreamMessage.error("invalid websocket payload"));
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        } catch (RuntimeException ex) {
            send(session, StreamMessage.error(ex.getMessage() == null ? "stream failed" : ex.getMessage()));
        }
    }

    private void validate(ChatStreamRequest request) {
        if (request == null || !StringUtils.hasText(request.conversationId())) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (!StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    private String requireUserId(String token) {
        String normalizedToken = normalizeToken(token);
        try {
            Object loginId = saTokenFacade.getLoginIdByToken(normalizedToken);
            String userId = loginId == null ? "" : loginId.toString();
            if (!StringUtils.hasText(userId)) {
                throw new UnauthenticatedUserException("invalid authentication token");
            }
            return userId;
        } catch (UnauthenticatedUserException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UnauthenticatedUserException("invalid authentication token", ex);
        }
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new UnauthenticatedUserException("invalid authentication token");
        }
        String value = token.trim();
        if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            value = value.substring(BEARER_PREFIX.length()).trim();
        }
        if (!StringUtils.hasText(value)) {
            throw new UnauthenticatedUserException("invalid authentication token");
        }
        return value;
    }

    private void sendUnchecked(WebSocketSession session, StreamMessage message) {
        try {
            send(session, message);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void send(WebSocketSession session, StreamMessage message) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    private record ChatStreamRequest(String token, String conversationId, String message) {
    }
}
