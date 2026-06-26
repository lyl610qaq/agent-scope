package com.example.demoscope.config.chat;

import com.example.demoscope.controller.chat.ChatWebSocketHandler;
import com.example.demoscope.controller.interview.InterviewWebSocketHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ObjectProvider<InterviewWebSocketHandler> interviewWebSocketHandler;

    public WebSocketConfig(
            ChatWebSocketHandler chatWebSocketHandler,
            ObjectProvider<InterviewWebSocketHandler> interviewWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.interviewWebSocketHandler = interviewWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOriginPatterns("*");
        interviewWebSocketHandler.ifAvailable(handler -> registry.addHandler(handler, "/ws/interviews")
                .setAllowedOriginPatterns("*"));
    }
}
