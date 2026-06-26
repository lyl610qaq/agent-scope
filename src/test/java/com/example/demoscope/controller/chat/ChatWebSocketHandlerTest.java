package com.example.demoscope.controller.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.demoscope.common.ruoyi.SaTokenFacade;
import com.example.demoscope.service.chat.StreamingAgentChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class ChatWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void streamsChatDeltasWithRuoyiTokenUserId() throws Exception {
        StreamingAgentChatService chatService = mock(StreamingAgentChatService.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> onDelta = invocation.getArgument(3, Consumer.class);
            onDelta.accept("hello");
            onDelta.accept(" world");
            return "hello world";
        }).when(chatService).chat(eq("user-42"), eq("conversation-a"), eq("question"), any());
        ChatWebSocketHandler handler = new ChatWebSocketHandler(
                chatService,
                token -> "user-42",
                objectMapper);
        List<String> sentPayloads = new ArrayList<>();
        WebSocketSession session = sessionSendingTo(sentPayloads);

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "token": "Bearer valid-token",
                  "conversationId": "conversation-a",
                  "message": "question"
                }
                """));

        assertEquals(List.of(
                "{\"type\":\"start\",\"action\":\"chat\"}",
                "{\"type\":\"delta\",\"content\":\"hello\"}",
                "{\"type\":\"delta\",\"content\":\" world\"}",
                "{\"type\":\"done\"}"), sentPayloads);
        verify(chatService).chat(eq("user-42"), eq("conversation-a"), eq("question"), any());
    }

    @Test
    void sendsErrorWhenTokenIsMissing() throws Exception {
        StreamingAgentChatService chatService = mock(StreamingAgentChatService.class);
        SaTokenFacade saTokenFacade = mock(SaTokenFacade.class);
        ChatWebSocketHandler handler = new ChatWebSocketHandler(
                chatService,
                saTokenFacade,
                objectMapper);
        List<String> sentPayloads = new ArrayList<>();
        WebSocketSession session = sessionSendingTo(sentPayloads);

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "conversationId": "conversation-a",
                  "message": "question"
                }
                """));

        assertEquals(
                List.of("{\"type\":\"error\",\"message\":\"invalid authentication token\"}"),
                sentPayloads);
        verify(saTokenFacade, never()).getLoginIdByToken(any());
        verify(chatService, never()).chat(any(), any(), any(), any());
    }

    private WebSocketSession sessionSendingTo(List<String> sentPayloads) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0, TextMessage.class);
            sentPayloads.add(message.getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        return session;
    }
}
