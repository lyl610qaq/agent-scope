package com.example.demoscope.service.chat;

public interface AgentChatService {

    String chat(String userId, String conversationId, String message);
}
